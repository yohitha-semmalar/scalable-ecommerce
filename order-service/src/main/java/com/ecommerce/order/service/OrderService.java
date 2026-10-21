package com.ecommerce.order.service;

import com.ecommerce.order.dto.*;
import com.ecommerce.order.model.*;
import com.ecommerce.order.repository.OrderRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

 private final OrderRepository repo;
 private final CartClient carts;
 private final ProductClient products;

 public OrderService(
         OrderRepository r,
         CartClient c,
         ProductClient p) {
  repo = r;
  carts = c;
  products = p;
 }

 public OrderResponse create(
         String uid,
         String token,
         String key) {

  // 1. Validate the idempotency key.
  if (key != null && (key.isBlank() || key.length() > 128)) {
   throw new ServiceException(
           HttpStatus.BAD_REQUEST,
           "INVALID_IDEMPOTENCY_KEY",
           "Idempotency-Key must contain 1 to 128 characters."
   );
  }

  String lookup = key == null ? null : uid + ":" + key;

  // 2. Return an existing order for a repeated checkout.
  if (lookup != null) {
   Optional<Order> prior =
           repo.findByIdempotencyLookupKey(lookup);

   if (prior.isPresent()) {
    Order existing = prior.get();

    return dto(
            existing,
            existing.isCartCleared()
                    ? "Checkout already completed."
                    : "Order exists; cart clearing may still be pending."
    );
   }
  }

  // 3. Fetch and validate the cart.
  CartResponse cart = carts.getCart(token);

  if (cart.items() == null || cart.items().isEmpty()) {
   throw new ServiceException(
           HttpStatus.CONFLICT,
           "EMPTY_CART",
           "The cart is empty."
   );
  }

  List<OrderItem> items = new ArrayList<>();
  Map<String, Integer> quantities = new LinkedHashMap<>();
  BigDecimal total = BigDecimal.ZERO;

  // 4. Validate all products BEFORE changing any stock.
  for (CartResponse.CartItem ci : cart.items()) {

   if (ci == null
           || ci.productId() == null
           || ci.productId().isBlank()
           || ci.quantity() == null
           || ci.quantity() <= 0) {

    throw new ServiceException(
            HttpStatus.CONFLICT,
            "INVALID_CART_ITEM",
            "Cart contains an invalid product ID or quantity."
    );
   }

   ProductResponse p = products.getProduct(ci.productId());

   if (Boolean.FALSE.equals(p.available())) {
    throw new ServiceException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "PRODUCT_UNAVAILABLE",
            "A product in the cart is unavailable."
    );
   }

   if (p.stock() == null || p.stock() < 0) {
    throw new ServiceException(
            HttpStatus.BAD_GATEWAY,
            "INVALID_PRODUCT_STOCK",
            "Product Service returned invalid stock information."
    );
   }

   if (p.stock() < ci.quantity()) {
    throw new ServiceException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "INSUFFICIENT_STOCK",
            "Available stock is lower than requested quantity."
    );
   }

   BigDecimal price = p.price();

   if (price == null || price.signum() < 0) {
    throw new ServiceException(
            HttpStatus.BAD_GATEWAY,
            "INVALID_PRODUCT_PRICE",
            "Product Service returned an invalid price."
    );
   }

   String name = p.name() != null && !p.name().isBlank()
           ? p.name()
           : ci.productName();

   if (name == null || name.isBlank()) {
    throw new ServiceException(
            HttpStatus.BAD_GATEWAY,
            "INVALID_PRODUCT_NAME",
            "No product name was returned."
    );
   }

   BigDecimal unit =
           price.setScale(2, RoundingMode.HALF_UP);

   BigDecimal subtotal = unit
           .multiply(BigDecimal.valueOf(ci.quantity()))
           .setScale(2, RoundingMode.HALF_UP);

   items.add(new OrderItem(
           ci.productId(),
           name,
           ci.quantity(),
           unit,
           subtotal
   ));

   // Aggregate duplicate product IDs, if present.
   quantities.merge(
           ci.productId(),
           ci.quantity(),
           Math::addExact
   );

   total = total.add(subtotal);
  }

  // 5. Build the order before changing stock.
  Instant now = Instant.now();

  Order order = new Order();
  order.setUserId(uid);
  order.setItems(items);
  order.setTotalAmount(
          total.setScale(2, RoundingMode.HALF_UP)
  );
  order.setStatus(OrderStatus.PLACED);
  order.setCreatedAt(now);
  order.setUpdatedAt(now);
  order.setIdempotencyKey(key);
  order.setIdempotencyLookupKey(lookup);
  order.setCartCleared(false);

  // Track successful stock decrements for compensation.
  Map<String, Integer> decremented = new LinkedHashMap<>();

  try {
   // 6. Decrement stock through Product Service.
   for (Map.Entry<String, Integer> entry
           : quantities.entrySet()) {

    products.decreaseStock(token,
            entry.getKey(),
            entry.getValue()
    );

    // Record only after the decrement succeeds.
    decremented.put(
            entry.getKey(),
            entry.getValue()
    );
   }

   // 7. Save the order after stock decrements succeed.
   order = repo.save(order);

  } catch (DuplicateKeyException e) {
   // A competing request may have created the same order.
   rollbackStock(token, decremented);

   if (lookup != null) {
    Optional<Order> existing =
            repo.findByIdempotencyLookupKey(lookup);

    if (existing.isPresent()) {
     return dto(
             existing.get(),
             "Duplicate checkout resolved to the existing order."
     );
    }
   }

   throw e;

  } catch (RuntimeException e) {
   // Checkout failed before the order was saved.
   rollbackStock(token, decremented);
   throw e;
  }

  // 8. Clear the cart after the order is saved.
  try {
   carts.clearCart(token);

   order.setCartCleared(true);
   order.setUpdatedAt(Instant.now());

   order = repo.save(order);

   return dto(order, "Order placed and cart cleared.");

  } catch (ServiceException e) {
   // The order and stock changes have already succeeded.
   // Do not restore stock just because cart clearing failed.
   return dto(
           order,
           "Order was saved, but cart clearing failed. "
                   + "Do not retry with a new key; reuse the same "
                   + "Idempotency-Key or clear the cart manually."
   );
  }
 }

 /**
  * Best-effort compensation for stock decremented before an
  * order was saved. ProductClient must implement this endpoint.
  */
 private void rollbackStock(String token, Map<String, Integer> decremented) {
  for (Map.Entry<String, Integer> entry
          : decremented.entrySet()) {
   try {
    products.increaseStock(token,
            entry.getKey(),
            entry.getValue()
    );
   } catch (RuntimeException rollbackError) {
    // A failed compensation requires reconciliation.
    // Log this error in production.
    System.err.println(
            "CRITICAL: Failed to restore stock for product "
                    + entry.getKey()
                    + ", quantity " + entry.getValue()
                    + ". Manual inventory reconciliation required."
    );
   }
  }
 }

 public List<OrderResponse> list(String uid) {
  return repo.findByUserIdOrderByCreatedAtDesc(uid)
          .stream()
          .map(o -> dto(o, null))
          .toList();
 }

 public OrderResponse get(String id, String uid) {
  return repo.findByIdAndUserId(id, uid)
          .map(o -> dto(o, null))
          .orElseThrow(() -> new ServiceException(
                  HttpStatus.NOT_FOUND,
                  "ORDER_NOT_FOUND",
                  "Order was not found."
          ));
 }

 public OrderResponse cancel(String id, String uid) {
  Order order = repo.findByIdAndUserId(id, uid)
          .orElseThrow(() -> new ServiceException(
                  HttpStatus.NOT_FOUND,
                  "ORDER_NOT_FOUND",
                  "Order was not found."
          ));

  if (order.getStatus() != OrderStatus.PLACED) {
   throw new ServiceException(
           HttpStatus.CONFLICT,
           "ORDER_NOT_CANCELLABLE",
           "Only PLACED orders can be cancelled."
   );
  }

  order.setStatus(OrderStatus.CANCELLED);
  order.setUpdatedAt(Instant.now());

  return dto(repo.save(order), null);
 }

 private OrderResponse dto(Order order, String message) {
  return new OrderResponse(
          order.getId(),
          order.getUserId(),
          order.getItems().stream()
                  .map(i -> new OrderResponse.Item(
                          i.getProductId(),
                          i.getProductName(),
                          i.getQuantity(),
                          i.getUnitPrice(),
                          i.getSubtotal()
                  ))
                  .toList(),
          order.getTotalAmount(),
          order.getStatus(),
          order.getCreatedAt(),
          order.getUpdatedAt(),
          order.isCartCleared(),
          message
  );
 }
}