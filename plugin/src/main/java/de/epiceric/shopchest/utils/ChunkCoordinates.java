package de.epiceric.shopchest.utils;

public final class ChunkCoordinates {

    private ChunkCoordinates() {
    }

    public static int fromBlock(int blockCoordinate) {
        return Math.floorDiv(blockCoordinate, 16);
    }
}
