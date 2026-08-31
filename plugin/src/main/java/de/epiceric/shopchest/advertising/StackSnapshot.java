package de.epiceric.shopchest.advertising;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** A defensive, exact snapshot of every slot in an inventory scope. */
public final class StackSnapshot<S> {

    private final StackSemantics<S> semantics;
    private final List<S> slots;

    private StackSnapshot(StackSemantics<S> semantics, List<S> slots) {
        this.semantics = Objects.requireNonNull(semantics, "semantics");
        this.slots = copySlots(slots);
    }

    public static <S> StackSnapshot<S> capture(
            List<? extends S> slots,
            StackSemantics<S> semantics
    ) {
        Objects.requireNonNull(slots, "slots");
        return new StackSnapshot<>(semantics, new ArrayList<>(slots));
    }

    /** Returns defensive copies; null entries continue to represent empty slots. */
    public List<S> slots() {
        return Collections.unmodifiableList(copySlots(slots));
    }

    public boolean matches(List<? extends S> candidate) {
        if (candidate == null || candidate.size() != slots.size()) {
            return false;
        }
        for (int index = 0; index < slots.size(); index++) {
            if (!semantics.exactlyEquals(slots.get(index), candidate.get(index))) {
                return false;
            }
        }
        return true;
    }

    private List<S> copySlots(List<? extends S> source) {
        final List<S> copies = new ArrayList<>(source.size());
        for (S stack : source) {
            copies.add(semantics.copyOf(stack));
        }
        return copies;
    }
}
