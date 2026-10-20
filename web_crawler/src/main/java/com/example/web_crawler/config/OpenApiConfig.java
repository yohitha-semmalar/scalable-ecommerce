package com.example.web_crawler.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ScaleShop - Real-Time Web Crawler and Price Comparison Microservice")
                        .version("1.0.0")
                        .description("Member 5 microservice: crawls simulated and authorized competitor retailers, " +
                                "normalizes and matches equivalent product offers, provides Redis-backed caching, " +
                                "rate limiting, and single-flight duplicate crawl prevention.")
                        .contact(new Contact()
                                .name("Member 5 — Web Crawler & Performance Evaluation")
                                .url("https://github.com/yohitha-semmalar/scalable-ecommerce"))
                        .license(new License().name("Apache 2.0")));
    }
}
