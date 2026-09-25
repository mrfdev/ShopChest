package de.epiceric.shopchest.storefront;

import java.util.Objects;
import java.util.UUID;

/** Persisted owner and Ender Chest anchor for one Storefront Display. */
public record StorefrontDisplay(
        UUID ownerId,
        UUID worldId,
        String worldName,
        int blockX,
        int blockY,
        int blockZ,
        long createdAt
) {

    public StorefrontDisplay {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(worldId, "worldId");
        worldName = Objects.requireNonNull(worldName, "worldName").strip();
        if (worldName.isEmpty() || worldName.length() > 255) {
            throw new IllegalArgumentException("Storefront display world name is invalid");
        }
        if (worldName.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Storefront display world name contains control characters");
        }
        if (createdAt < 0L) {
            throw new IllegalArgumentException("Storefront display creation time cannot be negative");
        }
    }

    public StorefrontDisplayAnchor anchor() {
        return new StorefrontDisplayAnchor(worldId, blockX, blockY, blockZ);
    }
}
