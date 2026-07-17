package de.epiceric.shopchest.config.hologram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HologramTradeAvailabilityTest {

    @Test
    void requiresEnoughStockForOneCompleteTransaction() {
        assertTrue(HologramTradeAvailability.isBuyOutOfStock(12, false, 0, 5));
        assertTrue(HologramTradeAvailability.isBuyOutOfStock(12, false, 4, 5));
        assertTrue(HologramTradeAvailability.isBuyOutOfStock(12, false, 1, 123));
        assertFalse(HologramTradeAvailability.isBuyOutOfStock(12, false, 5, 5));
        assertFalse(HologramTradeAvailability.isBuyOutOfStock(12, false, 123, 123));
        assertFalse(HologramTradeAvailability.isBuyOutOfStock(0, false, 0, 5));
        assertFalse(HologramTradeAvailability.isBuyOutOfStock(12, true, 0, 5));
    }

    @Test
    void replacesOnlyTheUnavailableBuyValue() {
        assertEquals(
                "<unavailable>[Out of stock]",
                HologramTradeAvailability.formatBuyValue(
                        "$12", true, "[Out of stock]", "<unavailable>"));
        assertEquals(
                "$12",
                HologramTradeAvailability.formatBuyValue(
                        "$12", false, "[Out of stock]", "<unavailable>"));
    }
}
