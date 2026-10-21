package com.ecommerce.order.dto;
import com.ecommerce.order.model.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
public record OrderResponse(String id,String userId,List<Item> items,BigDecimal totalAmount,OrderStatus status,Instant createdAt,Instant updatedAt,boolean cartCleared,String checkoutMessage) {
 public record Item(String productId,String productName,int quantity,BigDecimal unitPrice,BigDecimal subtotal) {}
}