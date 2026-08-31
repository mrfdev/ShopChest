package de.epiceric.shopchest.catalog;

import java.util.Objects;

/** Immutable stock evidence associated with a Listing Availability result. */
public record ListingStock(
        ListingAvailability availability,
        int matchingItems,
        int completeBundles) {

    public ListingStock {
        Objects.requireNonNull(availability, "availability");
        if (matchingItems < 0 || completeBundles < 0) {
            throw new IllegalArgumentException("Stock counts cannot be negative");
        }
        if (availability == ListingAvailability.IN_STOCK && completeBundles == 0) {
            throw new IllegalArgumentException("In-stock listings need a complete bundle");
        }
        if (availability == ListingAvailability.OUT_OF_STOCK && completeBundles != 0) {
            throw new IllegalArgumentException("Out-of-stock listings cannot have a complete bundle");
        }
        if ((availability == ListingAvailability.UNCHECKED
                || availability == ListingAvailability.UNAVAILABLE)
                && (matchingItems != 0 || completeBundles != 0)) {
            throw new IllegalArgumentException("Uninspected listings cannot expose stock counts");
        }
    }

    public static ListingStock unchecked() {
        return new ListingStock(ListingAvailability.UNCHECKED, 0, 0);
    }

    public static ListingStock unavailable() {
        return new ListingStock(ListingAvailability.UNAVAILABLE, 0, 0);
    }
}
