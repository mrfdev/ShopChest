package de.epiceric.shopchest.catalog;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/** Immutable public presentation data for one physical shop. */
public record PublicShopListing(
        PublicShopCandidate candidate,
        ItemStack productTemplate,
        ListingStock stock) {

    public PublicShopListing {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(productTemplate, "productTemplate");
        Objects.requireNonNull(stock, "stock");
        if (productTemplate.getType() != candidate.baseMaterial()) {
            throw new IllegalArgumentException("Product material must match its candidate");
        }
        productTemplate = productTemplate.clone();
        productTemplate.setAmount(1);
    }

    @Override
    public ItemStack productTemplate() {
        return productTemplate.clone();
    }
}
