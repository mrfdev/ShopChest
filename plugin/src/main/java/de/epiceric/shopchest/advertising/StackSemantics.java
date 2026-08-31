package de.epiceric.shopchest.advertising;

/**
 * Operations needed to inspect and defensively copy inventory stacks.
 *
 * @param <S> stack representation
 */
public interface StackSemantics<S> {

    boolean isEmpty(S stack);

    int amount(S stack);

    S copyOf(S stack);

    S withAmount(S stack, int amount);

    /** Compares complete identity after the caller has normalized stack amounts. */
    boolean isSimilar(S candidate, S template);

    /** Compares the complete stack, including amount. */
    boolean exactlyEquals(S left, S right);
}
