package de.epiceric.shopchest.catalog;

/** Player-safe aggregate counts for one immutable search snapshot. */
public record ShopSearchSummary(
        int inStockShops,
        int inStockStorefronts,
        int outOfStockShops,
        int uncheckedShops) {

    public ShopSearchSummary {
        if (inStockShops < 0
                || inStockStorefronts < 0
                || outOfStockShops < 0
                || uncheckedShops < 0) {
            throw new IllegalArgumentException("Search summary counts cannot be negative");
        }
        if (inStockStorefronts > inStockShops) {
            throw new IllegalArgumentException("Storefront count cannot exceed shop count");
        }
    }
}
