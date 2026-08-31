package de.epiceric.shopchest.advertising;

import java.util.Objects;

/** Pure fail-closed decision policy for restart and reconnect recovery. */
public final class AdvertisingPurchaseRecoveryPolicy {

    private AdvertisingPurchaseRecoveryPolicy() {
    }

    public static AdvertisingPurchaseRecoveryAction decide(
            AdvertisingPurchaseStatus status,
            PurchaseInventoryState inventoryState
    ) {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(inventoryState, "inventoryState");
        return switch (status) {
            case PREPARED -> switch (inventoryState) {
                case BEFORE -> AdvertisingPurchaseRecoveryAction.MARK_NOT_CHARGED;
                case AFTER -> AdvertisingPurchaseRecoveryAction.RETRY_DELIVERY;
                case DIVERGED -> AdvertisingPurchaseRecoveryAction.WAIT_FOR_EXACT_EVIDENCE;
            };
            case REFUND_PENDING -> switch (inventoryState) {
                case BEFORE -> AdvertisingPurchaseRecoveryAction.MARK_REFUNDED;
                case AFTER -> AdvertisingPurchaseRecoveryAction.RESTORE_REFUND;
                case DIVERGED -> AdvertisingPurchaseRecoveryAction.WAIT_FOR_EXACT_EVIDENCE;
            };
            case DELIVERED, REFUNDED, NOT_CHARGED ->
                    AdvertisingPurchaseRecoveryAction.NONE;
        };
    }
}
