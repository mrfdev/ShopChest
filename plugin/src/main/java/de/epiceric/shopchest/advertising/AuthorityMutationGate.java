package de.epiceric.shopchest.advertising;

import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Fail-closed generation gate while durable currency-authority mutations are in flight. */
final class AuthorityMutationGate {

    private final AtomicLong generation = new AtomicLong();
    private final AtomicInteger activeMutations = new AtomicInteger();

    void begin() {
        activeMutations.incrementAndGet();
        generation.incrementAndGet();
    }

    void finish() {
        if (activeMutations.decrementAndGet() < 0) {
            activeMutations.incrementAndGet();
            throw new IllegalStateException("Authority mutation gate is unbalanced");
        }
    }

    OptionalLong stableGeneration() {
        final long observed = generation.get();
        if (activeMutations.get() != 0
                || generation.get() != observed
                || activeMutations.get() != 0) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(observed);
    }

    boolean isCurrent(long expectedGeneration) {
        return activeMutations.get() == 0
                && generation.get() == expectedGeneration
                && activeMutations.get() == 0;
    }
}
