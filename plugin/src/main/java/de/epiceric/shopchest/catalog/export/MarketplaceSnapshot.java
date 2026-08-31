package de.epiceric.shopchest.catalog.export;

import java.util.List;
import java.util.Objects;

public record MarketplaceSnapshot(
        MarketplaceSnapshotMetadata metadata,
        MarketplaceSnapshotCounts counts,
        List<MarketplaceListing> listings
) {

    public static final int SCHEMA_VERSION = 2;
    public static final String DOCUMENT_TYPE = "shopchest-marketplace-snapshot";

    public MarketplaceSnapshot {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(counts, "counts");
        listings = List.copyOf(listings);
        int inStock = 0;
        int outOfStock = 0;
        int unchecked = 0;
        for (MarketplaceListing listing : listings) {
            switch (listing.availabilityAtCapture()) {
                case IN_STOCK -> inStock++;
                case OUT_OF_STOCK -> outOfStock++;
                case UNCHECKED -> unchecked++;
            }
        }
        if (counts.published() != listings.size()
                || counts.inStock() != inStock
                || counts.outOfStock() != outOfStock
                || counts.unchecked() != unchecked) {
            throw new IllegalArgumentException(
                    "Marketplace snapshot counts must match its published listings");
        }
    }
}
