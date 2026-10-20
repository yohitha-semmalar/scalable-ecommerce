package com.example.web_crawler;

import com.example.web_crawler.model.NormalizedProduct;
import com.example.web_crawler.model.ProductOffer;
import com.example.web_crawler.model.SearchFilter;
import com.example.web_crawler.service.ProductNormalizerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ProductNormalizerServiceTest {

    private ProductNormalizerService normalizerService;

    @BeforeEach
    void setUp() {
        normalizerService = new ProductNormalizerService();
    }

    @Test
    void testConservativeMatching_SeparateGenerationsNotGrouped() {
        ProductOffer airpodsPro2 = ProductOffer.builder()
                .productId("P1")
                .name("Apple AirPods Pro (2nd Generation) with USB-C")
                .brand("Apple")
                .model("AirPods Pro")
                .generation("2nd Generation")
                .price(20999.0)
                .availability(true)
                .source("Amazon India")
                .build();

        ProductOffer airpods3 = ProductOffer.builder()
                .productId("P2")
                .name("Apple AirPods (3rd Generation) with Lightning Case")
                .brand("Apple")
                .model("AirPods")
                .generation("3rd Generation")
                .price(15990.0)
                .availability(true)
                .source("Amazon India")
                .build();

        List<NormalizedProduct> products = normalizerService.groupAndNormalize(
                List.of(airpodsPro2, airpods3),
                SearchFilter.builder().build()
        );

        // They must NOT be merged together into one product!
        assertEquals(2, products.size(), "AirPods Pro 2nd Gen and AirPods 3rd Gen must be separate products");
    }

    @Test
    void testConservativeMatching_SeparateStorageVariantsNotGrouped() {
        ProductOffer iphone128 = ProductOffer.builder()
                .productId("IP1")
                .name("Apple iPhone 15 (128 GB) - Black")
                .brand("Apple")
                .model("iPhone 15")
                .price(65999.0)
                .availability(true)
                .source("Amazon India")
                .build();

        ProductOffer iphone256 = ProductOffer.builder()
                .productId("IP2")
                .name("Apple iPhone 15 (256 GB) - Black")
                .brand("Apple")
                .model("iPhone 15")
                .price(75999.0)
                .availability(true)
                .source("Amazon India")
                .build();

        List<NormalizedProduct> products = normalizerService.groupAndNormalize(
                List.of(iphone128, iphone256),
                SearchFilter.builder().build()
        );

        assertEquals(2, products.size(), "128GB and 256GB variants must not be combined");
    }

    @Test
    void testEquivalentOffers_GroupedAndLowestPriceCalculated() {
        ProductOffer amazonOffer = ProductOffer.builder()
                .canonicalId("airpods-pro-2-usbc")
                .productId("AMZ-1")
                .name("Apple AirPods Pro (2nd Gen) with MagSafe Case (USB-C)")
                .brand("Apple")
                .price(20999.0)
                .availability(true)
                .source("Amazon India")
                .rating(4.6)
                .build();

        ProductOffer flipkartOffer = ProductOffer.builder()
                .canonicalId("airpods-pro-2-usbc")
                .productId("FK-1")
                .name("Apple AirPods Pro (2nd Generation) Type C")
                .brand("Apple")
                .price(20499.0)
                .availability(true)
                .source("Flipkart")
                .rating(4.7)
                .build();

        ProductOffer meeshoOffer = ProductOffer.builder()
                .canonicalId("airpods-pro-2-usbc")
                .productId("MSH-1")
                .name("Apple AirPods Pro 2nd Gen (Open Box)")
                .brand("Apple")
                .price(18999.0)
                .availability(false) // OUT OF STOCK
                .source("Meesho")
                .build();

        List<NormalizedProduct> products = normalizerService.groupAndNormalize(
                List.of(amazonOffer, flipkartOffer, meeshoOffer),
                SearchFilter.builder().build()
        );

        assertEquals(1, products.size(), "Equivalent offers must group into 1 normalized product");
        NormalizedProduct product = products.get(0);
        assertEquals(3, product.getOfferCount());
        // Lowest available price should be Flipkart (20499) since Meesho (18999) is out of stock!
        assertEquals(20499.0, product.getLowestPrice(), 0.01);
        assertEquals("Flipkart", product.getLowestPriceSource());
        assertEquals(20999.0, product.getHighestPrice(), 0.01);
        assertEquals(500.0, product.getPriceSpread(), 0.01);
    }

    @Test
    void testFilterByPriceAndAvailability() {
        ProductOffer cheapOffer = ProductOffer.builder()
                .canonicalId("item-cheap")
                .name("Budget Wireless Earphones")
                .brand("BrandX")
                .category("Electronics")
                .price(999.0)
                .availability(true)
                .source("Amazon India")
                .build();

        ProductOffer expOffer = ProductOffer.builder()
                .canonicalId("item-exp")
                .name("Premium Studio Earphones")
                .brand("BrandY")
                .category("Electronics")
                .price(4999.0)
                .availability(true)
                .source("Amazon India")
                .build();

        SearchFilter filter = SearchFilter.builder()
                .minPrice(2000.0)
                .maxPrice(6000.0)
                .build();

        List<NormalizedProduct> results = normalizerService.groupAndNormalize(
                List.of(cheapOffer, expOffer), filter
        );

        assertEquals(1, results.size());
        assertEquals("item-exp", results.get(0).getCanonicalId());
    }
}
