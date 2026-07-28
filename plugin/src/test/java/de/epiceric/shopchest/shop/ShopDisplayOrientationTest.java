package de.epiceric.shopchest.shop;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShopDisplayOrientationTest {

    private static final Location CENTER = new Location(null, 10.0, 64.0, 10.0);

    @Test
    void parsesEverySupportedCommandValue() {
        assertEquals(ShopDisplayOrientation.RESET, ShopDisplayOrientation.parse("reset"));
        assertEquals(ShopDisplayOrientation.FACE_ME, ShopDisplayOrientation.parse("faceme"));
        assertEquals(ShopDisplayOrientation.FACE_ME, ShopDisplayOrientation.parse("face-me"));
        assertEquals(ShopDisplayOrientation.NORTH, ShopDisplayOrientation.parse("north"));
        assertEquals(ShopDisplayOrientation.SOUTH, ShopDisplayOrientation.parse("south"));
        assertEquals(ShopDisplayOrientation.EAST, ShopDisplayOrientation.parse("east"));
        assertEquals(ShopDisplayOrientation.WEST, ShopDisplayOrientation.parse("west"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ShopDisplayOrientation.parse("up"));
    }

    @Test
    void faceMeSelectsThePlayersDominantSideOfTheContainer() {
        assertEquals(
                BlockFace.SOUTH,
                ShopDisplayOrientation.FACE_ME.resolve(
                        CENTER,
                        new Location(null, 10.5, 64.0, 15.0)));
        assertEquals(
                BlockFace.NORTH,
                ShopDisplayOrientation.FACE_ME.resolve(
                        CENTER,
                        new Location(null, 10.0, 64.0, 5.0)));
        assertEquals(
                BlockFace.EAST,
                ShopDisplayOrientation.FACE_ME.resolve(
                        CENTER,
                        new Location(null, 15.0, 64.0, 10.5)));
        assertEquals(
                BlockFace.WEST,
                ShopDisplayOrientation.FACE_ME.resolve(
                        CENTER,
                        new Location(null, 5.0, 64.0, 10.0)));
    }

    @Test
    void resetRemovesTheOverrideAndCardinalModesStayExact() {
        final Location viewer = new Location(null, 15.0, 64.0, 10.0);

        assertNull(ShopDisplayOrientation.RESET.resolve(CENTER, viewer));
        assertEquals(BlockFace.NORTH, ShopDisplayOrientation.NORTH.resolve(CENTER, viewer));
        assertEquals(BlockFace.SOUTH, ShopDisplayOrientation.SOUTH.resolve(CENTER, viewer));
        assertEquals(BlockFace.EAST, ShopDisplayOrientation.EAST.resolve(CENTER, viewer));
        assertEquals(BlockFace.WEST, ShopDisplayOrientation.WEST.resolve(CENTER, viewer));
    }
}
