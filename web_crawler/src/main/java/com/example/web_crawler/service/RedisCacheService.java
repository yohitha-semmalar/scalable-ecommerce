package com.example.web_crawler.service;

import com.example.web_crawler.config.CrawlerProperties;
import com.example.web_crawler.model.SearchFilter;
import com.example.web_crawler.model.SearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class RedisCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CrawlerProperties properties;
    private final AtomicBoolean redisHealthy = new AtomicBoolean(true);

    @Autowired
    public RedisCacheService(StringRedisTemplate redisTemplate,
                             ObjectMapper objectMapper,
                             CrawlerProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public boolean isRedisHealthy() {
        return redisHealthy.get();
    }

    /**
     * Generates a stable Redis cache key following:
     * price-search:{normalized-query}:{filter-hash}
     */
    public String buildCacheKey(SearchFilter filter) {
        String query = filter.getQuery() != null ? filter.getQuery().trim().toLowerCase() : "all";
        query = query.replaceAll("[^a-z0-9_-]", "_");

        StringBuilder filterSummary = new StringBuilder();
        if (filter.getBrand() != null) filterSummary.append("b:").append(filter.getBrand().toLowerCase()).append(";");
        if (filter.getCategory() != null) filterSummary.append("c:").append(filter.getCategory().toLowerCase()).append(";");
        if (filter.getMinPrice() != null) filterSummary.append("min:").append(filter.getMinPrice()).append(";");
        if (filter.getMaxPrice() != null) filterSummary.append("max:").append(filter.getMaxPrice()).append(";");
        if (filter.getMinRating() != null) filterSummary.append("r:").append(filter.getMinRating()).append(";");
        if (filter.getAvailableOnly() != null) filterSummary.append("a:").append(filter.getAvailableOnly()).append(";");
        if (filter.getSortBy() != null) filterSummary.append("s:").append(filter.getSortBy()).append(";");
        filterSummary.append("p:").append(filter.getPage()).append(";l:").append(filter.getLimit());

        String filterHash = md5Hex(filterSummary.toString());
        return "price-search:" + query + ":" + filterHash;
    }

    public SearchResponse get(String key) {
        if (!properties.getCache().isEnabled()) {
            return null;
        }

        try {
            String json = redisTemplate.opsForValue().get(key);
            redisHealthy.set(true);

            if (json == null) {
                return null;
            }

            SearchResponse response = objectMapper.readValue(json, SearchResponse.class);
            response.setCacheStatus("HIT");
            response.setRedisAvailable(true);
            return response;
        } catch (Exception ex) {
            log.warn("Redis cache read failure for key '{}' (graceful fallback): {}", key, ex.getMessage());
            redisHealthy.set(false);
            return null;
        }
    }

    public void put(String key, SearchResponse response) {
        if (!properties.getCache().isEnabled() || response == null) {
            return;
        }

        try {
            response.setCachedAt(Instant.now().toString());
            String json = objectMapper.writeValueAsString(response);
            int ttlSeconds = properties.getCache().getTtlSeconds();
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(ttlSeconds));
            redisHealthy.set(true);
        } catch (Exception ex) {
            log.warn("Redis cache write failure for key '{}' (non-fatal): {}", key, ex.getMessage());
            redisHealthy.set(false);
        }
    }

    public void evict(String key) {
        try {
            redisTemplate.delete(key);
            redisHealthy.set(true);
        } catch (Exception ex) {
            log.warn("Redis cache evict failure: {}", ex.getMessage());
            redisHealthy.set(false);
        }
    }

    private String md5Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().substring(0, 8); // compact 8-char hash
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
