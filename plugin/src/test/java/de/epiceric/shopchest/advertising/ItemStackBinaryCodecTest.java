package de.epiceric.shopchest.advertising;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemStackBinaryCodecTest {

    @Test
    void rawPaperBytesRoundTripThroughTheVersionedEnvelope() {
        final byte[] serialized = new byte[]{0, 1, 2, 3, 42, -1, 0, 99};

        final String payload = ItemStackBinaryCodec.encodeRawBytes(serialized);

        assertArrayEquals(serialized, ItemStackBinaryCodec.decodeRawBytes(payload));
    }

    @Test
    void corruptAndUnsupportedPayloadsFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> ItemStackBinaryCodec.decodeRawBytes("not-a-versioned-item"));
        assertThrows(IllegalArgumentException.class,
                () -> ItemStackBinaryCodec.decodeRawBytes("paper-item-v1:not base64!"));
        assertThrows(IllegalArgumentException.class,
                () -> ItemStackBinaryCodec.decodeRawBytes("paper-item-v2:AAAA"));
        assertThrows(IllegalArgumentException.class,
                () -> ItemStackBinaryCodec.decodeRawBytes("paper-item-v1:"));
    }

    @Test
    void oversizedPayloadFailsBeforeDeserialization() {
        final byte[] oversized = new byte[ItemStackBinaryCodec.MAX_RAW_BYTES + 1];
        final String payload = "paper-item-v1:"
                + Base64.getEncoder().encodeToString(oversized);

        assertThrows(IllegalArgumentException.class,
                () -> ItemStackBinaryCodec.decodeRawBytes(payload));
    }
}
