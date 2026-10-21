package com.ecommerce.order.controller;
import com.ecommerce.order.dto.OrderResponse;import com.ecommerce.order.service.OrderService;import jakarta.servlet.http.HttpServletRequest;import java.net.URI;import java.util.List;import org.springframework.http.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/orders") public class OrderController {
 private final OrderService service;public OrderController(OrderService s){service=s;}
 @PostMapping public ResponseEntity<OrderResponse> create(HttpServletRequest r,@RequestHeader(value="Idempotency-Key",required=false)String key){OrderResponse o=service.create((String)r.getAttribute("authenticatedUserId"),r.getHeader(HttpHeaders.AUTHORIZATION),key);return ResponseEntity.created(URI.create("/api/orders/"+o.id())).body(o);}
 @GetMapping public List<OrderResponse> list(HttpServletRequest r){return service.list((String)r.getAttribute("authenticatedUserId"));}
 @GetMapping("/{orderId}") public OrderResponse get(@PathVariable String orderId,HttpServletRequest r){return service.get(orderId,(String)r.getAttribute("authenticatedUserId"));}
 @PatchMapping("/{orderId}/cancel") public OrderResponse cancel(@PathVariable String orderId,HttpServletRequest r){return service.cancel(orderId,(String)r.getAttribute("authenticatedUserId"));}
}