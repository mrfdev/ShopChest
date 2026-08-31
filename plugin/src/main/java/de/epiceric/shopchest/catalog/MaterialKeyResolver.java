package de.epiceric.shopchest.catalog;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Resolves exact vanilla base-material keys without fuzzy matching. */
public final class MaterialKeyResolver {

    private static final String MINECRAFT_PREFIX = "minecraft:";
    private static final Pattern CANONICAL_PATH = Pattern.compile("[a-z0-9_]+");

    private final Function<NamespacedKey, Material> lookup;
    private final Predicate<Material> itemMaterial;

    public MaterialKeyResolver() {
        this(Registry.MATERIAL::get);
    }

    public MaterialKeyResolver(Function<NamespacedKey, Material> lookup) {
        this(lookup, material -> material.isItem() && !material.isAir());
    }

    public MaterialKeyResolver(
            Function<NamespacedKey, Material> lookup,
            Predicate<Material> itemMaterial) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.itemMaterial = Objects.requireNonNull(itemMaterial, "itemMaterial");
    }

    public Optional<ResolvedMaterial> resolve(String input) {
        if (input == null) {
            return Optional.empty();
        }

        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith(MINECRAFT_PREFIX)) {
            normalized = normalized.substring(MINECRAFT_PREFIX.length());
        }
        normalized = normalized.replaceAll("\\s+", "_");
        if (!CANONICAL_PATH.matcher(normalized).matches()) {
            return Optional.empty();
        }

        final NamespacedKey key = NamespacedKey.minecraft(normalized);
        try {
            final Material material = lookup.apply(key);
            if (material == null || material == Material.AIR || !itemMaterial.test(material)) {
                return Optional.empty();
            }
            return Optional.of(new ResolvedMaterial(material, key.asString()));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }
}
