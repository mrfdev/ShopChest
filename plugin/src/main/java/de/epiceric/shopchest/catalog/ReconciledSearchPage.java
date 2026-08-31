package de.epiceric.shopchest.catalog;

import java.util.List;

/** Current rows for one page while retaining the immutable captured ordering. */
public record ReconciledSearchPage(
        List<PublicShopListing> listings,
        int changedRows
) {
    public ReconciledSearchPage {
        listings = List.copyOf(listings);
        if (changedRows < 0) {
            throw new IllegalArgumentException("changedRows cannot be negative");
        }
    }
}
