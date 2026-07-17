package de.epiceric.shopchest.display;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HologramOrientationTest {

    @Test
    void convertsCardinalChestDirectionsToMinecraftYaw() {
        assertEquals(0f, HologramOrientation.yawForDirection(0, 1));
        assertEquals(90f, HologramOrientation.yawForDirection(-1, 0));
        assertEquals(180f, HologramOrientation.yawForDirection(0, -1));
        assertEquals(-90f, HologramOrientation.yawForDirection(1, 0));
    }
}
