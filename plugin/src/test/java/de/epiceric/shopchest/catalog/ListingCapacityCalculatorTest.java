package de.epiceric.shopchest.catalog;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ListingCapacityCalculatorTest {

    @Test
    void reportsHowManyCompleteExactBundlesACustomerCanSellToTheShop() {
        final TestItemStack template = new TestItemStack(
                Material.STONE_BRICKS, 1, "smooth");
        final ListingCapacity capacity = ListingCapacityCalculator.inspect(
                template,
                64,
                List.of(
                        new TestItemStack(Material.STONE_BRICKS, 60, "smooth"),
                        new TestItemStack(Material.DIRT, 64),
                        new TestItemStack(Material.AIR, 0)),
                ItemStack::isSimilar,
                ignored -> 64);

        assertEquals(ListingCapacityState.CAN_ACCEPT, capacity.state());
        assertEquals(68, capacity.matchingItemCapacity());
        assertEquals(1, capacity.completeBundles());
    }

    @Test
    void requiresRoomForOneCompleteConfiguredBundle() {
        final TestItemStack template = new TestItemStack(
                Material.STONE_BRICKS, 1, "smooth");
        final ListingCapacity capacity = ListingCapacityCalculator.inspect(
                template,
                64,
                List.of(new TestItemStack(Material.STONE_BRICKS, 1, "smooth")),
                ItemStack::isSimilar,
                ignored -> 64);

        assertEquals(ListingCapacityState.FULL, capacity.state());
        assertEquals(63, capacity.matchingItemCapacity());
        assertEquals(0, capacity.completeBundles());
    }

    @Test
    void doesNotCountSpaceInAStackWithDifferentItemMetadata() {
        final TestItemStack template = new TestItemStack(
                Material.STONE_BRICKS, 1, "smooth");
        final ListingCapacity capacity = ListingCapacityCalculator.inspect(
                template,
                1,
                List.of(new TestItemStack(Material.STONE_BRICKS, 1, "cracked")),
                ItemStack::isSimilar,
                ignored -> 64);

        assertEquals(ListingCapacityState.FULL, capacity.state());
        assertEquals(0, capacity.matchingItemCapacity());
    }
}
