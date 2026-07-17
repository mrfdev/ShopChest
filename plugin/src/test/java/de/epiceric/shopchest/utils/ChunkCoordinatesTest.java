package de.epiceric.shopchest.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkCoordinatesTest {

    @Test
    void mapsPositiveAndNegativeBlockCoordinatesToTheirContainingChunk() {
        assertEquals(0, ChunkCoordinates.fromBlock(0));
        assertEquals(0, ChunkCoordinates.fromBlock(15));
        assertEquals(1, ChunkCoordinates.fromBlock(16));
        assertEquals(-1, ChunkCoordinates.fromBlock(-1));
        assertEquals(-1, ChunkCoordinates.fromBlock(-16));
        assertEquals(-2, ChunkCoordinates.fromBlock(-17));
        assertEquals(-3, ChunkCoordinates.fromBlock(-38));
    }
}
