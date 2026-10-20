package com.example.web_crawler.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductOffer implements Serializable {
    private static final long serialVersionUID = 1L;

    private String productId;
    private String canonicalId;
    private String name;
    private String brand;
    private String model;
    private String generation;
    private String variant;
    private String category;
    private double price;
    private Double originalPrice;
    private String currency;
    private Double rating;
    private Integer reviewsCount;
    private Integer discountPercent;
    private boolean availability;
    private Integer stock;
    private String productUrl;
    private String imageUrl;
    private String source;
    private boolean mock;
    private String retrievalTimestamp;
    private Integer deliveryDays;
    private Integer deliveryMinutes;
    private Boolean verifiedSeller;
}
