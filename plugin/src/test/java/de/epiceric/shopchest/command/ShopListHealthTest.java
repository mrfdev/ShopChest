package de.epiceric.shopchest.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopListHealthTest {

    @Test
    void reportsIndependentStockAndCapacityProblemsForACompleteBundle() {
        final ShopListHealth health = ShopListHealth.checked(
                25,
                10,
                false,
                4,
                4,
                5,
                false);

        assertTrue(health.outOfStock());
        assertTrue(health.full());
        assertTrue(health.needsAttention());
        assertFalse(health.healthy());
    }

    @Test
    void disabledIndividualDirectionsAndAdminStorageDoNotCreateFalseAlerts() {
        final ShopListHealth sellOnly = ShopListHealth.checked(
                0,
                10,
                false,
                0,
                5,
                5,
                false);
        final ShopListHealth buyOnly = ShopListHealth.checked(
                25,
                0,
                false,
                5,
                0,
                5,
                false);
        final ShopListHealth admin = ShopListHealth.checked(
                25,
                10,
                true,
                0,
                0,
                5,
                false);

        assertTrue(sellOnly.healthy());
        assertFalse(sellOnly.outOfStock());
        assertFalse(sellOnly.full());
        assertTrue(buyOnly.healthy());
        assertFalse(buyOnly.outOfStock());
        assertFalse(buyOnly.full());
        assertTrue(admin.healthy());
        assertEquals(ShopListStock.State.UNLIMITED, admin.stock().state());
        assertFalse(admin.full());
    }

    @Test
    void blockedDisplaySpaceNeedsAttentionForNormalAndAdminShops() {
        final ShopListHealth normal = ShopListHealth.checked(
                25, 10, false, 5, 5, 5, true);
        final ShopListHealth admin = ShopListHealth.checked(
                25, 10, true, 0, 0, 5, true);

        assertTrue(normal.blocked());
        assertTrue(normal.needsAttention());
        assertFalse(normal.healthy());
        assertTrue(admin.blocked());
        assertTrue(admin.needsAttention());
        assertFalse(admin.healthy());
    }

    @Test
    void distinguishesUncheckedChunksFromKnownUnavailableShops() {
        final ShopListHealth unchecked = ShopListHealth.unchecked(25, false, 5);
        final ShopListHealth unavailable = ShopListHealth.unavailable(25, false, 5);

        assertTrue(unchecked.unchecked());
        assertFalse(unchecked.needsAttention());
        assertFalse(unchecked.healthy());
        assertTrue(unavailable.unavailable());
        assertTrue(unavailable.needsAttention());
        assertFalse(unavailable.healthy());
    }

    @Test
    void summaryDeduplicatesAttentionWhileKeepingOverlappingReasonCounts() {
        final ShopHealthSummary summary = ShopHealthSummary.summarize(List.of(
                ShopListHealth.checked(25, 10, false, 4, 4, 5, false),
                ShopListHealth.checked(25, 10, false, 5, 5, 5, false),
                ShopListHealth.checked(25, 10, false, 5, 5, 5, true),
                ShopListHealth.unavailable(25, false, 5),
                ShopListHealth.unchecked(25, false, 5)));

        assertEquals(1, summary.healthy());
        assertEquals(3, summary.attention());
        assertEquals(1, summary.outOfStock());
        assertEquals(1, summary.full());
        assertEquals(1, summary.blocked());
        assertEquals(1, summary.unavailable());
        assertEquals(1, summary.unchecked());
    }
}
