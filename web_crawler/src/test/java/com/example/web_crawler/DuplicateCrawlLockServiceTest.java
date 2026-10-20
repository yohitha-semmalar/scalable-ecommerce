package com.example.web_crawler;

import com.example.web_crawler.config.CrawlerProperties;
import com.example.web_crawler.service.DuplicateCrawlLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class DuplicateCrawlLockServiceTest {

    private DuplicateCrawlLockService lockService;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private RedisScript<Long> releaseScript;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        valueOps = Mockito.mock(ValueOperations.class);
        releaseScript = Mockito.mock(RedisScript.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        CrawlerProperties properties = new CrawlerProperties();
        lockService = new DuplicateCrawlLockService(redisTemplate, releaseScript, properties);
    }

    @Test
    void testFirstRequestAcquiresLock() {
        when(valueOps.setIfAbsent(eq("lock:crawl:airpods"), anyString(), any(Duration.class)))
                .thenReturn(Boolean.TRUE);

        DuplicateCrawlLockService.LockHandle handle = lockService.tryAcquireLock("airpods");

        assertTrue(handle.isAcquired());
        assertNotNull(handle.getToken());
        assertEquals("lock:crawl:airpods", handle.getLockKey());
    }

    @Test
    void testConcurrentIdenticalRequestRejectedByLock() {
        // Second concurrent request sees lock is already held
        when(valueOps.setIfAbsent(eq("lock:crawl:airpods"), anyString(), any(Duration.class)))
                .thenReturn(Boolean.FALSE);

        DuplicateCrawlLockService.LockHandle handle = lockService.tryAcquireLock("airpods");

        assertFalse(handle.isAcquired(), "Concurrent identical crawl must not acquire duplicate lock");
        assertEquals(1, lockService.getDuplicateCrawlsPreventedCount());
    }

    @Test
    void testSafeLockReleaseInvokesLuaScript() {
        DuplicateCrawlLockService.LockHandle handle = 
                new DuplicateCrawlLockService.LockHandle("lock:crawl:test", "token-123", true);

        lockService.releaseLock(handle);

        verify(redisTemplate, times(1)).execute(
                eq(releaseScript),
                eq(java.util.Collections.singletonList("lock:crawl:test")),
                eq("token-123")
        );
    }
}
