package com.example.web_crawler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "crawler")
public class CrawlerProperties {

    private CacheConfig cache = new CacheConfig();
    private RateLimitConfig rateLimit = new RateLimitConfig();
    private LockConfig lock = new LockConfig();
    private BackgroundConfig background = new BackgroundConfig();
    private Map<String, SourceConfig> sources = new HashMap<>();

    @Data
    public static class CacheConfig {
        private boolean enabled = true;
        private int ttlSeconds = 60;
    }

    @Data
    public static class RateLimitConfig {
        private int requestsPerSecond = 5;
    }

    @Data
    public static class LockConfig {
        private int ttlSeconds = 5;
        private long waitTimeoutMs = 3000;
    }

    @Data
    public static class BackgroundConfig {
        private boolean enabled = true;
        private int intervalSeconds = 300;
    }

    @Data
    public static class SourceConfig {
        private String url;
        private boolean enabled = true;
    }
}
