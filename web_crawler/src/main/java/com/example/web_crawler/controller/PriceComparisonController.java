package com.example.web_crawler.controller;

import com.example.web_crawler.model.CompetitorPriceDto;
import com.example.web_crawler.model.SearchFilter;
import com.example.web_crawler.model.SearchResponse;
import com.example.web_crawler.service.PriceComparisonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/prices")
@Tag(name = "Price Comparison", description = "Endpoints for searching, grouping and comparing competitor prices")
public class PriceComparisonController {

    private final PriceComparisonService priceComparisonService;

    @Autowired
    public PriceComparisonController(PriceComparisonService priceComparisonService) {
        this.priceComparisonService = priceComparisonService;
    }

    @GetMapping("/search")
    @Operation(summary = "Search products across multiple retailers and compare prices with Redis caching")
    public SearchResponse search(
            @Parameter(description = "Product search query (e.g. AirPods, iPhone 15)")
            @RequestParam(required = false) String q,
            @Parameter(description = "Filter by brand (e.g. Apple, Samsung, Levi's)")
            @RequestParam(required = false) String brand,
            @Parameter(description = "Filter by category (e.g. Electronics, Fashion, Kitchen)")
            @RequestParam(required = false) String category,
            @Parameter(description = "Minimum price in INR")
            @RequestParam(required = false) Double minPrice,
            @Parameter(description = "Maximum price in INR")
            @RequestParam(required = false) Double maxPrice,
            @Parameter(description = "Minimum customer rating (e.g. 4.0)")
            @RequestParam(required = false) Double minRating,
            @Parameter(description = "In stock products only")
            @RequestParam(required = false, defaultValue = "false") Boolean availableOnly,
            @Parameter(description = "Verified discount only")
            @RequestParam(required = false) Boolean verifiedDiscount,
            @Parameter(description = "Retailer platforms to include")
            @RequestParam(required = false) List<String> platforms,
            @Parameter(description = "Sort order: price_asc, price_desc, rating, discount, spread")
            @RequestParam(required = false, defaultValue = "price_asc") String sort,
            @Parameter(description = "1-based page number")
            @RequestParam(required = false, defaultValue = "1") int page,
            @Parameter(description = "Items per page")
            @RequestParam(required = false, defaultValue = "12") int limit,
            @Parameter(description = "Bypass Redis cache for live fresh crawl")
            @RequestParam(required = false, defaultValue = "false") boolean refresh,
            @RequestHeader(value = "X-Bypass-Cache", required = false) String bypassHeader
    ) {
        boolean shouldBypass = refresh || "true".equalsIgnoreCase(bypassHeader);

        SearchFilter filter = SearchFilter.builder()
                .query(q)
                .brand(brand)
                .category(category)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .minRating(minRating)
                .availableOnly(availableOnly)
                .verifiedDiscount(verifiedDiscount)
                .platforms(platforms)
                .sortBy(sort)
                .page(page)
                .limit(limit)
                .build();

        return priceComparisonService.searchPrices(filter, shouldBypass);
    }

    @GetMapping("/{product_id}")
    @Operation(summary = "Retrieve comparable competitor offers for ScaleShop product ID")
    public List<CompetitorPriceDto> getOffersForProduct(
            @PathVariable("product_id") String productId
    ) {
        return priceComparisonService.getCompetitorOffers(productId);
    }
}
