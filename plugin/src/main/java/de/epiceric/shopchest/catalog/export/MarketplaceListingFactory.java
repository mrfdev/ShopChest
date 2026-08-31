package de.epiceric.shopchest.catalog.export;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Converts already-authorized catalogue values into the deliberately small
 * public export schema. Raw Bukkit, database, location and item metadata never
 * cross this boundary.
 */
final class MarketplaceListingFactory {

    private static final int OWNER_NAME_LENGTH = 16;
    private static final int STOREFRONT_NAME_LENGTH = 32;
    private static final int DIRECTIONS_LENGTH = 120;
    private static final int ITEM_NAME_LENGTH = 80;
    private static final int LOCATION_LABEL_LENGTH = 80;
    private static final Pattern REPEATED_WHITESPACE = Pattern.compile("\\s+");

    private MarketplaceListingFactory() {
    }

    static Optional<MarketplaceListing> create(
            String ownerName,
            String storefrontName,
            String directions,
            String material,
            String itemName,
            int bundleAmount,
            double customerBuyPrice,
            de.epiceric.shopchest.catalog.ListingAvailability availability,
            String locationLabel
    ) {
        final String safeOwnerName = requiredText(ownerName, OWNER_NAME_LENGTH);
        final String safeMaterial = requiredText(material, 64);
        final String safeItemName = requiredText(itemName, ITEM_NAME_LENGTH);
        if (safeOwnerName == null
                || safeMaterial == null
                || safeItemName == null
                || bundleAmount <= 0
                || customerBuyPrice <= 0.0D
                || !Double.isFinite(customerBuyPrice)
                || availability == null
                || availability == de.epiceric.shopchest.catalog.ListingAvailability.UNAVAILABLE) {
            return Optional.empty();
        }

        final ListingAvailability publicAvailability = switch (availability) {
            case IN_STOCK -> ListingAvailability.IN_STOCK;
            case OUT_OF_STOCK -> ListingAvailability.OUT_OF_STOCK;
            case UNCHECKED -> ListingAvailability.UNCHECKED;
            case UNAVAILABLE -> throw new IllegalStateException("Unavailable rows are not public");
        };
        final BigDecimal bundlePrice = BigDecimal.valueOf(customerBuyPrice);
        final BigDecimal unitPrice = bundlePrice.divide(
                BigDecimal.valueOf(bundleAmount), MathContext.DECIMAL128);

        try {
            return Optional.of(new MarketplaceListing(
                    safeOwnerName,
                    optionalText(storefrontName, STOREFRONT_NAME_LENGTH),
                    optionalText(directions, DIRECTIONS_LENGTH),
                    safeMaterial,
                    safeItemName,
                    null,
                    bundleAmount,
                    bundlePrice,
                    unitPrice,
                    publicAvailability,
                    optionalText(locationLabel, LOCATION_LABEL_LENGTH)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static String requiredText(String value, int maximumCodePoints) {
        final String sanitized = sanitizeText(value, maximumCodePoints);
        return sanitized == null || sanitized.isBlank() ? null : sanitized;
    }

    private static String optionalText(String value, int maximumCodePoints) {
        final String sanitized = sanitizeText(value, maximumCodePoints);
        return sanitized == null || sanitized.isBlank() ? null : sanitized;
    }

    private static String sanitizeText(String value, int maximumCodePoints) {
        if (value == null) {
            return null;
        }

        final StringBuilder plain = new StringBuilder(value.length());
        for (int offset = 0; offset < value.length();) {
            final int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == '\u00a7') {
                if (offset < value.length()) {
                    final int formattingCode = value.codePointAt(offset);
                    offset += Character.charCount(formattingCode);
                }
                continue;
            }

            final int type = Character.getType(codePoint);
            if (Character.isWhitespace(codePoint) || Character.isISOControl(codePoint)) {
                plain.append(' ');
            } else if (type != Character.FORMAT
                    && type != Character.PRIVATE_USE
                    && type != Character.SURROGATE) {
                plain.appendCodePoint(codePoint);
            }
        }

        final String collapsed = REPEATED_WHITESPACE.matcher(plain.toString().strip())
                .replaceAll(" ");
        if (collapsed.codePointCount(0, collapsed.length()) <= maximumCodePoints) {
            return collapsed;
        }
        return collapsed.substring(0, collapsed.offsetByCodePoints(0, maximumCodePoints));
    }
}
