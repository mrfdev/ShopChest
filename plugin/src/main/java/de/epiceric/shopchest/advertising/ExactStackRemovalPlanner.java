package de.epiceric.shopchest.advertising;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Builds an all-or-nothing removal plan without mutating inventory state. */
public final class ExactStackRemovalPlanner<S> {

    private final StackSemantics<S> semantics;
    private final AdvertisingCurrencyMatcher<S> matcher;

    public ExactStackRemovalPlanner(StackSemantics<S> semantics) {
        this.semantics = Objects.requireNonNull(semantics, "semantics");
        this.matcher = new AdvertisingCurrencyMatcher<>(semantics);
    }

    public StackRemovalPlan<S> plan(
            List<? extends S> currentSlots,
            S authoritativeTemplate,
            int requiredAmount
    ) {
        Objects.requireNonNull(currentSlots, "currentSlots");
        if (requiredAmount <= 0) {
            throw new IllegalArgumentException("requiredAmount must be positive");
        }

        final StackSnapshot<S> before = StackSnapshot.capture(currentSlots, semantics);
        final List<S> afterSlots = new ArrayList<>(before.slots());
        final List<S> removedStacks = new ArrayList<>();
        final List<Integer> affectedSlots = new ArrayList<>();
        int found = 0;

        for (int slot = 0; slot < afterSlots.size() && found < requiredAmount; slot++) {
            final S candidate = afterSlots.get(slot);
            if (!matcher.matches(candidate, authoritativeTemplate)) {
                continue;
            }

            final int available = semantics.amount(candidate);
            if (available <= 0) {
                continue;
            }
            final int removed = Math.min(available, requiredAmount - found);
            removedStacks.add(semantics.withAmount(candidate, removed));
            affectedSlots.add(slot);
            afterSlots.set(slot, removed == available
                    ? null
                    : semantics.withAmount(candidate, available - removed));
            found += removed;
        }

        if (found < requiredAmount) {
            throw new InsufficientCurrencyException(requiredAmount, found);
        }

        return new StackRemovalPlan<>(
                semantics,
                before,
                StackSnapshot.capture(afterSlots, semantics),
                removedStacks,
                affectedSlots);
    }
}
