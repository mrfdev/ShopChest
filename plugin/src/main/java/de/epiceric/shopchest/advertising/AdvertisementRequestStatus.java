package de.epiceric.shopchest.advertising;

public enum AdvertisementRequestStatus {
    QUEUED(true),
    CANCELLED(false),
    BROADCAST(false);

    private final boolean open;

    AdvertisementRequestStatus(boolean open) {
        this.open = open;
    }

    public boolean isOpen() {
        return open;
    }
}
