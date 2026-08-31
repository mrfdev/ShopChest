package de.epiceric.shopchest.advertising;

import java.util.Objects;

/** A lifecycle transition was rejected by an Advertising Pass invariant. */
public final class AdvertisingPolicyException extends IllegalStateException {

    public enum Reason {
        PASS_NOT_ACTIVE,
        NO_ALLOWANCE,
        OPEN_REQUEST_EXISTS,
        REQUEST_NOT_OPEN,
        REQUEST_MISMATCH,
        OWNER_COOLDOWN
    }

    private final Reason reason;

    public AdvertisingPolicyException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }
}
