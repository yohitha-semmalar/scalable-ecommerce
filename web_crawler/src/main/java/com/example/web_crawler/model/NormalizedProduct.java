package com.example.web_crawler.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NormalizedProduct implements Serializable {
    private static final long serialVersionUID = 1L;

    private String canonicalId;
    private String title;
    private String brand;
    private String model;
    private String generation;
    private String variant;
    private String category;
    private String primaryImage;
    private double lowestPrice;
    private String lowestPriceSource;
    private double highestPrice;
    private double priceSpread;
    private double averagePrice;
    private Double averageRating;
    private int totalReviews;
    private boolean anyAvailable;
    private int offerCount;
    private List<ProductOffer> offers;
    private boolean mock;
}
