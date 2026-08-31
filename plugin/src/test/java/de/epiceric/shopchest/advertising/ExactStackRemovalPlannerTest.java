package de.epiceric.shopchest.advertising;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExactStackRemovalPlannerTest {

    private final TestStackSemantics semantics = new TestStackSemantics();
    private final ExactStackRemovalPlanner<TestStack> planner =
            new ExactStackRemovalPlanner<>(semantics);

    @Test
    void plansExactRemovalAcrossStacksWithoutMutatingTheInventorySnapshot() {
        final TestStack template = new TestStack("token", 1, "complete-metadata");
        final TestStack firstTokens = new TestStack("token", 2, "complete-metadata");
        final TestStack unrelated = new TestStack("stone", 64, "none");
        final TestStack lastTokens = new TestStack("token", 4, "complete-metadata");
        final List<TestStack> inventory = new ArrayList<>(
                Arrays.asList(firstTokens, unrelated, lastTokens));

        final StackRemovalPlan<TestStack> plan = planner.plan(inventory, template, 5);

        assertEquals(List.of(0, 2), plan.affectedSlots());
        assertEquals(List.of(
                new TestStack("token", 2, "complete-metadata"),
                new TestStack("token", 3, "complete-metadata")), plan.removedStacks());
        assertEquals(
                Arrays.asList(null, unrelated, new TestStack("token", 1, "complete-metadata")),
                plan.after().slots());
        assertEquals(List.of(firstTokens, unrelated, lastTokens), inventory);
    }

    @Test
    void rejectsAnInsufficientBalanceWithoutProducingAPartialPlan() {
        final TestStack template = new TestStack("token", 1, "complete-metadata");
        final List<TestStack> inventory = new ArrayList<>(List.of(
                new TestStack("token", 4, "complete-metadata"),
                new TestStack("token", 64, "forged-metadata")));

        final InsufficientCurrencyException failure = assertThrows(
                InsufficientCurrencyException.class,
                () -> planner.plan(inventory, template, 5));

        assertEquals(5, failure.required());
        assertEquals(4, failure.found());
        assertEquals(List.of(
                new TestStack("token", 4, "complete-metadata"),
                new TestStack("token", 64, "forged-metadata")), inventory);
    }

    @Test
    void refusesToApplyOrRollBackWhenAnyCapturedSlotChanged() {
        final TestStack template = new TestStack("token", 1, "complete-metadata");
        final StackRemovalPlan<TestStack> plan = planner.plan(
                List.of(new TestStack("token", 5, "complete-metadata")), template, 5);
        final List<TestStack> changedBeforeApply =
                List.of(new TestStack("token", 4, "complete-metadata"));
        final List<TestStack> changedAfterApply =
                List.of(new TestStack("token", 1, "new-metadata"));

        assertThrows(StaleStackSnapshotException.class,
                () -> plan.applyTo(changedBeforeApply));
        assertThrows(StaleStackSnapshotException.class,
                () -> plan.rollbackFrom(changedAfterApply));
    }

    @Test
    void retainedSnapshotsCanDriveAnExactApplyAndRollback() {
        final TestStack template = new TestStack("token", 1, "complete-metadata");
        final TestStack tokens = new TestStack("token", 7, "complete-metadata");
        final StackRemovalPlan<TestStack> plan = planner.plan(List.of(tokens), template, 5);

        final List<TestStack> after = plan.applyTo(List.of(tokens));
        final List<TestStack> restored = plan.rollbackFrom(after);

        assertEquals(List.of(new TestStack("token", 2, "complete-metadata")), after);
        assertEquals(List.of(tokens), restored);
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
            return java.util.Objects.equals(left, right);
        }
    }
}
