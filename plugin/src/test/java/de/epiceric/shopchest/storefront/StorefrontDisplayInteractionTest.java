package de.epiceric.shopchest.storefront;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StorefrontDisplayInteractionTest {

    @Test
    void sizesHitboxFromTheRenderedPanel() {
        assertEquals(
                2.90F,
                StorefrontDisplayInteraction.hitboxWidth(260, 0.40F),
                0.001F);
        assertEquals(
                0.85F,
                StorefrontDisplayInteraction.hitboxHeight(6, 0.40F),
                0.001F);
    }

    @Test
    void keepsHitboxDimensionsWithinSafeBounds() {
        assertEquals(
                0.75F,
                StorefrontDisplayInteraction.hitboxWidth(0, 0.0F),
                0.001F);
        assertEquals(
                6.0F,
                StorefrontDisplayInteraction.hitboxWidth(10_000, 2.0F),
                0.001F);
        assertEquals(
                0.40F,
                StorefrontDisplayInteraction.hitboxHeight(0, 0.0F),
                0.001F);
        assertEquals(
                2.5F,
                StorefrontDisplayInteraction.hitboxHeight(100, 2.0F),
                0.001F);
    }
}
