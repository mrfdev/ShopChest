package de.epiceric.shopchest.catalog;

import java.util.Objects;

/** One public listing together with a point-in-time stock inspection. */
public record RuntimeCatalogueListing(
        RuntimeCatalogueEntry entry,
        ListingStock stock,
        ListingCapacity capacity
) {
    public RuntimeCatalogueListing {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(stock, "stock");
        Objects.requireNonNull(capacity, "capacity");
    }

    public RuntimeCatalogueListing(RuntimeCatalogueEntry entry, ListingStock stock) {
        this(entry, stock, ListingCapacity.unavailable());
    }
}
