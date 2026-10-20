package com.example.web_crawler.model;

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
public class CrawlerJob implements Serializable {
    private static final long serialVersionUID = 1L;

    private String jobId;
    private String triggerType; // "SCHEDULED", "MANUAL"
    private String status;      // "PENDING", "RUNNING", "COMPLETED", "PARTIAL_SUCCESS", "FAILED"
    private String startedAt;
    private String completedAt;
    private long durationMs;
    private int totalQueriesProcessed;
    private int totalOffersDiscovered;
    private Map<String, Integer> sourceOffersCount;
    private Map<String, String> sourceStatuses;
    private List<String> errorSummaries;
}
