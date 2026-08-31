package de.epiceric.shopchest.catalog;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShopSearchSnapshotTest {

    private static final ResolvedMaterial STONE_BRICKS =
            new ResolvedMaterial(Material.STONE_BRICKS, "minecraft:stone_bricks");
    private static final Instant CAPTURED_AT = Instant.parse("2026-08-31T08:00:00Z");

    @Test
    void summarizesOnlyEligibleExactMaterialListingsAndOmitsUnavailableTotals() {
        final ShopSearchSnapshot snapshot = ShopSearchSnapshot.capture(
                STONE_BRICKS,
                CAPTURED_AT,
                List.of(
                        listing(1, owner(1), ListingAvailability.IN_STOCK, PublicShopKind.NORMAL, false),
                        listing(2, owner(1), ListingAvailability.IN_STOCK, PublicShopKind.NORMAL, false),
                        listing(3, owner(2), ListingAvailability.IN_STOCK, PublicShopKind.NORMAL, false),
                        listing(4, owner(3), ListingAvailability.OUT_OF_STOCK, PublicShopKind.NORMAL, false),
                        listing(5, owner(4), ListingAvailability.UNCHECKED, PublicShopKind.NORMAL, false),
                        listing(6, owner(5), ListingAvailability.UNAVAILABLE, PublicShopKind.NORMAL, false),
                        listing(7, owner(6), ListingAvailability.IN_STOCK, PublicShopKind.ADMIN, false),
                        listing(8, owner(7), ListingAvailability.IN_STOCK, PublicShopKind.NORMAL, true),
                        listing(9, owner(8), Material.DIRT, ListingAvailability.IN_STOCK)));

        assertEquals(new ShopSearchSummary(3, 2, 1, 1), snapshot.summary());
        assertEquals(List.of(1, 3, 2), snapshot.page(1).listings().stream()
                .map(listing -> listing.candidate().shopId())
                .toList());
    }

    @Test
    void keepsFourImmutableInStockListingsPerPage() {
        final ArrayList<PublicShopListing> source = new ArrayList<>();
        for (int id = 1; id <= 6; id++) {
            source.add(listing(
                    id,
                    owner(id),
                    ListingAvailability.IN_STOCK,
                    PublicShopKind.NORMAL,
                    false));
        }
        final ShopSearchSnapshot snapshot = ShopSearchSnapshot.capture(
                STONE_BRICKS,
                CAPTURED_AT,
                source);
        source.clear();

        assertEquals(4, snapshot.page(1).listings().size());
        assertEquals(2, snapshot.page(2).listings().size());
        assertEquals(2, snapshot.page(99).page());
        assertEquals(2, snapshot.page(1).pageCount());
        assertEquals(CAPTURED_AT, snapshot.capturedAt());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.page(1).listings().clear());
    }

    private static PublicShopListing listing(
            int id,
            UUID owner,
            ListingAvailability availability,
            PublicShopKind kind,
            boolean suspended) {
        return listing(id, owner, Material.STONE_BRICKS, availability, kind, suspended);
    }

    private static PublicShopListing listing(
            int id,
            UUID owner,
            Material material,
            ListingAvailability availability) {
        return listing(id, owner, material, availability, PublicShopKind.NORMAL, false);
    }

    private static PublicShopListing listing(
            int id,
            UUID owner,
            Material material,
            ListingAvailability availability,
            PublicShopKind kind,
            boolean suspended) {
        final PublicShopCandidate candidate = new PublicShopCandidate(
                id,
                owner,
                material,
                16,
                16.0 + id,
                kind,
                suspended);
        final ListingStock stock = switch (availability) {
            case IN_STOCK -> new ListingStock(availability, 32, 2);
            case OUT_OF_STOCK -> new ListingStock(availability, 15, 0);
            case UNCHECKED -> ListingStock.unchecked();
            case UNAVAILABLE -> ListingStock.unavailable();
        };
        return new PublicShopListing(candidate, new TestItemStack(material, 1), stock);
    }

    private static UUID owner(int suffix) {
        return UUID.fromString("10000000-0000-0000-0000-" + String.format("%012d", suffix));
    }
}
