package com.ecommerce.order.model;
import java.math.BigDecimal; import java.time.Instant; import java.util.List;
import org.springframework.data.annotation.Id; import org.springframework.data.mongodb.core.index.*; import org.springframework.data.mongodb.core.mapping.Document;
@Document("orders")
@CompoundIndex(name="user_created_idx",def="{'userId':1,'createdAt':-1}")
public class Order {
 @Id private String id; private String userId; private List<OrderItem> items; private BigDecimal totalAmount; private OrderStatus status; private Instant createdAt,updatedAt;
 @Indexed(unique=true,sparse=true) private String idempotencyLookupKey; private String idempotencyKey; private boolean cartCleared;
 public String getId(){return id;} public void setId(String v){id=v;} public String getUserId(){return userId;} public void setUserId(String v){userId=v;}
 public List<OrderItem> getItems(){return items;} public void setItems(List<OrderItem> v){items=v;} public BigDecimal getTotalAmount(){return totalAmount;} public void setTotalAmount(BigDecimal v){totalAmount=v;}
 public OrderStatus getStatus(){return status;} public void setStatus(OrderStatus v){status=v;} public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
 public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;} public String getIdempotencyLookupKey(){return idempotencyLookupKey;} public void setIdempotencyLookupKey(String v){idempotencyLookupKey=v;}
 public String getIdempotencyKey(){return idempotencyKey;} public void setIdempotencyKey(String v){idempotencyKey=v;} public boolean isCartCleared(){return cartCleared;} public void setCartCleared(boolean v){cartCleared=v;}
}