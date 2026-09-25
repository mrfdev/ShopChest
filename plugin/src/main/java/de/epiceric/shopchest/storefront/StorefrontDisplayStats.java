package de.epiceric.shopchest.storefront;

import de.epiceric.shopchest.catalog.RuntimeCatalogueEntry;

import java.util.List;

/** Small live catalogue summary rendered on a Storefront Display. */
public record StorefrontDisplayStats(int shops, int selling, int buying) {

    public StorefrontDisplayStats {
        if (shops < 0 || selling < 0 || buying < 0
                || selling > shops || buying > shops) {
            throw new IllegalArgumentException("Storefront display statistics are inconsistent");
        }
    }

    public static StorefrontDisplayStats from(List<RuntimeCatalogueEntry> entries) {
        final List<RuntimeCatalogueEntry> safeEntries = entries == null ? List.of() : entries;
        return new StorefrontDisplayStats(
                safeEntries.size(),
                (int) safeEntries.stream()
                        .filter(entry -> entry.customerBuyPrice() > 0.0D
                                && Double.isFinite(entry.customerBuyPrice()))
                        .count(),
                (int) safeEntries.stream()
                        .filter(entry -> entry.customerSellPrice() > 0.0D
                                && Double.isFinite(entry.customerSellPrice()))
                        .count());
    }
}
