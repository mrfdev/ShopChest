package de.epiceric.shopchest.catalog.export;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

public record MarketplaceListing(
        String ownerName,
        String storefrontName,
        String directions,
        String material,
        String itemName,
        String variantSummary,
        int bundleAmount,
        BigDecimal customerBuyPrice,
        BigDecimal customerBuyUnitPrice,
        ListingAvailability availabilityAtCapture,
        String locationLabel
) {

    private static final Pattern MATERIAL_KEY = Pattern.compile("[A-Z][A-Z0-9_]*");

    public MarketplaceListing {
        requireNonBlank(ownerName, "ownerName");
        requireOptionalNonBlank(storefrontName, "storefrontName");
        requireOptionalNonBlank(directions, "directions");
        requireNonBlank(material, "material");
        if (!MATERIAL_KEY.matcher(material).matches()) {
            throw new IllegalArgumentException("material must be a canonical uppercase key");
        }
        requireNonBlank(itemName, "itemName");
        requireOptionalNonBlank(variantSummary, "variantSummary");
        if (bundleAmount <= 0) {
            throw new IllegalArgumentException("bundleAmount must be positive");
        }
        requirePositive(customerBuyPrice, "customerBuyPrice");
        requirePositive(customerBuyUnitPrice, "customerBuyUnitPrice");
        Objects.requireNonNull(availabilityAtCapture, "availabilityAtCapture");
        requireOptionalNonBlank(locationLabel, "locationLabel");
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static void requireOptionalNonBlank(String value, String name) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(name + " must be null or non-blank");
        }
    }

    private static void requirePositive(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
