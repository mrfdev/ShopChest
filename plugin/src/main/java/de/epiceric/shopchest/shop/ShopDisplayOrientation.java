package de.epiceric.shopchest.shop;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;

import java.util.Locale;

/**
 * A display-orientation change requested through {@code /shops edit}.
 */
public enum ShopDisplayOrientation {
    RESET,
    FACE_ME,
    NORTH,
    SOUTH,
    EAST,
    WEST;

    public static ShopDisplayOrientation parse(String value) {
        final String normalized = value.toLowerCase(Locale.ROOT)
                .replace("-", "")
                .replace("_", "");
        return switch (normalized) {
            case "reset" -> RESET;
            case "faceme" -> FACE_ME;
            case "north" -> NORTH;
            case "south" -> SOUTH;
            case "east" -> EAST;
            case "west" -> WEST;
            default -> throw new IllegalArgumentException("Unknown display orientation: " + value);
        };
    }

    public BlockFace resolve(Location containerCenter, Location viewerLocation) {
        return switch (this) {
            case RESET -> null;
            case FACE_ME -> faceToward(
                    viewerLocation.getX() - containerCenter.getX(),
                    viewerLocation.getZ() - containerCenter.getZ());
            case NORTH -> BlockFace.NORTH;
            case SOUTH -> BlockFace.SOUTH;
            case EAST -> BlockFace.EAST;
            case WEST -> BlockFace.WEST;
        };
    }

    static BlockFace faceToward(double deltaX, double deltaZ) {
        if (Math.abs(deltaX) > Math.abs(deltaZ)) {
            return deltaX >= 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return deltaZ >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }
}
