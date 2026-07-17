package de.epiceric.shopchest.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopListStockTest {

    @Test
    void marksStockBelowOneCompleteBundleAsOutOfStock() {
        final ShopListStock stock = ShopListStock.resolve(25, false, true, 4, 5);

        assertEquals(ShopListStock.State.OUT_OF_STOCK, stock.state());
        assertEquals(4, stock.available());
        assertTrue(stock.outOfStock());
    }

    @Test
    void marksOneCompleteBundleAsAvailable() {
        final ShopListStock stock = ShopListStock.resolve(25, false, true, 5, 5);

        assertEquals(ShopListStock.State.AVAILABLE, stock.state());
        assertFalse(stock.outOfStock());
    }

    @Test
    void distinguishesUnloadedDisabledAndAdminShops() {
        assertEquals(
                ShopListStock.State.UNKNOWN,
                ShopListStock.resolve(25, false, false, 0, 5).state());
        assertEquals(
                ShopListStock.State.NOT_SOLD,
                ShopListStock.resolve(0, false, false, 0, 5).state());
        assertEquals(
                ShopListStock.State.UNLIMITED,
                ShopListStock.resolve(25, true, false, 0, 5).state());
    }
}
