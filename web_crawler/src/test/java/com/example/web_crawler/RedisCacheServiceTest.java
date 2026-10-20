package com.example.web_crawler;

import com.example.web_crawler.config.CrawlerProperties;
import com.example.web_crawler.model.SearchFilter;
import com.example.web_crawler.service.RedisCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

public class RedisCacheServiceTest {

    private RedisCacheService cacheService;
    private StringRedisTemplate redisTemplate;
    private CrawlerProperties properties;

    @BeforeEach
    void setUp() {
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        properties = new CrawlerProperties();
        cacheService = new RedisCacheService(redisTemplate, new ObjectMapper(), properties);
    }

    @Test
    void testCacheKeyGeneration_DifferentFiltersProduceDifferentKeys() {
        SearchFilter filter1 = SearchFilter.builder()
                .query("AirPods")
                .minPrice(10000.0)
                .build();

        SearchFilter filter2 = SearchFilter.builder()
                .query("AirPods")
                .minPrice(15000.0)
                .build();

        String key1 = cacheService.buildCacheKey(filter1);
        String key2 = cacheService.buildCacheKey(filter2);

        assertTrue(key1.startsWith("price-search:airpods:"));
        assertTrue(key2.startsWith("price-search:airpods:"));
        assertNotEquals(key1, key2, "Different filter criteria must produce different cache keys");
    }

    @Test
    void testCacheKeyGeneration_IdenticalFiltersProduceIdenticalKeys() {
        SearchFilter filter1 = SearchFilter.builder().query("iPhone 15").brand("Apple").build();
        SearchFilter filter2 = SearchFilter.builder().query("iphone 15").brand("apple").build();

        String key1 = cacheService.buildCacheKey(filter1);
        String key2 = cacheService.buildCacheKey(filter2);

        assertEquals(key1, key2, "Normalized identical searches must map to identical cache keys");
    }
}
