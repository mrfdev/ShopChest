package de.epiceric.shopchest.shop;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

final class ShopItemAnimation {

    static final int UPDATE_INTERVAL_TICKS = 10;
    static final float SCALE = 0.45F;
    static final float BOB_CENTER = 0.06F;
    static final float BOB_AMPLITUDE = 0.06F;

    private static final double BOB_RADIANS_PER_TICK = 0.10D;
    private static final double ROTATION_RADIANS_PER_TICK = 0.05D;
    private static final double FULL_ROTATION = Math.PI * 2.0D;

    private ShopItemAnimation() {
    }

    static Transformation at(long elapsedTicks, float phase) {
        final double bobAngle = elapsedTicks * BOB_RADIANS_PER_TICK + phase;
        final float bob = BOB_CENTER + (float) Math.sin(bobAngle) * BOB_AMPLITUDE;
        final float rotation = (float) ((elapsedTicks * ROTATION_RADIANS_PER_TICK + phase)
                % FULL_ROTATION);

        return new Transformation(
                new Vector3f(0.0F, bob, 0.0F),
                new Quaternionf().rotateY(rotation),
                new Vector3f(SCALE),
                new Quaternionf());
    }

    static float phaseFor(UUID uuid) {
        final int phaseStep = Math.floorMod(uuid.hashCode(), 3_600);
        return (float) (phaseStep / 3_600.0D * FULL_ROTATION);
    }
}
