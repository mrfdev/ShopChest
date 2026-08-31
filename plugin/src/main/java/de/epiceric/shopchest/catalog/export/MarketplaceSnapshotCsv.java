package de.epiceric.shopchest.catalog.export;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

public final class MarketplaceSnapshotCsv {

    private static final String HEADER = String.join(",",
            "captured_at",
            "display_timezone",
            "source_version",
            "schema_version",
            "candidates",
            "published",
            "in_stock",
            "out_of_stock",
            "unchecked",
            "excluded_unavailable",
            "excluded_invalid",
            "banner",
            "marketplace_label",
            "owner_name",
            "storefront_name",
            "directions",
            "material",
            "item_name",
            "variant_summary",
            "bundle_amount",
            "customer_buy_price",
            "customer_buy_unit_price",
            "availability_at_capture",
            "location_label");

    private MarketplaceSnapshotCsv() {
    }

    public static String render(MarketplaceSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");

        final StringBuilder csv = new StringBuilder(HEADER.length() + 256);
        csv.append(HEADER).append("\r\n");
        if (snapshot.listings().isEmpty()) {
            csv.append(renderRow(snapshot.metadata(), snapshot.counts(), null))
                    .append("\r\n");
            return csv.toString();
        }
        for (MarketplaceListing listing : snapshot.listings()) {
            csv.append(renderRow(snapshot.metadata(), snapshot.counts(), listing))
                    .append("\r\n");
        }
        return csv.toString();
    }

    private static String renderRow(
            MarketplaceSnapshotMetadata metadata,
            MarketplaceSnapshotCounts counts,
            MarketplaceListing listing
    ) {
        final List<String> cells = new ArrayList<>(24);
        cells.add(textCell(metadata.capturedAt().toString()));
        cells.add(textCell(metadata.displayZone().getId()));
        cells.add(textCell(metadata.sourceVersion()));
        cells.add(Integer.toString(MarketplaceSnapshot.SCHEMA_VERSION));
        cells.add(Integer.toString(counts.candidates()));
        cells.add(Integer.toString(counts.published()));
        cells.add(Integer.toString(counts.inStock()));
        cells.add(Integer.toString(counts.outOfStock()));
        cells.add(Integer.toString(counts.unchecked()));
        cells.add(Integer.toString(counts.excludedUnavailable()));
        cells.add(Integer.toString(counts.excludedInvalid()));
        cells.add(textCell(metadata.banner()));
        cells.add(textCell(metadata.marketplaceLabel()));
        if (listing == null) {
            for (int index = 0; index < 11; index++) {
                cells.add(textCell(null));
            }
        } else {
            appendListing(cells, listing);
        }

        final StringJoiner row = new StringJoiner(",");
        cells.forEach(row::add);
        return row.toString();
    }

    private static void appendListing(List<String> cells, MarketplaceListing listing) {
        cells.add(textCell(listing.ownerName()));
        cells.add(textCell(listing.storefrontName()));
        cells.add(textCell(listing.directions()));
        cells.add(textCell(listing.material()));
        cells.add(textCell(listing.itemName()));
        cells.add(textCell(listing.variantSummary()));
        cells.add(Integer.toString(listing.bundleAmount()));
        cells.add(listing.customerBuyPrice().toPlainString());
        cells.add(listing.customerBuyUnitPrice().toPlainString());
        cells.add(textCell(listing.availabilityAtCapture().name()));
        cells.add(textCell(listing.locationLabel()));
    }

    private static String textCell(String value) {
        if (value == null) {
            return "\"\"";
        }

        final String neutralized = neutralizeFormula(value);
        return '"' + neutralized.replace("\"", "\"\"") + '"';
    }

    private static String neutralizeFormula(String value) {
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            if (isFormulaPrefix(character)) {
                return "'" + value;
            }
            if (!Character.isWhitespace(character)
                    && Character.getType(character) != Character.FORMAT) {
                return value;
            }
        }
        return value;
    }

    private static boolean isFormulaPrefix(char character) {
        return character == '='
                || character == '+'
                || character == '-'
                || character == '@'
                || character == '\t'
                || character == '\r'
                || character == '\n';
    }
}
