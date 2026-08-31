package de.epiceric.shopchest.advertising;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorityMutationGateTest {

    @Test
    void authorityIsUnreadableForTheEntireMutationWindow() {
        final AuthorityMutationGate gate = new AuthorityMutationGate();
        final long original = gate.stableGeneration().orElseThrow();

        gate.begin();

        assertTrue(gate.stableGeneration().isEmpty());
        assertFalse(gate.isCurrent(original));

        gate.finish();
        final long replaced = gate.stableGeneration().orElseThrow();
        assertFalse(gate.isCurrent(original));
        assertTrue(gate.isCurrent(replaced));
    }

    @Test
    void overlappingQueuedMutationsRemainFailClosedUntilAllFinish() {
        final AuthorityMutationGate gate = new AuthorityMutationGate();
        gate.begin();
        gate.begin();

        gate.finish();
        assertTrue(gate.stableGeneration().isEmpty());

        gate.finish();
        assertTrue(gate.stableGeneration().isPresent());
    }
}
