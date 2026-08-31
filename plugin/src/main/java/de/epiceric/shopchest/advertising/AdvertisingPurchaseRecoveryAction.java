package de.epiceric.shopchest.advertising;

public enum AdvertisingPurchaseRecoveryAction {
    NONE,
    MARK_NOT_CHARGED,
    RETRY_DELIVERY,
    RESTORE_REFUND,
    MARK_REFUNDED,
    WAIT_FOR_EXACT_EVIDENCE
}
