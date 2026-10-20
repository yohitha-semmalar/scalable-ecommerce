package com.example.web_crawler.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private String query;
    private int totalProducts;
    private int totalOffers;
    private List<NormalizedProduct> products;
    private Map<String, String> sourceStatus; // source -> "SUCCESS", "DEGRADED", "RATE_LIMITED", "TIMEOUT", "FAILED"
    private String cacheStatus; // "HIT", "MISS", "BYPASS", "STALE"
    private boolean redisAvailable;
    private boolean mock;
    private String cachedAt;
    private String retrievalTimestamp;
    private double latencyMs;
    private int page;
    private int limit;
    private int totalPages;
}
