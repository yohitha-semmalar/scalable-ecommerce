package com.example.web_crawler;

import com.example.web_crawler.model.CompetitorPriceDto;
import com.example.web_crawler.model.SearchFilter;
import com.example.web_crawler.model.SearchResponse;
import com.example.web_crawler.service.PriceComparisonService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class PriceComparisonIntegrationTest {

    @Autowired
    private PriceComparisonService priceComparisonService;

    @Test
    void testEndToEndCrawlAndRedisCaching() {
        SearchFilter filter = SearchFilter.builder()
                .query("AirPods")
                .limit(10)
                .build();

        // 1. First search -> Cache Miss & actual HTTP crawl of the 4 mock stores
        SearchResponse response1 = priceComparisonService.searchPrices(filter, false);

        assertNotNull(response1, "Response must not be null");
        assertTrue(response1.getTotalOffers() > 0, "Must discover offers from mock stores");
        assertTrue(response1.getTotalProducts() > 0, "Must normalize into products");
        assertEquals("MISS", response1.getCacheStatus(), "First search must be a cache MISS");
        assertNotNull(response1.getSourceStatus(), "Must record source statuses");
        assertTrue(response1.getSourceStatus().containsKey("Amazon India"), "Must query Amazon India");
        assertTrue(response1.getSourceStatus().containsKey("Flipkart"), "Must query Flipkart");

        // 2. Second search -> Cache Hit from Redis
        SearchResponse response2 = priceComparisonService.searchPrices(filter, false);

        assertNotNull(response2);
        assertEquals("HIT", response2.getCacheStatus(), "Repeated search must hit Redis cache");
        assertEquals(response1.getTotalProducts(), response2.getTotalProducts());
        assertTrue(response2.getLatencyMs() <= response1.getLatencyMs() + 20, "Cache hit latency must be ultra-fast");

        // 3. Third search with bypassCache -> Fresh crawl
        SearchResponse response3 = priceComparisonService.searchPrices(filter, true);
        assertNotNull(response3);
        assertEquals("BYPASS", response3.getCacheStatus(), "Cache bypass must trigger fresh crawl");
    }

    @Test
    void testProductDetailOfferComparison() {
        // Query offers for prd-001 (Studio wireless headphones)
        List<CompetitorPriceDto> offers = priceComparisonService.getCompetitorOffers("prd-001");

        assertNotNull(offers);
        assertFalse(offers.isEmpty(), "Should retrieve competitor offers for prd-001");
        assertTrue(offers.stream().anyMatch(o -> o.getPrice() > 0));
        assertTrue(offers.stream().anyMatch(o -> o.getSource().contains("Amazon") || o.getSource().contains("Flipkart")));
    }
}
