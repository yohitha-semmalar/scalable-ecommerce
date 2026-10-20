package com.example.web_crawler.adapter;

import com.example.web_crawler.model.ProductOffer;
import com.example.web_crawler.model.SearchFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public abstract class AbstractMockSourceAdapter implements RetailerSourceAdapter {

    protected final WebClient webClient;
    protected final ObjectMapper objectMapper;
    protected final String baseUrl;
    protected final boolean enabled;

    public AbstractMockSourceAdapter(WebClient.Builder webClientBuilder,
                                     ObjectMapper objectMapper,
                                     String baseUrl,
                                     boolean enabled) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.enabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Mono<List<ProductOffer>> search(String query, SearchFilter filter) {
        if (!enabled) {
            return Mono.just(Collections.emptyList());
        }

        String searchPath = "/mock-store/search?q=" + (query != null ? query : "");
        if (filter != null) {
            if (filter.getCategory() != null && !filter.getCategory().isBlank()) {
                searchPath += "&category=" + filter.getCategory();
            }
            if (filter.getMinPrice() != null) {
                searchPath += "&minPrice=" + filter.getMinPrice();
            }
            if (filter.getMaxPrice() != null) {
                searchPath += "&maxPrice=" + filter.getMaxPrice();
            }
            if (Boolean.TRUE.equals(filter.getAvailableOnly())) {
                searchPath += "&available=true";
            }
        }

        return webClient.get()
                .uri(searchPath)
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(Retry.backoff(2, Duration.ofMillis(200))
                        .filter(throwable -> throwable instanceof WebClientResponseException.ServiceUnavailable))
                .map(this::parseSearchResponse)
                .onErrorResume(ex -> {
                    log.warn("Failed to fetch products from {} at {}: {}", getDisplayName(), baseUrl, ex.getMessage());
                    return Mono.just(Collections.emptyList());
                });
    }

    @Override
    public Mono<ProductOffer> getProductById(String productId) {
        if (!enabled) {
            return Mono.empty();
        }

        return webClient.get()
                .uri("/mock-store/product/" + productId)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseProductOffer)
                .onErrorResume(ex -> {
                    log.warn("Failed to fetch product {} from {}: {}", productId, getDisplayName(), ex.getMessage());
                    return Mono.empty();
                });
    }

    protected List<ProductOffer> parseSearchResponse(String jsonString) {
        List<ProductOffer> list = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(jsonString);
            JsonNode itemsNode = root.path("items");
            if (itemsNode.isArray()) {
                for (JsonNode itemNode : itemsNode) {
                    ProductOffer offer = objectMapper.treeToValue(itemNode, ProductOffer.class);
                    list.add(offer);
                }
            }
        } catch (Exception e) {
            log.error("Error parsing search response from {}: {}", getDisplayName(), e.getMessage());
        }
        return list;
    }

    protected ProductOffer parseProductOffer(String jsonString) {
        try {
            return objectMapper.readValue(jsonString, ProductOffer.class);
        } catch (Exception e) {
            log.error("Error parsing product offer from {}: {}", getDisplayName(), e.getMessage());
            return null;
        }
    }
}
