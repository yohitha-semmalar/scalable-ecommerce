package com.ecommerce.order.repository;
import com.ecommerce.order.model.Order; import java.util.*; import org.springframework.data.mongodb.repository.MongoRepository;
public interface OrderRepository extends MongoRepository<Order,String> {
 List<Order> findByUserIdOrderByCreatedAtDesc(String userId);
 Optional<Order> findByIdAndUserId(String id,String userId);
 Optional<Order> findByIdempotencyLookupKey(String key);
}