
package com.ecommerce.cart.service;

import com.ecommerce.cart.model.*;
import com.ecommerce.cart.repository.CartRepository;
import com.ecommerce.cart.dto.ItemRequest;
import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@Service
public class CartService {

    private final CartRepository repo;
    private final ProductClient products;

    public CartService(CartRepository r, ProductClient p) {
        repo = r;
        products = p;
    }

    public Map<String, Object> view(String uid) {
        Cart c = repo.findByUserId(uid)
                .orElseGet(() -> new Cart(uid));

        List<Map<String, Object>> out = new ArrayList<>();
        double total = 0;

        for (Item i : c.getItems()) {
            JsonNode p = products.get(i.getProductId());

            double price = p.path("price").asDouble(0);
            double line = price * i.getQuantity();
            total += line;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("productId", i.getProductId());
            row.put("name", p.path("name")
                    .asText(p.path("title").asText("Product")));
            row.put("price", price);
            row.put("imageUrl", p.path("imageUrl").asText(null));
            row.put("available", p.path("available").asBoolean(true));
            row.put("quantity", i.getQuantity());
            row.put("lineTotal", line);

            out.add(row);
        }

        return Map.of(
                "userId", uid,
                "items", out,
                "total", total
        );
    }

    public Cart add(String uid, ItemRequest req) {
        int requestedQuantity = req.getQuantity();

        if (requestedQuantity <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Quantity must be greater than zero"
            );
        }

        // Fetch product details.
        JsonNode p = products.get(req.getProductId());

        validateProductAvailability(p);

        int stock = getStock(p);

        Cart c = repo.findByUserId(uid)
                .orElseGet(() -> new Cart(uid));

        Item item = c.getItems().stream()
                .filter(i -> i.getProductId()
                        .equals(req.getProductId()))
                .findFirst()
                .orElse(null);

        int existingQuantity =
                (item == null) ? 0 : item.getQuantity();

        long finalQuantity =
                (long) existingQuantity + requestedQuantity;

        // Check the combined quantity, not only the added quantity.
        if (finalQuantity > stock) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Requested quantity exceeds available stock. "
                            + "Available stock: " + stock
                            + ", quantity already in cart: "
                            + existingQuantity
            );
        }

        if (item == null) {
            c.getItems().add(
                    new Item(req.getProductId(), requestedQuantity)
            );
        } else {
            item.setQuantity((int) finalQuantity);
        }

        c.setUpdatedAt(Instant.now());
        return repo.save(c);
    }

    public Cart update(String uid, String pid, int q) {
        if (q <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Quantity must be greater than zero"
            );
        }

        Cart c = repo.findByUserId(uid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Cart not found"
                ));

        Item item = c.getItems().stream()
                .filter(i -> i.getProductId().equals(pid))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Item not found"
                ));

        // Update means replace the quantity, not add to it.
        JsonNode p = products.get(pid);

        validateProductAvailability(p);

        int stock = getStock(p);

        if (q > stock) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Requested quantity exceeds available stock. "
                            + "Available stock: " + stock
            );
        }

        item.setQuantity(q);
        c.setUpdatedAt(Instant.now());

        return repo.save(c);
    }

    public Cart remove(String uid, String pid) {
        Cart c = repo.findByUserId(uid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Cart not found"
                ));

        if (!c.getItems().removeIf(
                i -> i.getProductId().equals(pid))) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Item not found"
            );
        }

        c.setUpdatedAt(Instant.now());
        return repo.save(c);
    }

    public void clear(String uid) {
        repo.deleteByUserId(uid);
    }

    private void validateProductAvailability(JsonNode product) {
        if (product == null || product.isMissingNode()
                || product.isNull()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Product not found"
            );
        }

        if (product.has("available")
                && !product.path("available").asBoolean()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Product is unavailable"
            );
        }
    }

    private int getStock(JsonNode product) {
        // A missing or invalid stock value must not be treated as
        // unlimited stock.
        JsonNode stockNode = product.path("stock");

        if (stockNode.isMissingNode()
                || stockNode.isNull()
                || !stockNode.canConvertToInt()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Stock information is unavailable"
            );
        }

        int stock = stockNode.asInt();

        if (stock < 0) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Stock information is invalid"
            );
        }

        return stock;
    }
}
