package de.epiceric.shopchest.shop;

import org.bukkit.util.Transformation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopItemAnimationTest {

    @Test
    void scalesAndFloatsTheIconAboveItsAnchor() {
        final Transformation center = ShopItemAnimation.at(0L, 0.0F);
        final Transformation high = ShopItemAnimation.at(16L, 0.0F);
        final Transformation low = ShopItemAnimation.at(47L, 0.0F);

        assertEquals(ShopItemAnimation.SCALE, center.getScale().x, 0.0001F);
        assertEquals(ShopItemAnimation.SCALE, center.getScale().y, 0.0001F);
        assertEquals(ShopItemAnimation.SCALE, center.getScale().z, 0.0001F);
        assertEquals(0.45F, ShopItemAnimation.SCALE, 0.0001F);
        assertEquals(ShopItemAnimation.BOB_CENTER, center.getTranslation().y, 0.0001F);
        assertTrue(high.getTranslation().y > ShopItemAnimation.BOB_CENTER);
        assertTrue(low.getTranslation().y < ShopItemAnimation.BOB_CENTER);
    }

    @Test
    void rotatesAroundTheVerticalAxis() {
        final Transformation start = ShopItemAnimation.at(0L, 0.0F);
        final Transformation next = ShopItemAnimation.at(
                ShopItemAnimation.UPDATE_INTERVAL_TICKS, 0.0F);

        assertEquals(0.0F, next.getLeftRotation().x, 0.0001F);
        assertNotEquals(start.getLeftRotation().y, next.getLeftRotation().y);
        assertEquals(0.0F, next.getLeftRotation().z, 0.0001F);
    }

    @Test
    void givesDifferentIconsDifferentAnimationPhases() {
        final float first = ShopItemAnimation.phaseFor(new UUID(0L, 1L));
        final float second = ShopItemAnimation.phaseFor(new UUID(0L, 2L));

        assertTrue(first >= 0.0F && first < Math.PI * 2.0D);
        assertTrue(second >= 0.0F && second < Math.PI * 2.0D);
        assertNotEquals(first, second);
    }
}
