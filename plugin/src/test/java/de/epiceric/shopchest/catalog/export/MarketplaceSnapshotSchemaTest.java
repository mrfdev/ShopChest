package de.epiceric.shopchest.catalog.export;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class MarketplaceSnapshotSchemaTest {

    @Test
    void publicRecordsExposeOnlyApprovedFieldsAndNoStorageOrItemInternals() {
        final List<String> listingFields = fieldsOf(MarketplaceListing.class);
        final List<String> metadataFields = fieldsOf(MarketplaceSnapshotMetadata.class);
        final List<String> countFields = fieldsOf(MarketplaceSnapshotCounts.class);
        final List<String> snapshotFields = fieldsOf(MarketplaceSnapshot.class);

        assertEquals(List.of(
                "ownerName",
                "storefrontName",
                "directions",
                "material",
                "itemName",
                "variantSummary",
                "bundleAmount",
                "customerBuyPrice",
                "customerBuyUnitPrice",
                "availabilityAtCapture",
                "locationLabel"), listingFields);
        assertEquals(
                List.of("capturedAt", "displayZone", "sourceVersion", "banner", "marketplaceLabel"),
                metadataFields);
        assertEquals(List.of(
                "candidates", "published", "inStock", "outOfStock", "unchecked",
                "excludedUnavailable", "excludedInvalid"), countFields);
        assertEquals(List.of("metadata", "counts", "listings"), snapshotFields);

        final Set<String> forbiddenFields = Set.of(
                "uuid",
                "ownerId",
                "ownerUuid",
                "vendorId",
                "vendorUuid",
                "shopId",
                "world",
                "worldName",
                "x",
                "y",
                "z",
                "coordinates",
                "location",
                "blockX",
                "blockY",
                "blockZ",
                "rawItem",
                "itemStack",
                "itemMeta",
                "serializedProduct",
                "serializedItemStack",
                "product",
                "pdc",
                "persistentDataContainer",
                "book",
                "bookMeta",
                "bookPages",
                "pages");
        final List<String> publicFields = Stream.of(
                        listingFields, metadataFields, countFields, snapshotFields)
                .flatMap(List::stream)
                .toList();

        assertFalse(publicFields.stream().anyMatch(forbiddenFields::contains));
    }

    @Test
    void listingAvailabilityContainsOnlyPublishableSnapshotStates() {
        assertArrayEquals(
                new ListingAvailability[] {
                    ListingAvailability.IN_STOCK,
                    ListingAvailability.OUT_OF_STOCK,
                    ListingAvailability.UNCHECKED
                },
                ListingAvailability.values());
    }

    @Test
    void marketplaceSnapshotDefensivelyCopiesItsListings() {
        final List<MarketplaceListing> mutableListings = new ArrayList<>();
        mutableListings.add(listing());

        final MarketplaceSnapshot snapshot = new MarketplaceSnapshot(
                new MarketplaceSnapshotMetadata(
                        Instant.parse("2026-08-31T12:34:56Z"),
                        ZoneId.of("Europe/Amsterdam"),
                        "1.15.2+783",
                        "Captured in August 2026. Prices and stock may have changed.",
                        "/warp shops"),
                new MarketplaceSnapshotCounts(1, 1, 1, 0, 0, 0, 0),
                mutableListings);
        mutableListings.clear();

        assertEquals(1, snapshot.listings().size());
        assertThrows(UnsupportedOperationException.class, snapshot.listings()::clear);
    }

    @Test
    void snapshotMetadataRequiresAVisibleBannerAndMarketplaceLabel() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new MarketplaceSnapshotMetadata(
                                Instant.parse("2026-08-31T12:34:56Z"),
                                ZoneId.of("Europe/Amsterdam"),
                                "1.15.2+783",
                                " ",
                                "/warp shops")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new MarketplaceSnapshotMetadata(
                                Instant.parse("2026-08-31T12:34:56Z"),
                                ZoneId.of("Europe/Amsterdam"),
                                "1.15.2+783",
                                "Prices and stock may have changed.",
                                " ")));
    }

    @Test
    void shopListingRejectsMissingOrInvalidPublicValues() {
        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> listingWith(null, "STONE_BRICKS", 64,
                                new BigDecimal("100.00"), ListingAvailability.IN_STOCK)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> listingWith(" ", "STONE_BRICKS", 64,
                                new BigDecimal("100.00"), ListingAvailability.IN_STOCK)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> listingWith("Builder", "stone bricks", 64,
                                new BigDecimal("100.00"), ListingAvailability.IN_STOCK)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> listingWith("Builder", "STONE_BRICKS", 0,
                                new BigDecimal("100.00"), ListingAvailability.IN_STOCK)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> listingWith("Builder", "STONE_BRICKS", 64,
                                BigDecimal.ZERO, ListingAvailability.IN_STOCK)),
                () -> assertThrows(NullPointerException.class,
                        () -> listingWith("Builder", "STONE_BRICKS", 64,
                                new BigDecimal("100.00"), null)));
    }

    private static MarketplaceListing listing() {
        return listingWith(
                "Builder", "STONE_BRICKS", 64,
                new BigDecimal("100.00"), ListingAvailability.IN_STOCK);
    }

    private static MarketplaceListing listingWith(
            String ownerName,
            String material,
            int amount,
            BigDecimal price,
            ListingAvailability availability
    ) {
        return new MarketplaceListing(
                ownerName,
                "Builder's Blocks",
                "Aisle A",
                material,
                "Stone Bricks",
                null,
                amount,
                price,
                new BigDecimal("1.5625"),
                availability,
                                "Stall 7");
    }

    private static List<String> fieldsOf(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }
}
