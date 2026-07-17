package de.epiceric.shopchest.sql;

import de.epiceric.shopchest.event.ShopBuySellEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecentTransactionTest {

    private static final UUID PLAYER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID VENDOR = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Test
    void buyCostsTheBuyerAndEarnsTheNormalShopVendor() {
        final RecentTransaction transaction = transaction(ShopBuySellEvent.Type.BUY, false, 25);

        assertEquals(RecentTransaction.Perspective.PLAYER_BOUGHT, transaction.perspective(PLAYER));
        assertEquals(-25, transaction.moneyDelta(PLAYER));
        assertEquals("Vendor", transaction.counterparty(PLAYER));
        assertEquals(5, transaction.unitPrice());
        assertEquals(RecentTransaction.Perspective.SHOP_SOLD, transaction.perspective(VENDOR));
        assertEquals(25, transaction.moneyDelta(VENDOR));
        assertEquals("Buyer", transaction.counterparty(VENDOR));
    }

    @Test
    void sellEarnsTheSellerAndCostsTheNormalShopVendor() {
        final RecentTransaction transaction = transaction(ShopBuySellEvent.Type.SELL, false, 12.5);

        assertEquals(RecentTransaction.Perspective.PLAYER_SOLD, transaction.perspective(PLAYER));
        assertEquals(12.5, transaction.moneyDelta(PLAYER));
        assertEquals(RecentTransaction.Perspective.SHOP_BOUGHT, transaction.perspective(VENDOR));
        assertEquals(-12.5, transaction.moneyDelta(VENDOR));
    }

    @Test
    void adminShopCreatorDoesNotReceiveFictionalMoneyMovement() {
        final RecentTransaction transaction = transaction(ShopBuySellEvent.Type.BUY, true, 50);

        assertEquals(RecentTransaction.Perspective.PLAYER_BOUGHT, transaction.perspective(PLAYER));
        assertEquals(-50, transaction.moneyDelta(PLAYER));
        assertEquals(RecentTransaction.Perspective.UNRELATED, transaction.perspective(VENDOR));
        assertEquals(0, transaction.moneyDelta(VENDOR));
    }

    private static RecentTransaction transaction(
            ShopBuySellEvent.Type type,
            boolean adminShop,
            double price
    ) {
        return new RecentTransaction(
                1,
                42,
                "2026-07-17 09:00:00",
                1,
                "Buyer",
                PLAYER.toString(),
                "Oak Log",
                5,
                "Vendor",
                VENDOR.toString(),
                adminShop,
                "spawn",
                10,
                64,
                -20,
                price,
                type);
    }
}
