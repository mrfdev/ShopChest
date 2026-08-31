package de.epiceric.shopchest.advertising;

import java.time.Duration;
import java.util.Objects;

/** Terms snapshotted when an Advertising Pass is issued. */
public record AdvertisingPassTerms(
        Duration duration,
        int broadcastLimit,
        Duration ownerCooldown
) {

    public static final AdvertisingPassTerms STANDARD =
            new AdvertisingPassTerms(Duration.ofDays(7), 3, Duration.ofHours(24));

    public AdvertisingPassTerms {
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(ownerCooldown, "ownerCooldown");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("duration must be positive");
        }
        if (broadcastLimit <= 0) {
            throw new IllegalArgumentException("broadcastLimit must be positive");
        }
        if (ownerCooldown.isNegative()) {
            throw new IllegalArgumentException("ownerCooldown cannot be negative");
        }
    }
}
