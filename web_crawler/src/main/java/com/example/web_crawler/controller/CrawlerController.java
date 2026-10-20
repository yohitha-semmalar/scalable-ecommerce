package com.example.web_crawler.controller;

import com.example.web_crawler.model.CrawlerJob;
import com.example.web_crawler.service.BackgroundCrawlerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/crawler")
@Tag(name = "Crawler Management", description = "Endpoints for triggering and monitoring background crawl jobs")
public class CrawlerController {

    private final BackgroundCrawlerService backgroundCrawlerService;

    @Autowired
    public CrawlerController(BackgroundCrawlerService backgroundCrawlerService) {
        this.backgroundCrawlerService = backgroundCrawlerService;
    }

    @PostMapping("/run")
    @Operation(summary = "Trigger a background crawl and cache warm-up job")
    public ResponseEntity<CrawlerJob> triggerCrawl() {
        CrawlerJob job = backgroundCrawlerService.triggerManualCrawl();
        return ResponseEntity.accepted().body(job);
    }

    @GetMapping("/status")
    @Operation(summary = "Get the latest or active background crawler job status")
    public ResponseEntity<CrawlerJob> getStatus() {
        CrawlerJob job = backgroundCrawlerService.getLatestJobStatus();
        if (job == null) {
            return ResponseEntity.ok(CrawlerJob.builder()
                    .status("IDLE")
                    .totalQueriesProcessed(0)
                    .totalOffersDiscovered(0)
                    .build());
        }
        return ResponseEntity.ok(job);
    }

    @GetMapping("/jobs/{job_id}")
    @Operation(summary = "Get progress and results for a specific crawler job ID")
    public ResponseEntity<?> getJob(@PathVariable("job_id") String jobId) {
        CrawlerJob job = backgroundCrawlerService.getJobById(jobId);
        if (job == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "Job not found or expired from Redis retention",
                    "jobId", jobId
            ));
        }
        return ResponseEntity.ok(job);
    }
}
