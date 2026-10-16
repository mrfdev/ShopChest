package de.epiceric.shopchest.utils;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class PeriodicRefreshSweep<T> {

    private final int intervalTicks;
    private final int maxPerTick;
    private Iterator<? extends T> pending = Collections.emptyIterator();
    private Set<T> visited = new HashSet<>();
    private int ticksSinceSweep;

    PeriodicRefreshSweep(int intervalTicks, int maxPerTick) {
        if (intervalTicks <= 0) {
            throw new IllegalArgumentException("intervalTicks must be positive");
        }
        if (maxPerTick <= 0) {
            throw new IllegalArgumentException("maxPerTick must be positive");
        }
        this.intervalTicks = intervalTicks;
        this.maxPerTick = maxPerTick;
    }

    void tick(boolean enabled,
              Supplier<? extends Collection<? extends T>> currentValues,
              Consumer<? super T> refresher) {
        Objects.requireNonNull(currentValues, "currentValues");
        Objects.requireNonNull(refresher, "refresher");

        if (!enabled) {
            reset();
            return;
        }

        if (!pending.hasNext()) {
            ticksSinceSweep++;
            if (ticksSinceSweep < intervalTicks) {
                return;
            }

            Collection<? extends T> values = Objects.requireNonNull(
                    currentValues.get(), "currentValues returned null");
            // The production source is a weakly consistent concurrent-map view. Do not copy
            // every shop on one tick. Duplicate container entries count against the budget too.
            pending = values.iterator();
            ticksSinceSweep = 0;
        }

        int examined = 0;
        while (examined < maxPerTick && pending.hasNext()) {
            T value = pending.next();
            if (visited.add(value)) {
                refresher.accept(value);
            }
            examined++;
        }
        if (!pending.hasNext()) {
            pending = Collections.emptyIterator();
            visited = new HashSet<>();
        }
    }

    void reset() {
        pending = Collections.emptyIterator();
        visited = new HashSet<>();
        ticksSinceSweep = 0;
    }
}
