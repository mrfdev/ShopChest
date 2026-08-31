package de.epiceric.shopchest.catalog;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialSuggestionIndexTest {

    @Test
    void suggestsOnlyMaterialsActuallySoldByPublicCustomerBuyOffers() {
        final MaterialSuggestionIndex index = MaterialSuggestionIndex.fromMaterials(List.of(
                Material.STONE_BRICKS,
                Material.STONE_BRICK_STAIRS,
                Material.DIRT));

        assertEquals(
                List.of("minecraft:stone_bricks"),
                index.suggest("stone briks", 3).stream()
                        .map(ResolvedMaterial::canonicalKey)
                        .toList());
        assertTrue(index.suggest("diamond", 3).isEmpty());
    }

    @Test
    void deduplicatesAndHardLimitsClickableSuggestions() {
        final MaterialSuggestionIndex index = MaterialSuggestionIndex.fromMaterials(List.of(
                Material.STONE,
                Material.STONE,
                Material.STONE_BRICKS,
                Material.STONE_BRICK_SLAB,
                Material.STONE_BRICK_STAIRS,
                Material.STONE_BRICK_WALL));

        assertEquals(3, index.suggest("stone", 3).size());
        assertEquals(
                List.of("minecraft:stone", "minecraft:stone_bricks", "minecraft:stone_brick_slab"),
                index.suggest("stone", 3).stream()
                        .map(ResolvedMaterial::canonicalKey)
                        .toList());
        assertTrue(index.suggest("s".repeat(65), 3).isEmpty());
    }
}
