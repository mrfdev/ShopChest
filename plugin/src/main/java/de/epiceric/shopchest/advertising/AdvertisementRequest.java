package de.epiceric.shopchest.advertising;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Durable request state without duplicating storefront or listing data. */
public record AdvertisementRequest(
        UUID id,
        UUID ownerId,
        UUID passId,
        AdvertisementRequestStatus status,
        Instant submittedAt,
        Instant eligibleAt,
        Instant closedAt
) {

    public AdvertisementRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(passId, "passId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(submittedAt, "submittedAt");
        Objects.requireNonNull(eligibleAt, "eligibleAt");
        if (eligibleAt.isBefore(submittedAt)) {
            throw new IllegalArgumentException("eligibleAt cannot precede submission");
        }
        if (status.isOpen() != (closedAt == null)) {
            throw new IllegalArgumentException("Only open requests omit closedAt");
        }
        if (closedAt != null && closedAt.isBefore(submittedAt)) {
            throw new IllegalArgumentException("closedAt cannot precede submission");
        }
    }
}
