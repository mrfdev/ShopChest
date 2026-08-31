package de.epiceric.shopchest.catalog;

import java.util.Objects;

/** A validated exact-material search request. */
public record ShopSearchRequest(ResolvedMaterial material, int requestedPage) {

    public ShopSearchRequest {
        Objects.requireNonNull(material, "material");
        if (requestedPage < 1) {
            throw new IllegalArgumentException("requestedPage must be positive");
        }
    }
}
