package de.epiceric.shopchest.advertising;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Exact before/after and removed-stack evidence for the slots changed by a purchase.
 * Unaffected slots may change without making a safe charge or refund impossible.
 */
public final class PurchaseEscrowEvidence<S> {

    private final StackSemantics<S> semantics;
    private final int slotCount;
    private final List<Integer> affectedSlots;
    private final List<S> beforeStacks;
    private final List<S> afterStacks;
    private final List<S> removedStacks;

    public PurchaseEscrowEvidence(
            StackSemantics<S> semantics,
            int slotCount,
            List<Integer> affectedSlots,
            List<? extends S> beforeStacks,
            List<? extends S> afterStacks,
            List<? extends S> removedStacks
    ) {
        this.semantics = Objects.requireNonNull(semantics, "semantics");
        if (slotCount <= 0) {
            throw new IllegalArgumentException("slotCount must be positive");
        }
        Objects.requireNonNull(affectedSlots, "affectedSlots");
        if (affectedSlots.isEmpty()
                || affectedSlots.size() != beforeStacks.size()
                || affectedSlots.size() != afterStacks.size()) {
            throw new IllegalArgumentException("Affected slot evidence is incomplete");
        }
        final Set<Integer> unique = new HashSet<>();
        for (Integer slot : affectedSlots) {
            if (slot == null || slot < 0 || slot >= slotCount || !unique.add(slot)) {
                throw new IllegalArgumentException("Affected slot index is invalid or duplicated");
            }
        }
        if (removedStacks == null || removedStacks.isEmpty()) {
            throw new IllegalArgumentException("Removed-stack escrow cannot be empty");
        }
        this.slotCount = slotCount;
        this.affectedSlots = List.copyOf(affectedSlots);
        this.beforeStacks = copyStacks(beforeStacks);
        this.afterStacks = copyStacks(afterStacks);
        this.removedStacks = copyStacks(removedStacks);
    }

    public static <S> PurchaseEscrowEvidence<S> fromPlan(
            StackRemovalPlan<S> plan,
            StackSemantics<S> semantics
    ) {
        Objects.requireNonNull(plan, "plan");
        final List<S> before = plan.before().slots();
        final List<S> after = plan.after().slots();
        final List<S> affectedBefore = new ArrayList<>();
        final List<S> affectedAfter = new ArrayList<>();
        for (int slot : plan.affectedSlots()) {
            affectedBefore.add(before.get(slot));
            affectedAfter.add(after.get(slot));
        }
        return new PurchaseEscrowEvidence<>(
                semantics,
                before.size(),
                plan.affectedSlots(),
                affectedBefore,
                affectedAfter,
                plan.removedStacks());
    }

    public int slotCount() {
        return slotCount;
    }

    public List<Integer> affectedSlots() {
        return affectedSlots;
    }

    public List<S> beforeStacks() {
        return Collections.unmodifiableList(copyStacks(beforeStacks));
    }

    public List<S> afterStacks() {
        return Collections.unmodifiableList(copyStacks(afterStacks));
    }

    public List<S> removedStacks() {
        return Collections.unmodifiableList(copyStacks(removedStacks));
    }

    public PurchaseInventoryState classify(List<? extends S> currentSlots) {
        if (currentSlots == null || currentSlots.size() != slotCount) {
            return PurchaseInventoryState.DIVERGED;
        }
        if (matches(currentSlots, beforeStacks)) {
            return PurchaseInventoryState.BEFORE;
        }
        if (matches(currentSlots, afterStacks)) {
            return PurchaseInventoryState.AFTER;
        }
        return PurchaseInventoryState.DIVERGED;
    }

    public List<S> applyCharge(List<? extends S> currentSlots) {
        if (classify(currentSlots) != PurchaseInventoryState.BEFORE) {
            throw new StaleStackSnapshotException(
                    "Affected inventory slots changed before purchase charge");
        }
        return replaceAffected(currentSlots, afterStacks);
    }

    public List<S> restoreRefund(List<? extends S> currentSlots) {
        if (classify(currentSlots) != PurchaseInventoryState.AFTER) {
            throw new StaleStackSnapshotException(
                    "Affected inventory slots changed before purchase refund");
        }
        return replaceAffected(currentSlots, beforeStacks);
    }

    private boolean matches(List<? extends S> currentSlots, List<S> expected) {
        for (int index = 0; index < affectedSlots.size(); index++) {
            if (!semantics.exactlyEquals(
                    currentSlots.get(affectedSlots.get(index)), expected.get(index))) {
                return false;
            }
        }
        return true;
    }

    private List<S> replaceAffected(List<? extends S> currentSlots, List<S> replacements) {
        final List<S> result = copyStacks(currentSlots);
        for (int index = 0; index < affectedSlots.size(); index++) {
            result.set(affectedSlots.get(index), semantics.copyOf(replacements.get(index)));
        }
        return Collections.unmodifiableList(result);
    }

    private List<S> copyStacks(List<? extends S> source) {
        Objects.requireNonNull(source, "source");
        final List<S> copies = new ArrayList<>(source.size());
        for (S stack : source) {
            copies.add(semantics.copyOf(stack));
        }
        return copies;
    }
}
