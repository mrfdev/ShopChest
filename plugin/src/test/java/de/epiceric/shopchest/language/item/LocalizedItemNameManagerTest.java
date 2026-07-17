package de.epiceric.shopchest.language.item;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizedItemNameManagerTest {

    @Test
    void acceptsFullNamespacedAndSimpleOverrideKeys() {
        assertEquals(
                "Full key",
                LocalizedItemNameManager.findOverride(
                        Map.of("block.minecraft.oak_log", "Full key"),
                        "block.minecraft.oak_log",
                        NamespacedKey.minecraft("oak_log"),
                        "OAK_LOG"));
        assertEquals(
                "Namespaced key",
                LocalizedItemNameManager.findOverride(
                        Map.of("minecraft:oak_log", "Namespaced key"),
                        "block.minecraft.oak_log",
                        NamespacedKey.minecraft("oak_log"),
                        "OAK_LOG"));
        assertEquals(
                "Simple key",
                LocalizedItemNameManager.findOverride(
                        Map.of("oak_log", "Simple key"),
                        "block.minecraft.oak_log",
                        NamespacedKey.minecraft("oak_log"),
                        "OAK_LOG"));
    }

    @Test
    void futureTranslationKeysProduceReadableFallbacks() {
        assertEquals(
                "Golden Dandelion",
                LocalizedItemNameManager.getReadableName(
                        Material.DANDELION,
                        "item.minecraft.golden_dandelion"));
        assertEquals(
                "Sulfur",
                LocalizedItemNameManager.getReadableName(
                        Material.GUNPOWDER,
                        "item.minecraft.sulfur"));
    }

    @Test
    void ignoresValuesThatPreviouslyLeakedIntoHolograms() {
        assertFalse(LocalizedItemNameManager.isUsableOverride(""));
        assertFalse(LocalizedItemNameManager.isUsableOverride("ERROR"));
        assertFalse(LocalizedItemNameManager.isUsableOverride("unknown item"));
        assertFalse(LocalizedItemNameManager.isUsableOverride("Not Configured"));
        assertTrue(LocalizedItemNameManager.isUsableOverride("Oak Log"));
    }
}
