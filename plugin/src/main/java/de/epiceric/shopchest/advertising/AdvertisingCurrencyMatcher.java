package de.epiceric.shopchest.advertising;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/** Matches a candidate against the complete captured currency item at amount one. */
public final class AdvertisingCurrencyMatcher<S> {

    private final StackSemantics<S> semantics;

    public AdvertisingCurrencyMatcher(StackSemantics<S> semantics) {
        this.semantics = Objects.requireNonNull(semantics, "semantics");
    }

    /** Production matcher backed by {@link ItemStack#isSimilar(ItemStack)}. */
    public static AdvertisingCurrencyMatcher<ItemStack> itemStacks() {
        return new AdvertisingCurrencyMatcher<>(ItemStackStackSemantics.INSTANCE);
    }

    public boolean matches(S candidate, S template) {
        if (candidate == null || template == null
                || semantics.isEmpty(candidate) || semantics.isEmpty(template)) {
            return false;
        }

        final S normalizedCandidate = semantics.withAmount(candidate, 1);
        final S normalizedTemplate = semantics.withAmount(template, 1);
        return semantics.isSimilar(normalizedCandidate, normalizedTemplate);
    }
}
