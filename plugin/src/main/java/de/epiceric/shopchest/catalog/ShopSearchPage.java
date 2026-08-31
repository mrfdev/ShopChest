package de.epiceric.shopchest.catalog;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** One four-listing view into a fixed search snapshot. */
public record ShopSearchPage(
        ResolvedMaterial material,
        Instant capturedAt,
        List<PublicShopListing> listings,
        int page,
        int pageCount,
        ShopSearchSummary summary) {

    public ShopSearchPage {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(capturedAt, "capturedAt");
        listings = List.copyOf(Objects.requireNonNull(listings, "listings"));
        Objects.requireNonNull(summary, "summary");
        if (page < 1 || pageCount < 1 || page > pageCount) {
            throw new IllegalArgumentException("Invalid search page bounds");
        }
    }
}
