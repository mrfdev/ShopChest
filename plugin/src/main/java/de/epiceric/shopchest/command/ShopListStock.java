package de.epiceric.shopchest.command;

import de.epiceric.shopchest.config.hologram.HologramTradeAvailability;

record ShopListStock(State state, int available) {

    static ShopListStock resolve(
            double buyPrice,
            boolean adminShop,
            boolean stockKnown,
            int stock,
            int transactionAmount
    ) {
        if (adminShop) {
            return new ShopListStock(State.UNLIMITED, 0);
        }
        if (buyPrice <= 0) {
            return new ShopListStock(State.NOT_SOLD, 0);
        }
        if (!stockKnown) {
            return new ShopListStock(State.UNKNOWN, 0);
        }
        final int available = Math.max(0, stock);
        final boolean outOfStock = HologramTradeAvailability.isBuyOutOfStock(
                buyPrice, false, available, transactionAmount);
        return new ShopListStock(
                outOfStock ? State.OUT_OF_STOCK : State.AVAILABLE,
                available);
    }

    boolean outOfStock() {
        return state == State.OUT_OF_STOCK;
    }

    enum State {
        AVAILABLE,
        OUT_OF_STOCK,
        UNKNOWN,
        UNLIMITED,
        NOT_SOLD
    }
}
