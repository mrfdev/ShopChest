package de.epiceric.shopchest.nms;

public final class HologramOrientation {

    private HologramOrientation() {
    }

    public static float yawForDirection(int x, int z) {
        if (x == 0 && z == 0) {
            return 0f;
        }
        return (float) Math.toDegrees(Math.atan2(-x, z));
    }
}
