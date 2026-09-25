package de.epiceric.shopchest.storefront;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Pure animation and proximity calculations for Storefront Display effects. */
final class StorefrontDisplayEffects {

    static final int UPDATE_INTERVAL_TICKS = 10;
    static final int MAX_PARTICLE_DISPLAYS_PER_VIEWER = 6;

    private static final double FULL_ROTATION = Math.PI * 2.0D;
    private static final double TICKS_PER_SECOND = 20.0D;
    private static final double PARTICLE_ORBIT_PERIOD_SECONDS = 5.5D;
    private static final double PARTICLE_ORBIT_RADIUS = 0.68D;
    private static final double PARTICLE_CENTER_HEIGHT = 1.10D;
    private static final double PARTICLE_VERTICAL_TRAVEL = 0.38D;

    private StorefrontDisplayEffects() {
    }

    static Transformation iconTransformation(
            long elapsedTicks,
            float phase,
            float scale,
            float bobAmplitude,
            double bobPeriodSeconds,
            double rotationPeriodSeconds
    ) {
        final float bob = (float) Math.sin(
                animationAngle(elapsedTicks, bobPeriodSeconds, phase)) * bobAmplitude;
        final float rotation = (float) animationAngle(
                elapsedTicks, rotationPeriodSeconds, phase);
        return new Transformation(
                new Vector3f(0.0F, bob, 0.0F),
                new Quaternionf().rotateY(rotation),
                new Vector3f(scale),
                new Quaternionf());
    }

    static int particleCount(double distanceSquared, double radius, int maximumCount) {
        if (maximumCount <= 0 || radius <= 0.0D || distanceSquared < 0.0D) {
            return 0;
        }
        final double radiusSquared = radius * radius;
        if (distanceSquared >= radiusSquared) {
            return 0;
        }
        final double distance = Math.sqrt(distanceSquared);
        final double proximity = 1.0D - distance / radius;
        return Math.max(1, Math.min(maximumCount,
                (int) Math.ceil(maximumCount * proximity)));
    }

    static List<Offset> particleOffsets(
            long elapsedTicks,
            float phase,
            int count
    ) {
        if (count <= 0) {
            return List.of();
        }
        final double baseAngle = animationAngle(
                elapsedTicks, PARTICLE_ORBIT_PERIOD_SECONDS, phase);
        final List<Offset> offsets = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            final double angle = baseAngle + FULL_ROTATION * index / count;
            offsets.add(new Offset(
                    Math.cos(angle) * PARTICLE_ORBIT_RADIUS,
                    PARTICLE_CENTER_HEIGHT
                            + Math.sin(angle * 2.0D) * PARTICLE_VERTICAL_TRAVEL,
                    Math.sin(angle) * PARTICLE_ORBIT_RADIUS));
        }
        return List.copyOf(offsets);
    }

    static boolean within(double distanceSquared, double radius) {
        return distanceSquared >= 0.0D && radius > 0.0D
                && distanceSquared <= radius * radius;
    }

    static float phaseFor(UUID ownerId) {
        final int phaseStep = Math.floorMod(ownerId.hashCode(), 3_600);
        return (float) (phaseStep / 3_600.0D * FULL_ROTATION);
    }

    private static double animationAngle(long elapsedTicks, double periodSeconds, float phase) {
        final double periodTicks = periodSeconds * TICKS_PER_SECOND;
        return (elapsedTicks / periodTicks * FULL_ROTATION + phase) % FULL_ROTATION;
    }

    record Offset(double x, double y, double z) {
    }
}
