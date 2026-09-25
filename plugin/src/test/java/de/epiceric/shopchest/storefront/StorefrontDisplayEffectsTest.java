package de.epiceric.shopchest.storefront;

import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorefrontDisplayEffectsTest {

    @Test
    void buildsARestrainedRotatingAndBobbingIconTransformation() {
        final Transformation start = StorefrontDisplayEffects.iconTransformation(
                0L, 0.0F, 0.5F, 0.1F, 2.0D, 2.0D);
        final Transformation quarterCycle = StorefrontDisplayEffects.iconTransformation(
                10L, 0.0F, 0.5F, 0.1F, 2.0D, 2.0D);

        assertEquals(0.0F, start.getTranslation().y, 0.0001F);
        assertEquals(0.1F, quarterCycle.getTranslation().y, 0.0001F);
        assertEquals(new Vector3f(0.5F), quarterCycle.getScale());
        assertNotEquals(start.getLeftRotation(), quarterCycle.getLeftRotation());
    }

    @Test
    void increasesParticleDensityAsTheViewerApproaches() {
        assertEquals(0, StorefrontDisplayEffects.particleCount(64.0D, 8.0D, 6));
        assertEquals(1, StorefrontDisplayEffects.particleCount(49.0D, 8.0D, 6));
        assertEquals(3, StorefrontDisplayEffects.particleCount(16.0D, 8.0D, 6));
        assertEquals(6, StorefrontDisplayEffects.particleCount(0.0D, 8.0D, 6));
        assertEquals(0, StorefrontDisplayEffects.particleCount(0.0D, 8.0D, 0));
    }

    @Test
    void keepsParticleOffsetsInABoundedOrbit() {
        final List<StorefrontDisplayEffects.Offset> start =
                StorefrontDisplayEffects.particleOffsets(0L, 0.0F, 6);
        final List<StorefrontDisplayEffects.Offset> later =
                StorefrontDisplayEffects.particleOffsets(10L, 0.0F, 6);

        assertEquals(6, start.size());
        assertNotEquals(start, later);
        for (StorefrontDisplayEffects.Offset offset : start) {
            assertEquals(0.68D, Math.hypot(offset.x(), offset.z()), 0.0001D);
            assertTrue(offset.y() >= 0.72D);
            assertTrue(offset.y() <= 1.48D);
        }
    }

    @Test
    void boundsRangesAndProducesAStableOwnerPhase() {
        final UUID ownerId = UUID.fromString("7d65302d-840f-4c6b-a1ce-0864072e2af4");

        assertTrue(StorefrontDisplayEffects.within(64.0D, 8.0D));
        assertFalse(StorefrontDisplayEffects.within(64.01D, 8.0D));
        assertFalse(StorefrontDisplayEffects.within(0.0D, 0.0D));
        assertEquals(
                StorefrontDisplayEffects.phaseFor(ownerId),
                StorefrontDisplayEffects.phaseFor(ownerId));
        assertTrue(StorefrontDisplayEffects.phaseFor(ownerId) >= 0.0F);
        assertTrue(StorefrontDisplayEffects.phaseFor(ownerId) < Math.PI * 2.0D);
    }
}
