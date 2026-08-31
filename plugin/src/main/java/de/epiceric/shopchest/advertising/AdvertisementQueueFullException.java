package de.epiceric.shopchest.advertising;

/** Durable queue admission rejected because the configured global capacity is exhausted. */
public final class AdvertisementQueueFullException extends IllegalStateException {

    public AdvertisementQueueFullException(String message) {
        super(message);
    }
}
