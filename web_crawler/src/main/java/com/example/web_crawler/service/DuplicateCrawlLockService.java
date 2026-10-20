package com.example.web_crawler.service;

import com.example.web_crawler.config.CrawlerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;

@Slf4j
@Service
public class DuplicateCrawlLockService {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> safeReleaseLockScript;
    private final CrawlerProperties properties;

    private final LongAdder duplicateCrawlsPrevented = new LongAdder();

    @Autowired
    public DuplicateCrawlLockService(StringRedisTemplate redisTemplate,
                                    RedisScript<Long> safeReleaseLockScript,
                                    CrawlerProperties properties) {
        this.redisTemplate = redisTemplate;
        this.safeReleaseLockScript = safeReleaseLockScript;
        this.properties = properties;
    }

    public static class LockHandle {
        private final String lockKey;
        private final String token;
        private final boolean acquired;

        public LockHandle(String lockKey, String token, boolean acquired) {
            this.lockKey = lockKey;
            this.token = token;
            this.acquired = acquired;
        }

        public String getLockKey() { return lockKey; }
        public String getToken() { return token; }
        public boolean isAcquired() { return acquired; }
    }

    /**
     * Attempts to acquire a single-flight distributed lock for the given crawl query.
     * Returns a LockHandle with isAcquired() = true if acquired, false otherwise.
     */
    public LockHandle tryAcquireLock(String queryKey) {
        String lockKey = "lock:crawl:" + queryKey;
        String token = UUID.randomUUID().toString();
        int ttlSeconds = properties.getLock().getTtlSeconds();

        try {
            Boolean success = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, token, Duration.ofSeconds(ttlSeconds));

            boolean acquired = Boolean.TRUE.equals(success);
            if (!acquired) {
                duplicateCrawlsPrevented.increment();
                log.debug("Concurrent identical crawl detected for '{}', lock active.", queryKey);
            }
            return new LockHandle(lockKey, token, acquired);
        } catch (Exception ex) {
            log.warn("Redis lock acquisition error for '{}' (proceeding without lock): {}", queryKey, ex.getMessage());
            // Fail open if Redis is down
            return new LockHandle(lockKey, token, true);
        }
    }

    /**
     * Safely releases the distributed lock using a Lua script to ensure only the owner's token can release it.
     */
    public void releaseLock(LockHandle handle) {
        if (handle == null || !handle.isAcquired()) {
            return;
        }

        try {
            redisTemplate.execute(
                    safeReleaseLockScript,
                    Collections.singletonList(handle.getLockKey()),
                    handle.getToken()
            );
        } catch (Exception ex) {
            log.warn("Error safely releasing lock '{}': {}", handle.getLockKey(), ex.getMessage());
        }
    }

    public long getDuplicateCrawlsPreventedCount() {
        return duplicateCrawlsPrevented.sum();
    }
}
