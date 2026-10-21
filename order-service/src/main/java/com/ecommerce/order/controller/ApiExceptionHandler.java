package com.ecommerce.order.controller;
import com.ecommerce.order.dto.ErrorResponse;import com.ecommerce.order.service.ServiceException;import jakarta.validation.ConstraintViolationException;import java.time.Instant;import org.springframework.dao.DataAccessException;import org.springframework.http.*;import org.springframework.http.converter.HttpMessageNotReadableException;import org.springframework.web.bind.MethodArgumentNotValidException;import org.springframework.web.bind.annotation.*;
@RestControllerAdvice public class ApiExceptionHandler {
 @ExceptionHandler(ServiceException.class) ResponseEntity<ErrorResponse> service(ServiceException e){return error(e.getStatus(),e.getCode(),e.getMessage());}
 @ExceptionHandler({MethodArgumentNotValidException.class,ConstraintViolationException.class,HttpMessageNotReadableException.class,IllegalArgumentException.class}) ResponseEntity<ErrorResponse> bad(Exception e){return error(HttpStatus.BAD_REQUEST,"BAD_REQUEST","Request data is invalid.");}
 @ExceptionHandler(DataAccessException.class) ResponseEntity<ErrorResponse> db(Exception e){return error(HttpStatus.INTERNAL_SERVER_ERROR,"DATABASE_ERROR","A database operation failed.");}
 @ExceptionHandler(Exception.class) ResponseEntity<ErrorResponse> other(Exception e){return error(HttpStatus.INTERNAL_SERVER_ERROR,"INTERNAL_ERROR","An unexpected error occurred.");}
 private ResponseEntity<ErrorResponse> error(HttpStatus s,String code,String msg){return ResponseEntity.status(s).body(new ErrorResponse(Instant.now(),s.value(),code,msg));}
}