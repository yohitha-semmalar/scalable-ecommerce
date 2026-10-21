package com.ecommerce.order.service;
import org.springframework.http.HttpStatus;
public class ServiceException extends RuntimeException {
 private final HttpStatus status; private final String code;
 public ServiceException(HttpStatus s,String c,String m){super(m);status=s;code=c;}
 public HttpStatus getStatus(){return status;} public String getCode(){return code;}
}