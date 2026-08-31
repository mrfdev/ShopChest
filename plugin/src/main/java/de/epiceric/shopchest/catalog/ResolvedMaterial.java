package de.epiceric.shopchest.catalog;

import org.bukkit.Material;

import java.util.Objects;

/** An exact runtime material resolution and its canonical one-token key. */
public record ResolvedMaterial(Material material, String canonicalKey) {

    public ResolvedMaterial {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(canonicalKey, "canonicalKey");
    }
}
