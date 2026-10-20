package com.example.web_crawler.adapter;

import com.example.web_crawler.model.ProductOffer;
import com.example.web_crawler.model.SearchFilter;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Common source-adapter interface so each retailer can be handled independently.
 */
public interface RetailerSourceAdapter {

    /**
     * Unique identifier for the retailer source (e.g. "amazon", "flipkart").
     */
    String getSourceKey();

    /**
     * Human-readable display name (e.g. "Amazon India").
     */
    String getDisplayName();

    /**
     * Whether this source is enabled in configuration.
     */
    boolean isEnabled();

    /**
     * Whether this source represents an authorized live source (false for mock).
     */
    default boolean isLive() {
        return false;
    }

    /**
     * Fetch and parse product listings for a query over HTTP.
     */
    Mono<List<ProductOffer>> search(String query, SearchFilter filter);

    /**
     * Fetch a specific product offer by ID.
     */
    Mono<ProductOffer> getProductById(String productId);
}
