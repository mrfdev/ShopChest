package de.epiceric.shopchest.catalog;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ListingAvailabilityCalculatorTest {

    private static final ExactItemMatcher EXACT_TYPE =
            (candidate, template) -> candidate.getType() == template.getType();

    @Test
    void countsOnlyExactProductsAndRequiresOneFullConfiguredBundle() {
        final ListingStock stock = ListingAvailabilityCalculator.inspect(
                new TestItemStack(Material.STONE_BRICKS, 1),
                16,
                List.of(
                        new TestItemStack(Material.STONE_BRICKS, 15),
                        new TestItemStack(Material.DIRT, 64),
                        new TestItemStack(Material.STONE_BRICKS, 16)),
                EXACT_TYPE);

        assertEquals(ListingAvailability.IN_STOCK, stock.availability());
        assertEquals(31, stock.matchingItems());
        assertEquals(1, stock.completeBundles());
    }

    @Test
    void oneItemBelowTheConfiguredBundleIsOutOfStock() {
        final ListingStock stock = ListingAvailabilityCalculator.inspect(
                new TestItemStack(Material.STONE_BRICKS, 1),
                16,
                List.of(new TestItemStack(Material.STONE_BRICKS, 15)),
                EXACT_TYPE);

        assertEquals(ListingAvailability.OUT_OF_STOCK, stock.availability());
        assertEquals(15, stock.matchingItems());
        assertEquals(0, stock.completeBundles());
    }

    @Test
    void sameBaseMaterialWithDifferentMetadataDoesNotCountAsExactStock() {
        final ListingStock stock = ListingAvailabilityCalculator.inspect(
                new TestItemStack(Material.STONE_BRICKS, 1, "configured-product"),
                16,
                List.of(
                        new TestItemStack(Material.STONE_BRICKS, 64, "different-product"),
                        new TestItemStack(Material.STONE_BRICKS, 15, "configured-product")),
                (candidate, template) -> candidate.isSimilar(template));

        assertEquals(ListingAvailability.OUT_OF_STOCK, stock.availability());
        assertEquals(15, stock.matchingItems());
    }

    @Test
    void normalizesAmountsBeforeCallingTheExactItemMatcher() {
        final ListingStock stock = ListingAvailabilityCalculator.inspect(
                new TestItemStack(Material.STONE_BRICKS, 7),
                16,
                List.of(new TestItemStack(Material.STONE_BRICKS, 32)),
                (candidate, template) -> candidate.getAmount() == 1
                        && template.getAmount() == 1
                        && candidate.getType() == template.getType());

        assertEquals(2, stock.completeBundles());
    }

    @Test
    void failsClosedWhenStockCannotBeInspectedReliably() {
        assertEquals(
                ListingAvailability.UNAVAILABLE,
                ListingAvailabilityCalculator.inspect(
                        new TestItemStack(Material.STONE_BRICKS, 1),
                        0,
                        List.of(),
                        EXACT_TYPE).availability());
        assertEquals(
                ListingAvailability.UNAVAILABLE,
                ListingAvailabilityCalculator.inspect(
                        new TestItemStack(Material.STONE_BRICKS, 1),
                        16,
                        List.of(new TestItemStack(Material.STONE_BRICKS, 16)),
                        (candidate, template) -> {
                            throw new IllegalStateException("metadata unavailable");
                        }).availability());
    }
}
