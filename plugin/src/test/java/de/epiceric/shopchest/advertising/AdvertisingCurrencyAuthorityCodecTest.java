package de.epiceric.shopchest.advertising;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdvertisingCurrencyAuthorityCodecTest {

    @Test
    void completeItemBytesAndCaptureAuditDataRoundTrip() {
        final byte[] serializedItem = new byte[]{10, 0, 7, 88, -4, 12};
        final UUID administrator = UUID.fromString(
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        final Instant capturedAt = Instant.parse("2026-08-31T10:12:00Z");

        final String encoded = AdvertisingCurrencyAuthorityCodec.encode(
                serializedItem, administrator, capturedAt);
        final AdvertisingCurrencyAuthorityCodec.Decoded decoded =
                AdvertisingCurrencyAuthorityCodec.decode(encoded);

        assertArrayEquals(serializedItem, decoded.serializedItem());
        assertEquals(administrator, decoded.capturedBy());
        assertEquals(capturedAt, decoded.capturedAt());
    }

    @Test
    void corruptOrUnsupportedAuthorityFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> AdvertisingCurrencyAuthorityCodec.decode("format-version: 1\n"));
        assertThrows(IllegalArgumentException.class,
                () -> AdvertisingCurrencyAuthorityCodec.decode(
                        "format-version: 99\nitem-payload: paper-item-v1:AAAA\n"
                                + "captured-by: aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\n"
                                + "captured-at: 2026-08-31T10:12:00Z\n"));
        assertThrows(IllegalArgumentException.class,
                () -> AdvertisingCurrencyAuthorityCodec.decode(
                        "format-version: 1\nitem-payload: 'paper-item-v1:not base64!'\n"
                                + "captured-by: aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\n"
                                + "captured-at: 2026-08-31T10:12:00Z\n"));
    }
}
