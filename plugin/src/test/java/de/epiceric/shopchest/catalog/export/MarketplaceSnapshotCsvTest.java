package de.epiceric.shopchest.catalog.export;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

class MarketplaceSnapshotCsvTest {

    @Test
    void neutralizesFormulaPrefixesAndQuotesEveryPublicTextCell() {
        final MarketplaceSnapshot snapshot = new MarketplaceSnapshot(
                new MarketplaceSnapshotMetadata(
                        Instant.parse("2026-08-31T12:34:56Z"),
                        ZoneId.of("Europe/Amsterdam"),
                        "1.15.2+783",
                        "=WEBSERVICE(\"https://example.invalid\")",
                        "/warp shops"),
                new MarketplaceSnapshotCounts(4, 1, 1, 0, 0, 2, 1),
                List.of(new MarketplaceListing(
                        "=CMD|' /C calc'!A0",
                        "  +SUM(1,1)",
                        "@IMPORTXML(\"https://example.invalid\")",
                        "STONE_BRICKS",
                        "-1+2",
                        "\t=1+1",
                        64,
                        new BigDecimal("100.00"),
                        new BigDecimal("1.5625"),
                        ListingAvailability.IN_STOCK,
                        "Stall \"7\", north")));

        assertEquals(
                "captured_at,display_timezone,source_version,schema_version,candidates,published,in_stock,"
                        + "out_of_stock,unchecked,excluded_unavailable,excluded_invalid,"
                        + "banner,marketplace_label,owner_name,"
                        + "storefront_name,directions,material,item_name,variant_summary,"
                        + "bundle_amount,customer_buy_price,customer_buy_unit_price,"
                        + "availability_at_capture,location_label\r\n"
                        + "\"2026-08-31T12:34:56Z\",\"Europe/Amsterdam\",\"1.15.2+783\","
                        + "2,4,1,1,0,0,2,1,"
                        + "\"'=WEBSERVICE(\"\"https://example.invalid\"\")\","
                        + "\"/warp shops\",\"'=CMD|' /C calc'!A0\","
                        + "\"'  +SUM(1,1)\","
                        + "\"'@IMPORTXML(\"\"https://example.invalid\"\")\","
                        + "\"STONE_BRICKS\",\"'-1+2\",\"'\t=1+1\","
                        + "64,100.00,1.5625,\"IN_STOCK\","
                        + "\"Stall \"\"7\"\", north\"\r\n",
                MarketplaceSnapshotCsv.render(snapshot));
    }

    @Test
    void keepsCaptureMetadataAndCountsWhenThereAreNoPublishedListings() {
        final MarketplaceSnapshot snapshot = new MarketplaceSnapshot(
                new MarketplaceSnapshotMetadata(
                        Instant.parse("2026-08-31T12:34:56Z"),
                        ZoneId.of("Europe/Amsterdam"),
                        "1.15.2+783",
                        "No listings were publishable.",
                        "/warp shops"),
                new MarketplaceSnapshotCounts(2, 0, 0, 0, 0, 1, 1),
                List.of());

        final String[] lines = MarketplaceSnapshotCsv.render(snapshot)
                .split("\\r\\n", -1);
        assertEquals(3, lines.length);
        assertEquals(
                "\"2026-08-31T12:34:56Z\",\"Europe/Amsterdam\",\"1.15.2+783\","
                        + "2,2,0,0,0,0,1,1,\"No listings were publishable.\",\"/warp shops\","
                        + "\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\"",
                lines[1]);
    }
}
