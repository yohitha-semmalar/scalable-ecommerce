package com.ecommerce.auth.config;

import com.ecommerce.auth.ratelimit.Bucket4jRateLimitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class RateLimitConfig {
    @Bean
    FilterRegistrationBean<Bucket4jRateLimitFilter> rateLimitFilterRegistration(
            Bucket4jRateLimitFilter filter) {
        FilterRegistrationBean<Bucket4jRateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/api/auth/register", "/api/auth/login");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
