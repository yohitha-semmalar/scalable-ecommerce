package com.example.web_crawler.service;

import com.example.web_crawler.config.CrawlerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.atomic.LongAdder;

@Slf4j
@Service
public class RedisRateLimiterService {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> rateLimitScript;
    private final CrawlerProperties properties;

    private final LongAdder rateLimitEvents = new LongAdder();

    @Autowired
    public RedisRateLimiterService(StringRedisTemplate redisTemplate,
                                  RedisScript<Long> rateLimitScript,
                                  CrawlerProperties properties) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = rateLimitScript;
        this.properties = properties;
    }

    /**
     * Checks if an upstream request to sourceKey is permitted under the sliding-window rate limit.
     * Returns true if allowed, false if rate limited.
     */
    public boolean tryAcquire(String sourceKey) {
        int rps = properties.getRateLimit().getRequestsPerSecond();
        if (rps <= 0) {
            return true; // disabled
        }

        String key = "ratelimit:source:" + sourceKey;
        long nowMs = System.currentTimeMillis();
        long windowMs = 1000L; // 1 second window

        try {
            Long result = redisTemplate.execute(
                    rateLimitScript,
                    Collections.singletonList(key),
                    String.valueOf(nowMs),
                    String.valueOf(windowMs),
                    String.valueOf(rps)
            );

            boolean allowed = result != null && result == 1L;
            if (!allowed) {
                rateLimitEvents.increment();
                log.info("Rate limit reached for source '{}' (max {} req/sec)", sourceKey, rps);
            }
            return allowed;
        } catch (Exception ex) {
            log.warn("Redis rate limiter error for source '{}' (failing open): {}", sourceKey, ex.getMessage());
            // Graceful fallback: fail open so application stays functional during Redis downtime
            return true;
        }
    }

    public long getRateLimitEventsCount() {
        return rateLimitEvents.sum();
    }
}
