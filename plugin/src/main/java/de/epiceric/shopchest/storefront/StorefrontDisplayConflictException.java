package de.epiceric.shopchest.storefront;

/** Raised when the hard owner or Ender Chest uniqueness boundary is crossed. */
public final class StorefrontDisplayConflictException extends RuntimeException {

    private final Kind kind;

    public StorefrontDisplayConflictException(Kind kind) {
        super(kind == Kind.OWNER
                ? "That player already has a Storefront Display"
                : "That Ender Chest already hosts a Storefront Display");
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public enum Kind {
        OWNER,
        LOCATION
    }
}
