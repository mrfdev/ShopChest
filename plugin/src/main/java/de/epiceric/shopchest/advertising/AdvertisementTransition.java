package de.epiceric.shopchest.advertising;

import java.util.Objects;

/** Pass and request state that must be persisted as one transaction. */
public record AdvertisementTransition(
        AdvertisingPass pass,
        AdvertisementRequest request
) {

    public AdvertisementTransition {
        Objects.requireNonNull(pass, "pass");
        Objects.requireNonNull(request, "request");
        if (!pass.id().equals(request.passId())
                || !pass.ownerId().equals(request.ownerId())) {
            throw new IllegalArgumentException("Pass and request do not belong together");
        }
        final boolean requestIsReserved = request.id().equals(pass.openRequestId());
        if (request.status().isOpen() != requestIsReserved) {
            throw new IllegalArgumentException(
                    "Open request state must match the pass reservation");
        }
    }
}
