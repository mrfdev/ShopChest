package de.epiceric.shopchest.shop;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopContainerTest {

    @Test
    void supportsOnlyTheIntendedPaper262ShopContainers() {
        Set<Material> supported = ShopContainer.supportedMaterials();

        assertEquals(28, supported.size());
        assertTrue(supported.contains(Material.CHEST));
        assertTrue(supported.contains(Material.TRAPPED_CHEST));
        assertTrue(supported.contains(Material.BARREL));
        assertFalse(supported.contains(Material.ENDER_CHEST));
        assertFalse(supported.contains(Material.HOPPER));
    }

    @Test
    void supportsEveryDyedAndUndyedShulkerBox() {
        Set<Material> shulkerBoxes = Arrays.stream(Material.values())
                .filter(material -> !material.name().startsWith("LEGACY_"))
                .filter(material -> material == Material.SHULKER_BOX || material.name().endsWith("_SHULKER_BOX"))
                .collect(Collectors.toSet());

        assertEquals(17, shulkerBoxes.size());
        assertTrue(ShopContainer.supportedMaterials().containsAll(shulkerBoxes));
    }

    @Test
    void supportsEveryCopperChestAndWaxedOxidationVariant() {
        Set<Material> copperChests = Arrays.stream(Material.values())
                .filter(material -> material.name().endsWith("COPPER_CHEST"))
                .collect(Collectors.toSet());

        assertEquals(8, copperChests.size());
        assertTrue(ShopContainer.supportedMaterials().containsAll(copperChests));
    }

    @Test
    void keepsHorizontalFacingAndStabilizesVerticalContainers() {
        assertEquals(BlockFace.NORTH, ShopContainer.horizontalFacing(BlockFace.NORTH));
        assertEquals(BlockFace.EAST, ShopContainer.horizontalFacing(BlockFace.EAST));
        assertEquals(BlockFace.SOUTH, ShopContainer.horizontalFacing(BlockFace.SOUTH));
        assertEquals(BlockFace.WEST, ShopContainer.horizontalFacing(BlockFace.WEST));
        assertEquals(BlockFace.SOUTH, ShopContainer.horizontalFacing(BlockFace.UP));
        assertEquals(BlockFace.SOUTH, ShopContainer.horizontalFacing(BlockFace.DOWN));
    }

    @Test
    void usesStoredPlayerFacingOnlyWhenTheContainerFacesVertically() {
        assertEquals(BlockFace.WEST, ShopContainer.resolveFacing(BlockFace.UP, BlockFace.WEST));
        assertEquals(BlockFace.NORTH, ShopContainer.resolveFacing(BlockFace.DOWN, BlockFace.NORTH));
        assertEquals(BlockFace.SOUTH, ShopContainer.resolveFacing(BlockFace.UP, null));
        assertEquals(BlockFace.EAST, ShopContainer.resolveFacing(BlockFace.EAST, BlockFace.WEST));
    }

    @Test
    void shopOverrideTakesPriorityOverNativeAndVerticalContainerFacing() {
        assertEquals(
                BlockFace.NORTH,
                ShopContainer.resolveFacing(
                        BlockFace.SOUTH,
                        BlockFace.WEST,
                        BlockFace.NORTH));
        assertEquals(
                BlockFace.EAST,
                ShopContainer.resolveFacing(
                        BlockFace.UP,
                        BlockFace.WEST,
                        BlockFace.EAST));
        assertEquals(
                BlockFace.SOUTH,
                ShopContainer.resolveFacing(
                        BlockFace.SOUTH,
                        BlockFace.WEST,
                        null));
    }
}
