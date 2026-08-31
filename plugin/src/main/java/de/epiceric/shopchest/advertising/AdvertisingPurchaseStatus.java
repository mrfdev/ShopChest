package de.epiceric.shopchest.advertising;

/** Durable purchase state; non-terminal states retain the owner's purchase guard. */
public enum AdvertisingPurchaseStatus {
    PREPARED(false),
    DELIVERED(true),
    REFUND_PENDING(false),
    REFUNDED(true),
    NOT_CHARGED(true);

    private final boolean terminal;

    AdvertisingPurchaseStatus(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
