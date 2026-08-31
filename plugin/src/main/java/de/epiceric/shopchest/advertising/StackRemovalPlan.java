package de.epiceric.shopchest.advertising;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Complete before/after evidence for an exact currency removal.
 *
 * <p>The plan never mutates a supplied inventory. It returns the complete next
 * snapshot only after all slots match, so callers can perform one compare-before-apply
 * operation on the server thread.</p>
 */
public final class StackRemovalPlan<S> {

    private final StackSemantics<S> semantics;
    private final StackSnapshot<S> before;
    private final StackSnapshot<S> after;
    private final List<S> removedStacks;
    private final List<Integer> affectedSlots;

    StackRemovalPlan(
            StackSemantics<S> semantics,
            StackSnapshot<S> before,
            StackSnapshot<S> after,
            List<? extends S> removedStacks,
            List<Integer> affectedSlots
    ) {
        this.semantics = Objects.requireNonNull(semantics, "semantics");
        this.before = Objects.requireNonNull(before, "before");
        this.after = Objects.requireNonNull(after, "after");
        this.removedStacks = copyStacks(removedStacks);
        this.affectedSlots = List.copyOf(affectedSlots);
    }

    public StackSnapshot<S> before() {
        return before;
    }

    public StackSnapshot<S> after() {
        return after;
    }

    public List<S> removedStacks() {
        return Collections.unmodifiableList(copyStacks(removedStacks));
    }

    public List<Integer> affectedSlots() {
        return affectedSlots;
    }

    public List<S> applyTo(List<? extends S> currentSlots) {
        if (!before.matches(currentSlots)) {
            throw new StaleStackSnapshotException("Inventory changed before currency removal");
        }
        return after.slots();
    }

    public List<S> rollbackFrom(List<? extends S> currentSlots) {
        if (!after.matches(currentSlots)) {
            throw new StaleStackSnapshotException("Inventory changed before currency rollback");
        }
        return before.slots();
    }

    private List<S> copyStacks(List<? extends S> source) {
        final List<S> copies = new ArrayList<>(source.size());
        for (S stack : source) {
            copies.add(semantics.copyOf(stack));
        }
        return copies;
    }
}
