package de.epiceric.shopchest.catalog;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopSearchPageReconcilerTest {

    @Test
    void keepsCapturedOrderingButOmitsRowsThatAreNoLongerInStock() {
        final ResolvedMaterial material = new ResolvedMaterial(
                Material.STONE_BRICKS, "minecraft:stone_bricks");
        final PublicShopListing first = listing(1);
        final PublicShopListing second = listing(2);
        final ShopSearchPage page = ShopSearchSnapshot.capture(
                material,
                Instant.parse("2026-08-31T12:00:00Z"),
                List.of(first, second)).page(1);

        final ReconciledSearchPage reconciled = ShopSearchPageReconciler.reconcile(
                page,
                Map.of(
                        1, new ListingStock(ListingAvailability.IN_STOCK, 48, 3),
                        2, new ListingStock(ListingAvailability.OUT_OF_STOCK, 8, 0)));

        assertEquals(List.of(1), reconciled.listings().stream()
                .map(listing -> listing.candidate().shopId())
                .toList());
        assertEquals(3, reconciled.listings().getFirst().stock().completeBundles());
        assertEquals(1, reconciled.changedRows());
    }

    private static PublicShopListing listing(int shopId) {
        return new PublicShopListing(
                new PublicShopCandidate(
                        shopId,
                        new UUID(0L, shopId),
                        Material.STONE_BRICKS,
                        16,
                        32.0D,
                        PublicShopKind.NORMAL,
                        false),
                new TestItemStack(Material.STONE_BRICKS, 1),
                new ListingStock(ListingAvailability.IN_STOCK, 32, 2));
    }
}
