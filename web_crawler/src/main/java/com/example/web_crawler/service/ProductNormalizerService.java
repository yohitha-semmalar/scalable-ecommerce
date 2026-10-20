package com.example.web_crawler.service;

import com.example.web_crawler.model.NormalizedProduct;
import com.example.web_crawler.model.ProductOffer;
import com.example.web_crawler.model.SearchFilter;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ProductNormalizerService {

    private static final Pattern GENERATION_PATTERN = Pattern.compile("(?i)\\b(\\d+)(?:st|nd|rd|th)?\\s*(?:gen|generation)\\b");
    private static final Pattern STORAGE_PATTERN = Pattern.compile("(?i)\\b(\\d+\\s*(?:gb|tb))\\b");
    private static final Pattern PRO_MAX_PATTERN = Pattern.compile("(?i)\\b(pro\\s*max|pro|max|mini|plus|ultra)\\b");

    /**
     * Group a list of raw offers into conservatively matched NormalizedProducts.
     */
    public List<NormalizedProduct> groupAndNormalize(List<ProductOffer> offers, SearchFilter filter) {
        if (offers == null || offers.isEmpty()) {
            return Collections.emptyList();
        }

        // Group offers by canonical identity
        Map<String, List<ProductOffer>> grouped = new LinkedHashMap<>();

        for (ProductOffer offer : offers) {
            String key = determineGroupingKey(offer);
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(offer);
        }

        List<NormalizedProduct> normalizedList = new ArrayList<>();

        for (Map.Entry<String, List<ProductOffer>> entry : grouped.entrySet()) {
            List<ProductOffer> groupOffers = entry.getValue();
            ProductOffer primary = groupOffers.get(0);

            // Sort offers in this group by price ascending
            groupOffers.sort(Comparator.comparingDouble(ProductOffer::getPrice));

            // Find lowest price among available offers, or lowest overall if none available
            ProductOffer lowestOffer = groupOffers.stream()
                    .filter(ProductOffer::isAvailability)
                    .findFirst()
                    .orElse(groupOffers.get(0));

            double lowestPrice = lowestOffer.getPrice();
            String lowestSource = lowestOffer.getSource();

            double highestPrice = groupOffers.stream()
                    .mapToDouble(ProductOffer::getPrice)
                    .max()
                    .orElse(lowestPrice);

            double avgPrice = groupOffers.stream()
                    .mapToDouble(ProductOffer::getPrice)
                    .average()
                    .orElse(lowestPrice);

            double avgRating = groupOffers.stream()
                    .filter(o -> o.getRating() != null && o.getRating() > 0)
                    .mapToDouble(ProductOffer::getRating)
                    .average()
                    .orElse(primary.getRating() != null ? primary.getRating() : 0.0);

            int totalReviews = groupOffers.stream()
                    .filter(o -> o.getReviewsCount() != null)
                    .mapToInt(ProductOffer::getReviewsCount)
                    .sum();

            boolean anyAvailable = groupOffers.stream().anyMatch(ProductOffer::isAvailability);
            boolean isMock = groupOffers.stream().allMatch(ProductOffer::isMock);

            NormalizedProduct product = NormalizedProduct.builder()
                    .canonicalId(entry.getKey())
                    .title(extractCleanTitle(primary))
                    .brand(primary.getBrand())
                    .model(primary.getModel())
                    .generation(primary.getGeneration())
                    .variant(primary.getVariant())
                    .category(primary.getCategory())
                    .primaryImage(primary.getImageUrl())
                    .lowestPrice(round(lowestPrice))
                    .lowestPriceSource(lowestSource)
                    .highestPrice(round(highestPrice))
                    .priceSpread(round(highestPrice - lowestPrice))
                    .averagePrice(round(avgPrice))
                    .averageRating(avgRating > 0 ? round(avgRating) : null)
                    .totalReviews(totalReviews)
                    .anyAvailable(anyAvailable)
                    .offerCount(groupOffers.size())
                    .offers(groupOffers)
                    .mock(isMock)
                    .build();

            normalizedList.add(product);
        }

        // Apply filters
        List<NormalizedProduct> filtered = normalizedList.stream()
                .filter(p -> filter == null || matchesFilter(p, filter))
                .collect(Collectors.toList());

        // Apply sorting
        if (filter != null && filter.getSortBy() != null) {
            applySorting(filtered, filter.getSortBy());
        } else {
            // Default sort: lowest price first
            filtered.sort(Comparator.comparingDouble(NormalizedProduct::getLowestPrice));
        }

        return filtered;
    }

    /**
     * Determines a conservative grouping key to ensure non-equivalent products are NEVER grouped.
     */
    public String determineGroupingKey(ProductOffer offer) {
        if (offer.getCanonicalId() != null && !offer.getCanonicalId().isBlank()) {
            return offer.getCanonicalId().toLowerCase(Locale.ROOT).trim();
        }

        // Extract key components
        String brand = normalize(offer.getBrand());
        String name = normalize(offer.getName());

        String gen = extractPattern(offer.getName(), GENERATION_PATTERN);
        if (gen.isEmpty() && offer.getGeneration() != null) {
            gen = normalize(offer.getGeneration());
        }

        String storage = extractPattern(offer.getName(), STORAGE_PATTERN);
        String proMax = extractPattern(offer.getName(), PRO_MAX_PATTERN);

        // Build composite key
        StringBuilder sb = new StringBuilder();
        if (!brand.isEmpty()) sb.append(brand).append(":");
        if (!proMax.isEmpty()) sb.append(proMax).append(":");
        if (!gen.isEmpty()) sb.append("gen-").append(gen).append(":");
        if (!storage.isEmpty()) sb.append("storage-").append(storage).append(":");

        if (sb.length() > 0) {
            return sb.toString();
        }

        return name;
    }

    private boolean matchesFilter(NormalizedProduct p, SearchFilter filter) {
        if (filter.getBrand() != null && !filter.getBrand().isBlank()
                && !normalize(p.getBrand()).equalsIgnoreCase(normalize(filter.getBrand()))) {
            return false;
        }
        if (filter.getCategory() != null && !filter.getCategory().isBlank()
                && !normalize(p.getCategory()).contains(normalize(filter.getCategory()))) {
            return false;
        }
        if (filter.getMinPrice() != null && p.getLowestPrice() < filter.getMinPrice()) {
            return false;
        }
        if (filter.getMaxPrice() != null && p.getLowestPrice() > filter.getMaxPrice()) {
            return false;
        }
        if (filter.getMinRating() != null && (p.getAverageRating() == null || p.getAverageRating() < filter.getMinRating())) {
            return false;
        }
        if (Boolean.TRUE.equals(filter.getAvailableOnly()) && !p.isAnyAvailable()) {
            return false;
        }
        if (filter.getPlatforms() != null && !filter.getPlatforms().isEmpty()) {
            boolean hasPlatform = p.getOffers().stream()
                    .anyMatch(o -> filter.getPlatforms().contains(o.getSource()));
            if (!hasPlatform) {
                return false;
            }
        }
        return true;
    }

    private void applySorting(List<NormalizedProduct> list, String sortBy) {
        switch (sortBy.toLowerCase(Locale.ROOT)) {
            case "price_desc":
                list.sort(Comparator.comparingDouble(NormalizedProduct::getLowestPrice).reversed());
                break;
            case "rating":
                list.sort((a, b) -> Double.compare(
                        b.getAverageRating() != null ? b.getAverageRating() : 0.0,
                        a.getAverageRating() != null ? a.getAverageRating() : 0.0));
                break;
            case "discount":
                list.sort((a, b) -> {
                    int maxDiscA = a.getOffers().stream().mapToInt(o -> o.getDiscountPercent() != null ? o.getDiscountPercent() : 0).max().orElse(0);
                    int maxDiscB = b.getOffers().stream().mapToInt(o -> o.getDiscountPercent() != null ? o.getDiscountPercent() : 0).max().orElse(0);
                    return Integer.compare(maxDiscB, maxDiscA);
                });
                break;
            case "spread":
                list.sort(Comparator.comparingDouble(NormalizedProduct::getPriceSpread).reversed());
                break;
            case "price_asc":
            default:
                list.sort(Comparator.comparingDouble(NormalizedProduct::getLowestPrice));
                break;
        }
    }

    private String extractCleanTitle(ProductOffer offer) {
        if (offer.getName() == null) return "Unknown Product";
        String name = offer.getName();
        // Remove trailing retailer noise
        name = name.replaceAll("(?i)\\s*-\\s*(Instant|Delivered in|with MagSafe|Bluetooth Headset).*", "");
        return name.trim();
    }

    private String extractPattern(String text, Pattern pattern) {
        if (text == null) return "";
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            return normalize(m.group(1));
        }
        return "";
    }

    public static String normalize(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static double round(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}
