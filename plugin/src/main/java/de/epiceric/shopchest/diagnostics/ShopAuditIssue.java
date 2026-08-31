package de.epiceric.shopchest.diagnostics;

/** Known, read-only findings for one persisted shop record. */
public enum ShopAuditIssue {
    WORLD_UNAVAILABLE,
    MISSING_CONTAINER,
    UNSUPPORTED_CONTAINER,
    INCOMPLETE_CONTAINER,
    BLOCKED_DISPLAY,
    INVALID_PRODUCT,
    INVALID_OWNER,
    INVALID_SHOP_TYPE,
    INVALID_TERMS,
    INVALID_LOCATION,
    INVALID_RECORD,
    CONFLICTING_RECORD,
    SHADOWED_RECORD,
    INACTIVE_RECORD
}
