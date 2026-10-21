package com.ecommerce.order.dto;
import com.fasterxml.jackson.annotation.*;
import java.math.BigDecimal;
@JsonIgnoreProperties(ignoreUnknown=true)
public record ProductResponse(@JsonAlias("id") String productId,@JsonAlias({"title","productName"}) String name,BigDecimal price,Boolean available,Integer stock) {}