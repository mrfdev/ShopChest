package de.epiceric.shopchest.catalog;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialKeyResolverTest {

    private final MaterialKeyResolver resolver = new MaterialKeyResolver(
            key -> Map.of(
                    NamespacedKey.minecraft("stone_bricks"), Material.STONE_BRICKS,
                    NamespacedKey.minecraft("music_disc_5"), Material.MUSIC_DISC_5)
                    .get(key),
            material -> true);

    @Test
    void resolvesCanonicalMaterialNamesWithSpacesOrUnderscores() {
        assertEquals(
                Material.STONE_BRICKS,
                resolver.resolve("stone bricks").orElseThrow().material());
        assertEquals(
                Material.STONE_BRICKS,
                resolver.resolve("STONE_BRICKS").orElseThrow().material());
    }

    @Test
    void acceptsTheVanillaPrefixAndKeepsNumberedNamesAsMaterials() {
        final ResolvedMaterial material = resolver.resolve("MINECRAFT:music disc 5")
                .orElseThrow();

        assertEquals(Material.MUSIC_DISC_5, material.material());
        assertEquals("minecraft:music_disc_5", material.canonicalKey());
    }

    @Test
    void rejectsFuzzyOrNonVanillaMaterialNames() {
        assertTrue(resolver.resolve("custom:stone_bricks").isEmpty());
        assertTrue(resolver.resolve("stone-bricks").isEmpty());
        assertTrue(resolver.resolve("stone.bricks").isEmpty());
        assertTrue(resolver.resolve("stonebrick").isEmpty());
        assertTrue(resolver.resolve("").isEmpty());
    }

    @Test
    void rejectsBlocksThatCannotExistAsItems() {
        final MaterialKeyResolver nonItemResolver = new MaterialKeyResolver(
                ignored -> Material.WATER,
                material -> material != Material.WATER);

        assertTrue(nonItemResolver.resolve("water").isEmpty());
    }
}
