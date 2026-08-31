package de.epiceric.shopchest.catalog;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OwnerListingInterleaverTest {

    private static final UUID OWNER_A = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OWNER_B = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID OWNER_C = UUID.fromString("30000000-0000-0000-0000-000000000003");

    @Test
    void givesEachDistinctOwnerOneListingBeforeFillingWithDuplicates() {
        final List<PublicShopListing> ordered = OwnerListingInterleaver.interleave(List.of(
                listing(2, OWNER_A, 24.0),
                listing(4, OWNER_C, 48.0),
                listing(1, OWNER_A, 16.0),
                listing(3, OWNER_B, 32.0)));

        assertEquals(List.of(1, 3, 4, 2), ordered.stream()
                .map(listing -> listing.candidate().shopId())
                .toList());
    }

    @Test
    void usesUnitPriceThenShopIdAsDeterministicTieBreakers() {
        final List<PublicShopListing> ordered = OwnerListingInterleaver.interleave(List.of(
                listing(9, OWNER_A, 32.0),
                listing(8, OWNER_A, 16.0),
                listing(7, OWNER_A, 16.0)));

        assertEquals(List.of(7, 8, 9), ordered.stream()
                .map(listing -> listing.candidate().shopId())
                .toList());
    }

    @Test
    void listingKeepsAnAmountNormalizedDefensiveProductCopy() {
        final TestItemStack product = new TestItemStack(Material.STONE_BRICKS, 12);
        final PublicShopCandidate candidate = new PublicShopCandidate(
                10,
                OWNER_A,
                Material.STONE_BRICKS,
                16,
                16.0,
                PublicShopKind.NORMAL,
                false);
        final PublicShopListing listing = new PublicShopListing(
                candidate,
                product,
                new ListingStock(ListingAvailability.IN_STOCK, 32, 2));

        product.setAmount(33);
        final TestItemStack firstRead = (TestItemStack) listing.productTemplate();
        firstRead.setAmount(44);

        assertEquals(1, listing.productTemplate().getAmount());
    }

    private static PublicShopListing listing(int id, UUID owner, double price) {
        final PublicShopCandidate candidate = new PublicShopCandidate(
                id,
                owner,
                Material.STONE_BRICKS,
                16,
                price,
                PublicShopKind.NORMAL,
                false);
        return new PublicShopListing(
                candidate,
                new TestItemStack(Material.STONE_BRICKS, 1),
                new ListingStock(ListingAvailability.IN_STOCK, 32, 2));
    }
}
