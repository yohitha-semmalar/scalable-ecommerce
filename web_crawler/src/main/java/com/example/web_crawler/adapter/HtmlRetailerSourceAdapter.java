
package com.example.web_crawler.adapter;

import com.example.web_crawler.model.ProductOffer;
import com.example.web_crawler.model.SearchFilter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public abstract class HtmlRetailerSourceAdapter
        implements RetailerSourceAdapter {

    private final WebClient webClient;
    private final String baseUrl;

    protected HtmlRetailerSourceAdapter(String baseUrl, WebClient webClient) {
        this.baseUrl = baseUrl;
        this.webClient = webClient;
    }

    protected String getBaseUrl() {
        return baseUrl;
    }

    @Override
    public Mono<List<ProductOffer>> search(
            String query,
            SearchFilter filter) {

        String searchUrl = baseUrl + "/mock-store/search?q="
                + java.net.URLEncoder.encode(
                        query == null ? "" : query,
                        java.nio.charset.StandardCharsets.UTF_8);

        return webClient.get()
                .uri(searchUrl)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(3))
                .map(html -> parseProducts(html))
                .onErrorResume(error -> {
                    return Mono.error(new RuntimeException(
                            "HTML search failed for " + getDisplayName()
                                    + ": " + error.getMessage(),
                            error));
                });
    }

    @Override
    public Mono<ProductOffer> getProductById(String productId) {
        String productUrl = baseUrl + "/store/product/"
                + java.net.URLEncoder.encode(
                        productId == null ? "" : productId,
                        java.nio.charset.StandardCharsets.UTF_8);

        return webClient.get()
                .uri(productUrl)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(3))
                .map(html -> parseProductPage(html, productId));
    }

    private List<ProductOffer> parseProducts(String html) {
        Document document = Jsoup.parse(html, baseUrl);
        Elements cards = document.select(".product-card");

        List<ProductOffer> offers = new ArrayList<>();

        for (Element card : cards) {
            ProductOffer offer = parseCard(card);

            if (offer != null) {
                offers.add(offer);
            }
        }

        return offers;
    }

    private ProductOffer parseCard(Element card) {
        String productId = card.attr("data-product-id");
        String name = card.selectFirst(".product-name") != null
                ? card.selectFirst(".product-name").text()
                : "";

        if (productId.isBlank() || name.isBlank()) {
            return null;
        }

        String canonicalId = card.attr("data-canonical-id");
        String brand = text(card, ".product-brand");
        String priceText = card.select(".product-price").attr("data-price");
        double price = parsePrice(priceText);

        String availabilityText = text(card, ".product-availability")
                .toLowerCase(Locale.ROOT);

        boolean available = availabilityText.contains("in stock");

        Element image = card.selectFirst(".product-image");

        String imageUrl = null;

        if (image != null) {
            imageUrl = image.hasAttr("src")
                    ? image.absUrl("src")
                    : image.absUrl("data-src");

            if (imageUrl.isBlank()) {
                imageUrl = null;
            }
        }

        Element link = card.selectFirst(".product-url");
        String productUrl = link != null
                ? link.absUrl("href")
                : baseUrl + "/store/product/" + productId;

        return ProductOffer.builder()
                .productId(productId)
                .canonicalId(canonicalId.isBlank() ? null : canonicalId)
                .name(name)
                .brand(brand.isBlank() ? null : brand)
                .price(price)
                .currency("INR")
                .availability(available)
                .productUrl(productUrl)
                .imageUrl(imageUrl)
                .source(getDisplayName())
                .mock(true)
                .retrievalTimestamp(Instant.now().toString())
                .build();
    }

    private ProductOffer parseProductPage(String html, String requestedId) {
        Document document = Jsoup.parse(html, baseUrl);
        Element product = document.selectFirst(".product-detail");

        if (product == null) {
            throw new IllegalStateException(
                    "Product HTML was missing product details for "
                            + requestedId);
        }

        ProductOffer offer = parseCard(product);

        if (offer == null) {
            throw new IllegalStateException(
                    "Could not parse product " + requestedId);
        }

        return offer;
    }

    private static String text(Element element, String selector) {
        Element selected = element.selectFirst(selector);
        return selected == null ? "" : selected.text().trim();
    }

    private static double parsePrice(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception ignored) {
            return 0.0;
        }
    }
}
