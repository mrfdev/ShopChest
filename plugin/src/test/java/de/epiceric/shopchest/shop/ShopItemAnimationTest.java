package de.epiceric.shopchest.shop;

import org.bukkit.util.Transformation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopItemAnimationTest {

    private static final float SCALE = 0.45F;
    private static final float BOB_AMPLITUDE = 0.06F;
    private static final double BOB_PERIOD_SECONDS = 3.14D;
    private static final double ROTATION_PERIOD_SECONDS = 6.28D;

    @Test
    void scalesAndFloatsTheIconAboveItsAnchor() {
        final Transformation center = animationAt(0L);
        final Transformation high = animationAt(16L);
        final Transformation low = animationAt(47L);

        assertEquals(SCALE, center.getScale().x, 0.0001F);
        assertEquals(SCALE, center.getScale().y, 0.0001F);
        assertEquals(SCALE, center.getScale().z, 0.0001F);
        assertEquals(0.0F, center.getTranslation().y, 0.0001F);
        assertTrue(high.getTranslation().y > 0.0F);
        assertTrue(low.getTranslation().y < 0.0F);
    }

    @Test
    void rotatesAroundTheVerticalAxis() {
        final Transformation start = animationAt(0L);
        final Transformation next = animationAt(ShopItemAnimation.UPDATE_INTERVAL_TICKS);

        assertEquals(0.0F, next.getLeftRotation().x, 0.0001F);
        assertNotEquals(start.getLeftRotation().y, next.getLeftRotation().y);
        assertEquals(0.0F, next.getLeftRotation().z, 0.0001F);
    }

    @Test
    void canDisableBobbingAndRotationWithoutChangingScale() {
        final Transformation transformation = ShopItemAnimation.at(
                123L,
                1.5F,
                0.7F,
                false,
                0.4F,
                1.0D,
                false,
                1.0D);

        assertEquals(0.0F, transformation.getTranslation().y, 0.0001F);
        assertEquals(0.7F, transformation.getScale().x, 0.0001F);
        assertEquals(0.0F, transformation.getLeftRotation().y, 0.0001F);
    }

    @Test
    void givesDifferentIconsDifferentAnimationPhases() {
        final float first = ShopItemAnimation.phaseFor(new UUID(0L, 1L));
        final float second = ShopItemAnimation.phaseFor(new UUID(0L, 2L));

        assertTrue(first >= 0.0F && first < Math.PI * 2.0D);
        assertTrue(second >= 0.0F && second < Math.PI * 2.0D);
        assertNotEquals(first, second);
    }

    private static Transformation animationAt(long elapsedTicks) {
        return ShopItemAnimation.at(
                elapsedTicks,
                0.0F,
                SCALE,
                true,
                BOB_AMPLITUDE,
                BOB_PERIOD_SECONDS,
                true,
                ROTATION_PERIOD_SECONDS);
    }
}
