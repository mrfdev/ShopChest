package de.epiceric.shopchest.catalog;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.UUID;

/** Immutable decoded projection of one persisted shop row used for discovery. */
public record RuntimeCatalogueEntry(
        int shopId,
        UUID ownerId,
        ItemStack productTemplate,
        int bundleAmount,
        double customerBuyPrice,
        double customerSellPrice,
        Location location,
        boolean marketplaceLocation
) {
    public RuntimeCatalogueEntry {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(productTemplate, "productTemplate");
        Objects.requireNonNull(location, "location");
        productTemplate = productTemplate.clone();
        productTemplate.setAmount(1);
        location = location.clone();
    }

    @Override
    public ItemStack productTemplate() {
        return productTemplate.clone();
    }

    @Override
    public Location location() {
        return location.clone();
    }

    public PublicShopCandidate candidate() {
        return new PublicShopCandidate(
                shopId,
                ownerId,
                productTemplate.getType(),
                bundleAmount,
                customerBuyPrice,
                PublicShopKind.NORMAL,
                false);
    }
}
