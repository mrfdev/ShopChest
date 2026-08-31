package de.epiceric.shopchest.advertising;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Durable purchase intent and exact escrow payload prepared before inventory mutation. */
public record AdvertisingPassPurchase(
        String nonce,
        UUID ownerId,
        AdvertisingPass pass,
        AdvertisingPurchaseStatus status,
        String escrowPayload,
        Instant createdAt,
        Instant updatedAt,
        String failure
) {

    public AdvertisingPassPurchase {
        Objects.requireNonNull(nonce, "nonce");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(pass, "pass");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(escrowPayload, "escrowPayload");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (nonce.isBlank() || nonce.length() > 128) {
            throw new IllegalArgumentException("Purchase nonce is invalid");
        }
        if (!ownerId.equals(pass.ownerId())) {
            throw new IllegalArgumentException("Purchase owner and pass owner differ");
        }
        if (escrowPayload.isBlank()) {
            throw new IllegalArgumentException("Exact escrow payload is required");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Purchase update precedes creation");
        }
    }

    public static AdvertisingPassPurchase prepared(
            String nonce,
            AdvertisingPass pass,
            String escrowPayload,
            Instant preparedAt
    ) {
        return new AdvertisingPassPurchase(
                nonce,
                pass.ownerId(),
                pass,
                AdvertisingPurchaseStatus.PREPARED,
                escrowPayload,
                preparedAt,
                preparedAt,
                null);
    }
}
