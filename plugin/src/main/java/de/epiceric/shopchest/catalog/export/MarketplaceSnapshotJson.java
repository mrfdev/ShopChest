package de.epiceric.shopchest.catalog.export;

import java.util.Objects;

public final class MarketplaceSnapshotJson {

    private MarketplaceSnapshotJson() {
    }

    public static String render(MarketplaceSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");

        final StringBuilder json = new StringBuilder(512);
        json.append("{\n");
        json.append("  \"schemaVersion\": ").append(MarketplaceSnapshot.SCHEMA_VERSION).append(",\n");
        json.append("  \"documentType\": ");
        appendString(json, MarketplaceSnapshot.DOCUMENT_TYPE);
        json.append(",\n");
        appendMetadata(json, snapshot.metadata());
        json.append(",\n");
        appendCounts(json, snapshot.counts());
        json.append(",\n");
        appendListings(json, snapshot);
        json.append("\n}\n");
        return json.toString();
    }

    private static void appendCounts(
            StringBuilder json,
            MarketplaceSnapshotCounts counts
    ) {
        json.append("  \"counts\": {\n");
        appendNumberField(json, "candidates", counts.candidates(), 4, true);
        appendNumberField(json, "published", counts.published(), 4, true);
        appendNumberField(json, "inStock", counts.inStock(), 4, true);
        appendNumberField(json, "outOfStock", counts.outOfStock(), 4, true);
        appendNumberField(json, "unchecked", counts.unchecked(), 4, true);
        appendNumberField(
                json, "excludedUnavailable", counts.excludedUnavailable(), 4, true);
        appendNumberField(json, "excludedInvalid", counts.excludedInvalid(), 4, false);
        json.append("  }");
    }

    private static void appendMetadata(
            StringBuilder json,
            MarketplaceSnapshotMetadata metadata
    ) {
        json.append("  \"metadata\": {\n");
        appendStringField(json, "capturedAt", metadata.capturedAt().toString(), 4, true);
        appendStringField(json, "displayTimezone", metadata.displayZone().getId(), 4, true);
        appendStringField(json, "sourceVersion", metadata.sourceVersion(), 4, true);
        appendStringField(json, "banner", metadata.banner(), 4, true);
        appendStringField(json, "marketplaceLabel", metadata.marketplaceLabel(), 4, false);
        json.append("  }");
    }

    private static void appendListings(StringBuilder json, MarketplaceSnapshot snapshot) {
        json.append("  \"listings\": [");
        if (snapshot.listings().isEmpty()) {
            json.append(']');
            return;
        }

        json.append('\n');
        for (int index = 0; index < snapshot.listings().size(); index++) {
            appendListing(json, snapshot.listings().get(index));
            if (index + 1 < snapshot.listings().size()) {
                json.append(',');
            }
            json.append('\n');
        }
        json.append("  ]");
    }

    private static void appendListing(StringBuilder json, MarketplaceListing listing) {
        json.append("    {\n");
        appendStringField(json, "ownerName", listing.ownerName(), 6, true);
        appendNullableStringField(json, "storefrontName", listing.storefrontName(), 6, true);
        appendNullableStringField(json, "directions", listing.directions(), 6, true);
        appendStringField(json, "material", listing.material(), 6, true);
        appendStringField(json, "itemName", listing.itemName(), 6, true);
        appendNullableStringField(json, "variantSummary", listing.variantSummary(), 6, true);
        appendNumberField(json, "bundleAmount", listing.bundleAmount(), 6, true);
        appendStringField(
                json, "customerBuyPrice", listing.customerBuyPrice().toPlainString(), 6, true);
        appendStringField(
                json,
                "customerBuyUnitPrice",
                listing.customerBuyUnitPrice().toPlainString(),
                6,
                true);
        appendStringField(
                json,
                "availabilityAtCapture",
                listing.availabilityAtCapture().name(),
                6,
                true);
        appendNullableStringField(json, "locationLabel", listing.locationLabel(), 6, false);
        json.append("    }");
    }

    private static void appendStringField(
            StringBuilder json,
            String name,
            String value,
            int indentation,
            boolean comma
    ) {
        appendIndentation(json, indentation);
        appendString(json, name);
        json.append(": ");
        appendString(json, value);
        appendLineEnd(json, comma);
    }

    private static void appendNullableStringField(
            StringBuilder json,
            String name,
            String value,
            int indentation,
            boolean comma
    ) {
        appendIndentation(json, indentation);
        appendString(json, name);
        json.append(": ");
        if (value == null) {
            json.append("null");
        } else {
            appendString(json, value);
        }
        appendLineEnd(json, comma);
    }

    private static void appendNumberField(
            StringBuilder json,
            String name,
            int value,
            int indentation,
            boolean comma
    ) {
        appendIndentation(json, indentation);
        appendString(json, name);
        json.append(": ").append(value);
        appendLineEnd(json, comma);
    }

    private static void appendLineEnd(StringBuilder json, boolean comma) {
        if (comma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static void appendIndentation(StringBuilder json, int count) {
        json.append(" ".repeat(count));
    }

    private static void appendString(StringBuilder json, String value) {
        Objects.requireNonNull(value, "JSON string value");
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                case '<' -> json.append("\\u003c");
                case '>' -> json.append("\\u003e");
                case '&' -> json.append("\\u0026");
                case '\u2028', '\u2029' -> appendUnicodeEscape(json, character);
                default -> {
                    if (character < 0x20) {
                        appendUnicodeEscape(json, character);
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        json.append('"');
    }

    private static void appendUnicodeEscape(StringBuilder json, char character) {
        json.append("\\u");
        final String hexadecimal = Integer.toHexString(character);
        json.append("0".repeat(4 - hexadecimal.length())).append(hexadecimal);
    }
}
