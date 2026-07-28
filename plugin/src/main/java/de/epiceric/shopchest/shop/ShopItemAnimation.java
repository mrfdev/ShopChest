package de.epiceric.shopchest.shop;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

final class ShopItemAnimation {

    static final int UPDATE_INTERVAL_TICKS = 10;
    private static final double FULL_ROTATION = Math.PI * 2.0D;
    private static final double TICKS_PER_SECOND = 20.0D;

    private ShopItemAnimation() {
    }

    static Transformation at(
            long elapsedTicks,
            float phase,
            float scale,
            boolean bobbingEnabled,
            float bobAmplitude,
            double bobPeriodSeconds,
            boolean rotationEnabled,
            double rotationPeriodSeconds
    ) {
        final float bob = bobbingEnabled
                ? (float) Math.sin(animationAngle(elapsedTicks, bobPeriodSeconds, phase)) * bobAmplitude
                : 0.0F;
        final float rotation = rotationEnabled
                ? (float) animationAngle(elapsedTicks, rotationPeriodSeconds, phase)
                : 0.0F;

        return new Transformation(
                new Vector3f(0.0F, bob, 0.0F),
                new Quaternionf().rotateY(rotation),
                new Vector3f(scale),
                new Quaternionf());
    }

    private static double animationAngle(long elapsedTicks, double periodSeconds, float phase) {
        final double periodTicks = periodSeconds * TICKS_PER_SECOND;
        return (elapsedTicks / periodTicks * FULL_ROTATION + phase) % FULL_ROTATION;
    }

    static float phaseFor(UUID uuid) {
        final int phaseStep = Math.floorMod(uuid.hashCode(), 3_600);
        return (float) (phaseStep / 3_600.0D * FULL_ROTATION);
    }
}
