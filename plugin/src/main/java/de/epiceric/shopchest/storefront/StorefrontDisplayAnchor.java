package de.epiceric.shopchest.storefront;

import java.util.Objects;
import java.util.UUID;

/** Stable block identity for a Storefront Display anchor. */
public record StorefrontDisplayAnchor(UUID worldId, int blockX, int blockY, int blockZ) {

    public StorefrontDisplayAnchor {
        Objects.requireNonNull(worldId, "worldId");
    }
}
