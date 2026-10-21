package com.ecommerce.order.config;
import java.time.Duration; import org.springframework.beans.factory.annotation.Value; import org.springframework.context.annotation.*; import org.springframework.http.client.SimpleClientHttpRequestFactory; import org.springframework.web.client.RestClient;
@Configuration public class RestClientConfig {
 @Bean RestClient.Builder restClientBuilder(@Value("${app.http.connect-timeout-millis:2000}") int c,@Value("${app.http.read-timeout-seconds:4}") int r){SimpleClientHttpRequestFactory f=new SimpleClientHttpRequestFactory();f.setConnectTimeout(c);f.setReadTimeout(Duration.ofSeconds(r));return RestClient.builder().requestFactory(f);}
}