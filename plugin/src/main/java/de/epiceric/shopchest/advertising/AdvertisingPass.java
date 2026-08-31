package de.epiceric.shopchest.advertising;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable state of one non-overlapping Advertising Pass. */
public record AdvertisingPass(
        UUID id,
        UUID ownerId,
        Instant startsAt,
        Instant expiresAt,
        int broadcastLimit,
        int broadcastsUsed,
        Duration ownerCooldown,
        Instant lastBroadcastAt,
        UUID openRequestId
) {

    public AdvertisingPass {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(ownerCooldown, "ownerCooldown");
        if (!expiresAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("expiresAt must be after startsAt");
        }
        if (broadcastLimit <= 0) {
            throw new IllegalArgumentException("broadcastLimit must be positive");
        }
        if (broadcastsUsed < 0 || broadcastsUsed > broadcastLimit) {
            throw new IllegalArgumentException("broadcastsUsed is outside the pass allowance");
        }
        if (ownerCooldown.isNegative()) {
            throw new IllegalArgumentException("ownerCooldown cannot be negative");
        }
        if (lastBroadcastAt != null && lastBroadcastAt.isBefore(startsAt)) {
            throw new IllegalArgumentException("lastBroadcastAt cannot precede the pass");
        }
        if (openRequestId != null && broadcastsUsed >= broadcastLimit) {
            throw new IllegalArgumentException("A spent pass cannot retain an open request");
        }
    }

    public boolean isActiveAt(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return !instant.isBefore(startsAt) && instant.isBefore(expiresAt);
    }

    public int unreservedBroadcasts() {
        return broadcastLimit - broadcastsUsed - (openRequestId == null ? 0 : 1);
    }
}
