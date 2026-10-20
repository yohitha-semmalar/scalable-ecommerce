package com.example.web_crawler.service;

import com.example.web_crawler.adapter.RetailerSourceAdapter;
import com.example.web_crawler.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PriceComparisonService {

    private final List<RetailerSourceAdapter> adapters;
    private final ProductNormalizerService normalizerService;
    private final RedisCacheService cacheService;
    private final RedisRateLimiterService rateLimiterService;
    private final DuplicateCrawlLockService lockService;
    private final MetricsCollectorService metricsCollectorService;

    @Autowired
    public PriceComparisonService(List<RetailerSourceAdapter> adapters,
                                  ProductNormalizerService normalizerService,
                                  RedisCacheService cacheService,
                                  RedisRateLimiterService rateLimiterService,
                                  DuplicateCrawlLockService lockService,
                                  MetricsCollectorService metricsCollectorService) {
        this.adapters = adapters;
        this.normalizerService = normalizerService;
        this.cacheService = cacheService;
        this.rateLimiterService = rateLimiterService;
        this.lockService = lockService;
        this.metricsCollectorService = metricsCollectorService;
    }

    /**
     * Executes product search across retailers with caching, rate limiting, and single-flight lock.
     */
    public SearchResponse searchPrices(SearchFilter filter, boolean bypassCache) {
        long startTime = System.nanoTime();
        String cacheKey = cacheService.buildCacheKey(filter);

        // 1. Check Redis Cache
        if (!bypassCache) {
            SearchResponse cached = cacheService.get(cacheKey);
            if (cached != null) {
                long elapsedNanos = System.nanoTime() - startTime;
                cached.setLatencyMs(round(elapsedNanos / 1_000_000.0));
                metricsCollectorService.recordSearch(true, elapsedNanos, adapters.size());
                return cached;
            }
        }

        // 2. Cache miss -> Single-flight lock to prevent duplicate concurrent crawls
        String queryNorm = filter.getQuery() != null ? filter.getQuery().trim().toLowerCase() : "all";
        DuplicateCrawlLockService.LockHandle lockHandle = lockService.tryAcquireLock(queryNorm);

        if (!lockHandle.isAcquired()) {
            // Another thread is crawling identical query: wait briefly for its cache result
            for (int i = 0; i < 6; i++) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {}
                SearchResponse newCached = cacheService.get(cacheKey);
                if (newCached != null) {
                    long elapsedNanos = System.nanoTime() - startTime;
                    newCached.setLatencyMs(round(elapsedNanos / 1_000_000.0));
                    metricsCollectorService.recordSearch(true, elapsedNanos, adapters.size());
                    return newCached;
                }
            }
        }

        try {
            // 3. Query enabled sources concurrently with bounded concurrency
            Map<String, String> sourceStatuses = new ConcurrentHashMap<>();
            List<ProductOffer> allOffers = fetchFromSources(filter, sourceStatuses);

            // 4. Normalize, deduplicate, and conservatively match products
            List<NormalizedProduct> groupedProducts = normalizerService.groupAndNormalize(allOffers, filter);

            // 5. Pagination
            int total = groupedProducts.size();
            int page = Math.max(1, filter.getPage());
            int limit = Math.max(1, filter.getLimit());
            int fromIndex = (page - 1) * limit;
            List<NormalizedProduct> pageItems = Collections.emptyList();
            if (fromIndex < total) {
                pageItems = groupedProducts.subList(fromIndex, Math.min(fromIndex + limit, total));
            }
            int totalPages = (int) Math.ceil((double) total / limit);

            long elapsedNanos = System.nanoTime() - startTime;
            double latencyMs = round(elapsedNanos / 1_000_000.0);

            boolean isAllMock = allOffers.stream().allMatch(ProductOffer::isMock);

            SearchResponse response = SearchResponse.builder()
                    .query(filter.getQuery() != null ? filter.getQuery() : "")
                    .totalProducts(total)
                    .totalOffers(allOffers.size())
                    .products(pageItems)
                    .sourceStatus(sourceStatuses)
                    .cacheStatus(bypassCache ? "BYPASS" : "MISS")
                    .redisAvailable(cacheService.isRedisHealthy())
                    .mock(isAllMock)
                    .retrievalTimestamp(Instant.now().toString())
                    .latencyMs(latencyMs)
                    .page(page)
                    .limit(limit)
                    .totalPages(totalPages)
                    .build();

            // 6. Cache in Redis
            cacheService.put(cacheKey, response);
            metricsCollectorService.recordSearch(false, elapsedNanos, 0);

            return response;

        } finally {
            lockService.releaseLock(lockHandle);
        }
    }

    /**
     * Retrieve comparable competitor offers for ScaleShop product ID (e.g. prd-001).
     */
    public List<CompetitorPriceDto> getCompetitorOffers(String productId) {
        String cleanId = productId != null ? productId.trim().toLowerCase() : "";

        // Query all adapters concurrently for matching product ID
        List<ProductOffer> offers = Flux.fromIterable(adapters)
                .filter(RetailerSourceAdapter::isEnabled)
                .flatMap(adapter -> {
                    String sourceKey = adapter.getSourceKey();
                    if (!rateLimiterService.tryAcquire(sourceKey)) {
                        return Mono.empty();
                    }
                    long start = System.currentTimeMillis();
                    return adapter.getProductById(cleanId)
                            .doOnSuccess(res -> metricsCollectorService.recordUpstreamCall(
                                    adapter.getDisplayName(), res != null, false, System.currentTimeMillis() - start))
                            .doOnError(err -> metricsCollectorService.recordUpstreamCall(
                                    adapter.getDisplayName(), false, false, System.currentTimeMillis() - start));
                })
                .collectList()
                .block(Duration.ofSeconds(4));

        if (offers == null || offers.isEmpty()) {
            // Also attempt searching by productId
            SearchFilter filter = SearchFilter.builder().query(productId).limit(5).build();
            SearchResponse searchResp = searchPrices(filter, false);
            if (searchResp != null && searchResp.getProducts() != null && !searchResp.getProducts().isEmpty()) {
                offers = searchResp.getProducts().get(0).getOffers();
            }
        }

        if (offers == null) {
            return Collections.emptyList();
        }

        return offers.stream().map(o -> CompetitorPriceDto.builder()
                .source(o.getSource())
                .price(o.getPrice())
                .available(o.isAvailability())
                .checkedAt(o.getRetrievalTimestamp() != null ? o.getRetrievalTimestamp() : Instant.now().toString())
                .simulated(o.isMock())
                .build()).collect(Collectors.toList());
    }

    private List<ProductOffer> fetchFromSources(SearchFilter filter, Map<String, String> statusMap) {
        String query = filter.getQuery() != null ? filter.getQuery().trim() : "";

        List<RetailerSourceAdapter> targetAdapters = adapters.stream()
                .filter(RetailerSourceAdapter::isEnabled)
                .filter(a -> filter.getPlatforms() == null || filter.getPlatforms().isEmpty()
                        || filter.getPlatforms().contains(a.getDisplayName())
                        || filter.getPlatforms().contains(a.getSourceKey()))
                .collect(Collectors.toList());

        return Flux.fromIterable(targetAdapters)
                .flatMap(adapter -> {
                    String key = adapter.getSourceKey();
                    String name = adapter.getDisplayName();

                    // Apply rate limit check
                    if (!rateLimiterService.tryAcquire(key)) {
                        statusMap.put(name, "RATE_LIMITED");
                        metricsCollectorService.recordUpstreamCall(name, false, true, 0);
                        log.info("Rate limiter throttled source '{}'", name);
                        return Mono.just(Collections.<ProductOffer>emptyList());
                    }

                    long startMs = System.currentTimeMillis();
                    return adapter.search(query, filter)
                            .timeout(Duration.ofMillis(3500))
                            .doOnSuccess(items -> {
                                long lat = System.currentTimeMillis() - startMs;
                                statusMap.put(name, "SUCCESS (" + items.size() + " items, " + lat + "ms)");
                                metricsCollectorService.recordUpstreamCall(name, true, false, lat);
                            })
                            .onErrorResume(ex -> {
                                long lat = System.currentTimeMillis() - startMs;
                                statusMap.put(name, "FAILED (" + ex.getClass().getSimpleName() + ")");
                                metricsCollectorService.recordUpstreamCall(name, false, false, lat);
                                log.warn("Source '{}' call failed: {}", name, ex.getMessage());
                                return Mono.just(Collections.emptyList());
                            });
                })
                .flatMapIterable(list -> list)
                .collectList()
                .block(Duration.ofSeconds(5));
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
