package com.ecommerce.order.dto;
import java.time.Instant;
public record ErrorResponse(Instant timestamp,int status,String error,String message) {}