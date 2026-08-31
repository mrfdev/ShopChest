package de.epiceric.shopchest.catalog;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

/** Revalidates one captured page without changing its ordering or pagination. */
public final class ShopSearchPageReconciler {

    private ShopSearchPageReconciler() {
    }

    public static ReconciledSearchPage reconcile(
            ShopSearchPage captured,
            Map<Integer, ListingStock> currentStockByShopId
    ) {
        Objects.requireNonNull(captured, "captured");
        Objects.requireNonNull(currentStockByShopId, "currentStockByShopId");

        final var currentListings = new ArrayList<PublicShopListing>(
                captured.listings().size());
        int changed = 0;
        for (PublicShopListing listing : captured.listings()) {
            final ListingStock current = currentStockByShopId.get(
                    listing.candidate().shopId());
            if (current == null || current.availability() != ListingAvailability.IN_STOCK) {
                changed++;
                continue;
            }
            currentListings.add(new PublicShopListing(
                    listing.candidate(),
                    listing.productTemplate(),
                    current));
        }
        return new ReconciledSearchPage(currentListings, changed);
    }
}
