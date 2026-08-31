package de.epiceric.shopchest.catalog.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class MarketplaceListingFactoryTest {

    @Test
    void createsAPlayerSafeListingAndCalculatesItsUnitPrice() {
        final MarketplaceListing listing = MarketplaceListingFactory.create(
                        "\u00a76Builder\nName",
                        "  Builder's   Blocks  ",
                        "Aisle\tA",
                        "STONE_BRICKS",
                        "\u00a7bStone\u0000 Bricks",
                        64,
                        100.0D,
                        de.epiceric.shopchest.catalog.ListingAvailability.IN_STOCK,
                        null)
                .orElseThrow();

        assertEquals("Builder Name", listing.ownerName());
        assertEquals("Builder's Blocks", listing.storefrontName());
        assertEquals("Aisle A", listing.directions());
        assertEquals("Stone Bricks", listing.itemName());
        assertEquals(new BigDecimal("100.0"), listing.customerBuyPrice());
        assertEquals(new BigDecimal("1.5625"), listing.customerBuyUnitPrice());
        assertEquals(ListingAvailability.IN_STOCK, listing.availabilityAtCapture());
        assertNull(listing.locationLabel());
    }

    @Test
    void omitsUnavailableAndInvalidRowsInsteadOfPublishingThem() {
        assertTrue(MarketplaceListingFactory.create(
                "Builder", null, null, "STONE_BRICKS", "Stone Bricks", 64, 100.0D,
                de.epiceric.shopchest.catalog.ListingAvailability.UNAVAILABLE, null).isEmpty());
        assertTrue(MarketplaceListingFactory.create(
                " ", null, null, "STONE_BRICKS", "Stone Bricks", 64, 100.0D,
                de.epiceric.shopchest.catalog.ListingAvailability.IN_STOCK, null).isEmpty());
        assertTrue(MarketplaceListingFactory.create(
                "Builder", null, null, "STONE_BRICKS", "Stone Bricks", 0, 100.0D,
                de.epiceric.shopchest.catalog.ListingAvailability.IN_STOCK, null).isEmpty());
    }

    @Test
    void limitsEveryAuthoredFieldByUnicodeCodePoint() {
        final MarketplaceListing listing = MarketplaceListingFactory.create(
                        "A".repeat(80),
                        "\ud83e\uddf1".repeat(80),
                        "D".repeat(200),
                        "STONE_BRICKS",
                        "I".repeat(200),
                        1,
                        1.0D,
                        de.epiceric.shopchest.catalog.ListingAvailability.UNCHECKED,
                        "L".repeat(200))
                .orElseThrow();

        assertEquals(16, listing.ownerName().codePointCount(0, listing.ownerName().length()));
        assertEquals(32, listing.storefrontName().codePointCount(0, listing.storefrontName().length()));
        assertEquals(120, listing.directions().codePointCount(0, listing.directions().length()));
        assertEquals(80, listing.itemName().codePointCount(0, listing.itemName().length()));
        assertEquals(80, listing.locationLabel().codePointCount(0, listing.locationLabel().length()));
    }
}
