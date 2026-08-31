package de.epiceric.shopchest.catalog.export;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

class MarketplaceSnapshotJsonTest {

    @Test
    void rendersDeterministicAllowlistedJsonAndEscapesPublicText() {
        final MarketplaceSnapshot snapshot = new MarketplaceSnapshot(
                new MarketplaceSnapshotMetadata(
                        Instant.parse("2026-08-31T12:34:56Z"),
                        ZoneId.of("Europe/Amsterdam"),
                        "1.15.2+783",
                        "August 2026 \"capture\"\nPrices may change </script>&",
                        "/warp shops"),
                new MarketplaceSnapshotCounts(4, 1, 1, 0, 0, 2, 1),
                List.of(new MarketplaceListing(
                        "Builder",
                        "Builder's Blocks",
                        "Aisle A",
                        "STONE_BRICKS",
                        "Stone Bricks",
                        null,
                        64,
                        new BigDecimal("100.00"),
                        new BigDecimal("1.5625"),
                        ListingAvailability.IN_STOCK,
                        "Stall 7")));

        assertEquals("""
                {
                  "schemaVersion": 2,
                  "documentType": "shopchest-marketplace-snapshot",
                  "metadata": {
                    "capturedAt": "2026-08-31T12:34:56Z",
                    "displayTimezone": "Europe/Amsterdam",
                    "sourceVersion": "1.15.2+783",
                    "banner": "August 2026 \\"capture\\"\\nPrices may change \\u003c/script\\u003e\\u0026",
                    "marketplaceLabel": "/warp shops"
                  },
                  "counts": {
                    "candidates": 4,
                    "published": 1,
                    "inStock": 1,
                    "outOfStock": 0,
                    "unchecked": 0,
                    "excludedUnavailable": 2,
                    "excludedInvalid": 1
                  },
                  "listings": [
                    {
                      "ownerName": "Builder",
                      "storefrontName": "Builder's Blocks",
                      "directions": "Aisle A",
                      "material": "STONE_BRICKS",
                      "itemName": "Stone Bricks",
                      "variantSummary": null,
                      "bundleAmount": 64,
                      "customerBuyPrice": "100.00",
                      "customerBuyUnitPrice": "1.5625",
                      "availabilityAtCapture": "IN_STOCK",
                      "locationLabel": "Stall 7"
                    }
                  ]
                }
                """, MarketplaceSnapshotJson.render(snapshot));
    }
}
