package com.ecommerce.order.config;
import com.ecommerce.order.security.*; import org.springframework.boot.web.servlet.FilterRegistrationBean; import org.springframework.context.annotation.*;
@Configuration public class FilterRegistrationConfig {
 @Bean FilterRegistrationBean<JwtFilter> jwtServletRegistration(JwtFilter f){FilterRegistrationBean<JwtFilter>b=new FilterRegistrationBean<>(f);b.setEnabled(false);return b;}
 @Bean FilterRegistrationBean<RateLimitFilter> rateServletRegistration(RateLimitFilter f){FilterRegistrationBean<RateLimitFilter>b=new FilterRegistrationBean<>(f);b.setEnabled(false);return b;}
}