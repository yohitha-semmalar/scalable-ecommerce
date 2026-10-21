package com.ecommerce.order.dto;
import com.fasterxml.jackson.annotation.*;
import java.math.BigDecimal;
import java.util.List;
@JsonIgnoreProperties(ignoreUnknown=true)
public record CartResponse(List<CartItem> items,BigDecimal totalAmount) {
 @JsonIgnoreProperties(ignoreUnknown=true)
 public record CartItem(@JsonAlias({"id","product_id"}) String productId,@JsonAlias({"name","title","product_name"}) String productName,Integer quantity,BigDecimal price,BigDecimal subtotal) {}
}