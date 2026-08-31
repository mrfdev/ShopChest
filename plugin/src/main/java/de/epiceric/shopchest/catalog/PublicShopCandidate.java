package de.epiceric.shopchest.catalog;

import org.bukkit.Material;

import java.util.Objects;
import java.util.UUID;

/** Immutable authoritative fields needed to decide public catalogue eligibility. */
public record PublicShopCandidate(
        int shopId,
        UUID ownerId,
        Material baseMaterial,
        int bundleAmount,
        double customerBuyPrice,
        PublicShopKind kind,
        boolean storefrontSuspended) {

    public PublicShopCandidate {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(baseMaterial, "baseMaterial");
        Objects.requireNonNull(kind, "kind");
    }
}
