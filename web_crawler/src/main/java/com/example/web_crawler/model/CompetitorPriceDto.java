package com.example.web_crawler.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Matches ScaleShop frontend CompetitorPrice schema exactly:
 * type CompetitorPrice = { source: string; price: number; available: boolean; checkedAt: string; simulated: boolean };
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorPriceDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private String source;
    private double price;
    private boolean available;
    private String checkedAt;
    private boolean simulated;
}
