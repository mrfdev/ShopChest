package de.epiceric.shopchest.advertising;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PurchaseEscrowEvidenceTest {

    private final TestStackSemantics semantics = new TestStackSemantics();
    private final ExactStackRemovalPlanner<TestStack> planner =
            new ExactStackRemovalPlanner<>(semantics);

    @Test
    void capturesOnlyAffectedSlotsAndPreservesExactRemovedStacks() {
        final TestStack template = stack("token", 1, "genuine");
        final List<TestStack> before = List.of(
                stack("token", 2, "genuine"),
                stack("stone", 64, "ordinary"),
                stack("token", 4, "genuine"));
        final StackRemovalPlan<TestStack> plan = planner.plan(before, template, 5);

        final PurchaseEscrowEvidence<TestStack> evidence =
                PurchaseEscrowEvidence.fromPlan(plan, semantics);

        assertEquals(List.of(0, 2), evidence.affectedSlots());
        assertEquals(List.of(
                stack("token", 2, "genuine"),
                stack("token", 3, "genuine")), evidence.removedStacks());

        final List<TestStack> unrelatedSlotChanged = new ArrayList<>(before);
        unrelatedSlotChanged.set(1, stack("dirt", 12, "ordinary"));
        assertEquals(PurchaseInventoryState.BEFORE,
                evidence.classify(unrelatedSlotChanged));

        final List<TestStack> charged = evidence.applyCharge(unrelatedSlotChanged);
        assertEquals(PurchaseInventoryState.AFTER, evidence.classify(charged));
        assertEquals(stack("dirt", 12, "ordinary"), charged.get(1));

        final List<TestStack> restored = evidence.restoreRefund(charged);
        assertEquals(unrelatedSlotChanged, restored);
    }

    @Test
    void refusesToChargeOrRefundWhenAnAffectedSlotDiverged() {
        final TestStack template = stack("token", 1, "genuine");
        final StackRemovalPlan<TestStack> plan = planner.plan(
                List.of(stack("token", 5, "genuine")), template, 5);
        final PurchaseEscrowEvidence<TestStack> evidence =
                PurchaseEscrowEvidence.fromPlan(plan, semantics);
        final List<TestStack> diverged = List.of(stack("token", 4, "changed"));

        assertEquals(PurchaseInventoryState.DIVERGED, evidence.classify(diverged));
        assertThrows(StaleStackSnapshotException.class,
                () -> evidence.applyCharge(diverged));
        assertThrows(StaleStackSnapshotException.class,
                () -> evidence.restoreRefund(diverged));
    }

    @Test
    void recoveryPolicyNeverRefundsAnUnprovenCharge() {
        assertEquals(AdvertisingPurchaseRecoveryAction.MARK_NOT_CHARGED,
                AdvertisingPurchaseRecoveryPolicy.decide(
                        AdvertisingPurchaseStatus.PREPARED,
                        PurchaseInventoryState.BEFORE));
        assertEquals(AdvertisingPurchaseRecoveryAction.RETRY_DELIVERY,
                AdvertisingPurchaseRecoveryPolicy.decide(
                        AdvertisingPurchaseStatus.PREPARED,
                        PurchaseInventoryState.AFTER));
        assertEquals(AdvertisingPurchaseRecoveryAction.WAIT_FOR_EXACT_EVIDENCE,
                AdvertisingPurchaseRecoveryPolicy.decide(
                        AdvertisingPurchaseStatus.PREPARED,
                        PurchaseInventoryState.DIVERGED));
        assertEquals(AdvertisingPurchaseRecoveryAction.RESTORE_REFUND,
                AdvertisingPurchaseRecoveryPolicy.decide(
                        AdvertisingPurchaseStatus.REFUND_PENDING,
                        PurchaseInventoryState.AFTER));
        assertEquals(AdvertisingPurchaseRecoveryAction.MARK_REFUNDED,
                AdvertisingPurchaseRecoveryPolicy.decide(
                        AdvertisingPurchaseStatus.REFUND_PENDING,
                        PurchaseInventoryState.BEFORE));
        assertEquals(AdvertisingPurchaseRecoveryAction.WAIT_FOR_EXACT_EVIDENCE,
                AdvertisingPurchaseRecoveryPolicy.decide(
                        AdvertisingPurchaseStatus.REFUND_PENDING,
                        PurchaseInventoryState.DIVERGED));
    }

    private static TestStack stack(String material, int amount, String metadata) {
        return new TestStack(material, amount, metadata);
    }

    record TestStack(String material, int amount, String metadata) {
    }

    static final class TestStackSemantics implements StackSemantics<TestStack> {

        @Override
        public boolean isEmpty(TestStack stack) {
            return stack == null || stack.amount() <= 0 || "air".equals(stack.material());
        }

        @Override
        public int amount(TestStack stack) {
            return stack.amount();
        }

        @Override
        public TestStack copyOf(TestStack stack) {
            return stack == null ? null
                    : new TestStack(stack.material(), stack.amount(), stack.metadata());
        }

        @Override
        public TestStack withAmount(TestStack stack, int amount) {
            return new TestStack(stack.material(), amount, stack.metadata());
        }

        @Override
        public boolean isSimilar(TestStack candidate, TestStack template) {
            return candidate.material().equals(template.material())
                    && candidate.metadata().equals(template.metadata());
        }

        @Override
        public boolean exactlyEquals(TestStack left, TestStack right) {
            return Objects.equals(left, right);
        }
    }
}
