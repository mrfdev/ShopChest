package de.epiceric.shopchest.advertising;

/** A compare-before-apply or compare-before-rollback check failed. */
public final class StaleStackSnapshotException extends IllegalStateException {

    public StaleStackSnapshotException(String message) {
        super(message);
    }
}
