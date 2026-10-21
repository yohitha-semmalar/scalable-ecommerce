package com.ecommerce.order.service;

import com.ecommerce.order.dto.ProductResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

@Component
public class ProductClient {

 private final RestClient c;

 public ProductClient(
         RestClient.Builder b,
         @Value("${app.product-service.url}") String u,
         @Value("${app.http.connect-timeout-millis:2000}") int connectMillis,
         @Value("${app.http.read-timeout-seconds:4}") int readSeconds) {

  // The default JDK HttpURLConnection factory does NOT support HTTP PATCH,
  // which the stock endpoints use. java.net.http.HttpClient supports it.
  HttpClient http = HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .connectTimeout(Duration.ofMillis(connectMillis))
          .build();
  JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
  factory.setReadTimeout(Duration.ofSeconds(readSeconds));

  c = b.clone().requestFactory(factory).baseUrl(u).build();
 }

 public ProductResponse getProduct(String id) {
  try {
   ProductResponse p = c.get()
           .uri("/api/products/{id}", id)
           .retrieve()
           .body(ProductResponse.class);

   if (p == null) {
    throw new ServiceException(
            HttpStatus.BAD_GATEWAY,
            "INVALID_PRODUCT_RESPONSE",
            "Product Service returned an empty response.");
   }
   return p;

  } catch (ResourceAccessException e) {
   throw unavailable(e);

  } catch (RestClientResponseException e) {
   if (e.getStatusCode().value() == 404) {
    throw new ServiceException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "PRODUCT_NOT_FOUND",
            "A product in the cart no longer exists.");
   }
   throw new ServiceException(
           e.getStatusCode().is5xxServerError()
                   ? HttpStatus.SERVICE_UNAVAILABLE
                   : HttpStatus.BAD_GATEWAY,
           "PRODUCT_REQUEST_FAILED",
           "Product Service could not validate a product (HTTP "
                   + e.getStatusCode().value() + ").");
  }
 }

 public void decreaseStock(String token, String id, int quantity) {
  updateStock(token, "/api/products/{id}/stock", id, quantity);
 }

 public void increaseStock(String token, String id, int quantity) {
  updateStock(token, "/api/products/{id}/stock/increase", id, quantity);
 }

 private void updateStock(String token, String endpoint, String id, int quantity) {
  try {
   c.patch()
           .uri(endpoint, id)
           .header(HttpHeaders.AUTHORIZATION, token)
           .body(Map.of("quantity", quantity))
           .retrieve()
           .toBodilessEntity();

  } catch (ResourceAccessException e) {
   throw unavailable(e);

  } catch (RestClientResponseException e) {
   int code = e.getStatusCode().value();

   if (code == 404) {
    throw new ServiceException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "PRODUCT_NOT_FOUND",
            "Product was not found while updating stock.");
   }
   if (code == 409 || code == 422) {
    throw new ServiceException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "STOCK_UPDATE_REJECTED",
            "Product Service rejected the stock update (insufficient stock).");
   }
   if (code == 401 || code == 403) {
    throw new ServiceException(
            HttpStatus.BAD_GATEWAY,
            "STOCK_UPDATE_UNAUTHORIZED",
            "Product Service refused the token. Check that JWT_SECRET_BASE64 is the same in both services.");
   }
   throw new ServiceException(
           e.getStatusCode().is5xxServerError()
                   ? HttpStatus.SERVICE_UNAVAILABLE
                   : HttpStatus.BAD_GATEWAY,
           "STOCK_UPDATE_FAILED",
           "Product Service could not update stock (HTTP " + code + ").");
  }
 }

 private ServiceException unavailable(ResourceAccessException e) {
  System.err.println("Product Service call failed: " + e.getMessage());
  return new ServiceException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "PRODUCT_UNAVAILABLE",
          "Product Service is unavailable.");
 }
}