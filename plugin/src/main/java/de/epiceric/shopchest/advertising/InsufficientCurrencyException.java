package de.epiceric.shopchest.advertising;

/** The inspected inventory did not contain enough exact currency items. */
public final class InsufficientCurrencyException extends IllegalStateException {

    private final int required;
    private final int found;

    public InsufficientCurrencyException(int required, int found) {
        super("Required " + required + " exact currency items but found " + found);
        this.required = required;
        this.found = found;
    }

    public int required() {
        return required;
    }

    public int found() {
        return found;
    }
}
