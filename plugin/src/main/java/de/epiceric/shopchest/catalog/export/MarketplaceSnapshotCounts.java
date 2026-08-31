package de.epiceric.shopchest.catalog.export;

/** Aggregate row outcomes included in every versioned marketplace artifact. */
public record MarketplaceSnapshotCounts(
        int candidates,
        int published,
        int inStock,
        int outOfStock,
        int unchecked,
        int excludedUnavailable,
        int excludedInvalid
) {
    public MarketplaceSnapshotCounts {
        if (candidates < 0
                || published < 0
                || inStock < 0
                || outOfStock < 0
                || unchecked < 0
                || excludedUnavailable < 0
                || excludedInvalid < 0) {
            throw new IllegalArgumentException("Marketplace snapshot counts cannot be negative");
        }
        if (published != inStock + outOfStock + unchecked) {
            throw new IllegalArgumentException(
                    "Published count must equal the three public availability states");
        }
        if (candidates != published + excludedUnavailable + excludedInvalid) {
            throw new IllegalArgumentException(
                    "Candidate count must equal published and excluded row outcomes");
        }
    }
}
