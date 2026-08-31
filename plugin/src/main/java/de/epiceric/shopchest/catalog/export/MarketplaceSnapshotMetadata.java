package de.epiceric.shopchest.catalog.export;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

public record MarketplaceSnapshotMetadata(
        Instant capturedAt,
        ZoneId displayZone,
        String sourceVersion,
        String banner,
        String marketplaceLabel
) {

    public MarketplaceSnapshotMetadata {
        Objects.requireNonNull(capturedAt, "capturedAt");
        Objects.requireNonNull(displayZone, "displayZone");
        requireNonBlank(sourceVersion, "sourceVersion");
        requireNonBlank(banner, "banner");
        requireNonBlank(marketplaceLabel, "marketplaceLabel");
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
