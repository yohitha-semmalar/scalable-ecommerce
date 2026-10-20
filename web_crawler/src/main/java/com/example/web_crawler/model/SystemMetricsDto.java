package com.example.web_crawler.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * ScaleShop frontend system metrics format + detailed operational metrics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SystemMetricsDto implements Serializable {
    private static final long serialVersionUID = 1L;

    // ScaleShop required fields: source, simulated, results
    private String source;
    private boolean simulated;
    private List<BenchmarkScenarioResult> results;

    // Detailed operational metrics for evaluation & dashboard
    private OperationalMetrics operational;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BenchmarkScenarioResult implements Serializable {
        private String scenario;
        private int users;
        private double throughput; // requests/sec
        private double average;    // ms
        private double median;     // ms
        private double p95;        // ms
        private double p99;        // ms
        private double errorRate;  // percentage 0-100
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OperationalMetrics implements Serializable {
        private long totalSearchRequests;
        private long cacheHits;
        private long cacheMisses;
        private double cacheHitRatioPercent;
        private long rateLimitEvents;
        private long duplicateCrawlsPrevented;
        private long upstreamRequestsAvoided;
        private long upstreamRequestsExecuted;
        private long upstreamFailures;
        private double averageLatencyMs;
        private boolean redisHealthy;
        private Map<String, SourceMetricSummary> sourceMetrics;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceMetricSummary implements Serializable {
        private String source;
        private long requests;
        private long successes;
        private long failures;
        private long rateLimits;
        private double averageLatencyMs;
    }
}
