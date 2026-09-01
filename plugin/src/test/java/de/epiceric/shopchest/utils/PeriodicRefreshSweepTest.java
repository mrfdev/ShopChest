package de.epiceric.shopchest.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PeriodicRefreshSweepTest {

    @Test
    void refreshesUniqueValuesInBoundedDeterministicBatches() {
        PeriodicRefreshSweep<String> sweep = new PeriodicRefreshSweep<>(3, 2);
        List<String> refreshed = new ArrayList<>();

        sweep.tick(true, () -> List.of("one", "two", "one", "three"), refreshed::add);
        sweep.tick(true, () -> List.of("one", "two", "one", "three"), refreshed::add);
        assertEquals(List.of(), refreshed);

        sweep.tick(true, () -> List.of("one", "two", "one", "three"), refreshed::add);
        assertEquals(List.of("one", "two"), refreshed);

        sweep.tick(true, () -> List.of("changed"), refreshed::add);
        assertEquals(List.of("one", "two", "three"), refreshed);
    }

    @Test
    void disablingClearsPendingWorkAndRestartsTheInterval() {
        PeriodicRefreshSweep<String> sweep = new PeriodicRefreshSweep<>(2, 1);
        List<String> refreshed = new ArrayList<>();

        sweep.tick(true, () -> List.of("old-one", "old-two"), refreshed::add);
        sweep.tick(true, () -> List.of("old-one", "old-two"), refreshed::add);
        assertEquals(List.of("old-one"), refreshed);

        sweep.tick(false, () -> List.of("ignored"), refreshed::add);
        sweep.tick(true, () -> List.of("new"), refreshed::add);
        assertEquals(List.of("old-one"), refreshed);

        sweep.tick(true, () -> List.of("new"), refreshed::add);
        assertEquals(List.of("old-one", "new"), refreshed);
    }

    @Test
    void snapshotsLoadedValuesOnlyWhenARefreshCycleStarts() {
        PeriodicRefreshSweep<String> sweep = new PeriodicRefreshSweep<>(2, 1);
        AtomicInteger snapshots = new AtomicInteger();
        List<String> refreshed = new ArrayList<>();

        sweep.tick(true, () -> {
            snapshots.incrementAndGet();
            return List.of("one", "two");
        }, refreshed::add);
        assertEquals(0, snapshots.get());

        sweep.tick(true, () -> {
            snapshots.incrementAndGet();
            return List.of("one", "two");
        }, refreshed::add);
        sweep.tick(true, () -> {
            snapshots.incrementAndGet();
            return List.of("changed");
        }, refreshed::add);

        assertEquals(1, snapshots.get());
        assertEquals(List.of("one", "two"), refreshed);
    }

    @Test
    void rejectsNonPositiveLimits() {
        assertThrows(IllegalArgumentException.class,
                () -> new PeriodicRefreshSweep<>(0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new PeriodicRefreshSweep<>(1, 0));
    }
}
