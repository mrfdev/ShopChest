package de.epiceric.shopchest.advertising;

/** Exact relationship between affected inventory slots and persisted purchase evidence. */
public enum PurchaseInventoryState {
    BEFORE,
    AFTER,
    DIVERGED
}
