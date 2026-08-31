package de.epiceric.shopchest.advertising;

/** A prepared, possibly charged purchase can definitively no longer receive its pass. */
public final class AdvertisingPurchaseDeliveryRejectedException extends IllegalStateException {

    public AdvertisingPurchaseDeliveryRejectedException(String message) {
        super(message);
    }
}
