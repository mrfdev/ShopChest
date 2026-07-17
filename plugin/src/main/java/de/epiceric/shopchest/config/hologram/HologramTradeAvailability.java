package de.epiceric.shopchest.config.hologram;

public final class HologramTradeAvailability {

    private HologramTradeAvailability() {
    }

    public static boolean isBuyOutOfStock(
            double buyPrice,
            boolean adminShop,
            int stock,
            int transactionAmount) {
        return buyPrice > 0
                && !adminShop
                && stock < Math.max(1, transactionAmount);
    }

    public static String formatBuyValue(
            String formattedPrice,
            boolean outOfStock,
            String outOfStockText,
            String unavailableColor) {
        return outOfStock ? unavailableColor + outOfStockText : formattedPrice;
    }
}
