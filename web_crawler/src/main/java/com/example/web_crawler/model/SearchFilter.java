package com.example.web_crawler.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchFilter {
    private String query;
    private String brand;
    private String category;
    private Double minPrice;
    private Double maxPrice;
    private Double minRating;
    private Boolean availableOnly;
    private Boolean verifiedDiscount;
    private List<String> platforms; // e.g. ["Amazon India", "Flipkart"]
    private String sortBy; // "price_asc", "price_desc", "rating", "discount", "relevance", "recent"
    @Builder.Default
    private int page = 1;
    @Builder.Default
    private int limit = 12;
}
