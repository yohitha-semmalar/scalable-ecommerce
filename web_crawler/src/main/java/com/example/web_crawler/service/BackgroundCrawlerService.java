package com.example.web_crawler.service;

import com.example.web_crawler.config.CrawlerProperties;
import com.example.web_crawler.model.CrawlerJob;
import com.example.web_crawler.model.SearchFilter;
import com.example.web_crawler.model.SearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class BackgroundCrawlerService {

    private final PriceComparisonService priceComparisonService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CrawlerProperties properties;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private volatile CrawlerJob latestJob;

    private static final List<String> SEED_KEYWORDS = List.of(
            "AirPods", "iPhone", "Samsung", "OnePlus", "Jeans", "Shirt", "Air Fryer", "Mixer Grinder", "Kettle", "Headphones"
    );

    @Autowired
    public BackgroundCrawlerService(PriceComparisonService priceComparisonService,
                                   StringRedisTemplate redisTemplate,
                                   ObjectMapper objectMapper,
                                   CrawlerProperties properties) {
        this.priceComparisonService = priceComparisonService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * Periodic scheduled crawl for refreshing background data.
     */
    @Scheduled(fixedDelayString = "${crawler.background.interval-seconds:300}000", initialDelay = 15000)
    public void runScheduledCrawl() {
        if (!properties.getBackground().isEnabled()) {
            return;
        }
        executeCrawl("SCHEDULED");
    }

    /**
     * Manually triggered crawl run.
     */
    public CrawlerJob triggerManualCrawl() {
        if (isRunning.get()) {
            return latestJob;
        }
        return executeCrawl("MANUAL");
    }

    public CrawlerJob getLatestJobStatus() {
        return latestJob;
    }

    public CrawlerJob getJobById(String jobId) {
        try {
            String json = redisTemplate.opsForValue().get("crawler:job:" + jobId);
            if (json != null) {
                return objectMapper.readValue(json, CrawlerJob.class);
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve job {} from Redis: {}", jobId, e.getMessage());
        }
        if (latestJob != null && latestJob.getJobId().equals(jobId)) {
            return latestJob;
        }
        return null;
    }

    private CrawlerJob executeCrawl(String triggerType) {
        if (!isRunning.compareAndSet(false, true)) {
            log.info("Background crawler already running, skipping new execution.");
            return latestJob;
        }

        String jobId = "job-" + UUID.randomUUID().toString().substring(0, 8);
        Instant start = Instant.now();

        CrawlerJob job = CrawlerJob.builder()
                .jobId(jobId)
                .triggerType(triggerType)
                .status("RUNNING")
                .startedAt(start.toString())
                .totalQueriesProcessed(0)
                .totalOffersDiscovered(0)
                .sourceOffersCount(new ConcurrentHashMap<>())
                .sourceStatuses(new ConcurrentHashMap<>())
                .errorSummaries(Collections.synchronizedList(new ArrayList<>()))
                .build();

        latestJob = job;
        saveJobToRedis(job);

        log.info("Starting background crawler [{}] triggered by {}", jobId, triggerType);

        try {
            int totalOffers = 0;
            int queriesDone = 0;

            for (String keyword : SEED_KEYWORDS) {
                SearchFilter filter = SearchFilter.builder().query(keyword).build();
                // Bypass cache to refresh newly crawled data
                SearchResponse response = priceComparisonService.searchPrices(filter, true);

                if (response != null) {
                    queriesDone++;
                    totalOffers += response.getTotalOffers();
                    if (response.getSourceStatus() != null) {
                        job.getSourceStatuses().putAll(response.getSourceStatus());
                    }
                }
            }

            Instant finish = Instant.now();
            long duration = Duration.between(start, finish).toMillis();

            job.setStatus(queriesDone == SEED_KEYWORDS.size() ? "COMPLETED" : "PARTIAL_SUCCESS");
            job.setCompletedAt(finish.toString());
            job.setDurationMs(duration);
            job.setTotalQueriesProcessed(queriesDone);
            job.setTotalOffersDiscovered(totalOffers);

            log.info("Background crawler [{}] finished in {}ms. Discovered {} offers across {} queries.",
                    jobId, duration, totalOffers, queriesDone);

        } catch (Exception ex) {
            job.setStatus("FAILED");
            job.setCompletedAt(Instant.now().toString());
            job.getErrorSummaries().add("Fatal crawler error: " + ex.getMessage());
            log.error("Background crawler failed: {}", ex.getMessage(), ex);
        } finally {
            isRunning.set(false);
            saveJobToRedis(job);
        }

        return job;
    }

    private void saveJobToRedis(CrawlerJob job) {
        try {
            String json = objectMapper.writeValueAsString(job);
            // Retain job record for 1 hour in Redis (temporary tracking, not permanent storage)
            redisTemplate.opsForValue().set("crawler:job:" + job.getJobId(), json, Duration.ofHours(1));
        } catch (Exception ex) {
            log.warn("Failed to store crawler job in Redis: {}", ex.getMessage());
        }
    }
}
