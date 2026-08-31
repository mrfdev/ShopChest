package de.epiceric.shopchest.catalog;

import java.util.Objects;

/** Point-in-time container capacity for one exact configured product. */
public record ListingCapacity(
        ListingCapacityState state,
        int matchingItemCapacity,
        int completeBundles
) {
    public ListingCapacity {
        Objects.requireNonNull(state, "state");
        if (matchingItemCapacity < 0 || completeBundles < 0) {
            throw new IllegalArgumentException("Capacity counts cannot be negative");
        }
        if (state == ListingCapacityState.CAN_ACCEPT && completeBundles == 0) {
            throw new IllegalArgumentException("Available capacity needs a complete bundle");
        }
        if (state == ListingCapacityState.FULL && completeBundles != 0) {
            throw new IllegalArgumentException("A full listing cannot accept a complete bundle");
        }
        if ((state == ListingCapacityState.UNCHECKED
                || state == ListingCapacityState.UNAVAILABLE)
                && (matchingItemCapacity != 0 || completeBundles != 0)) {
            throw new IllegalArgumentException("Uninspected capacity cannot expose counts");
        }
    }

    public static ListingCapacity unchecked() {
        return new ListingCapacity(ListingCapacityState.UNCHECKED, 0, 0);
    }

    public static ListingCapacity unavailable() {
        return new ListingCapacity(ListingCapacityState.UNAVAILABLE, 0, 0);
    }
}
