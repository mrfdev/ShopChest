package de.epiceric.shopchest.utils;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class PeriodicRefreshSweep<T> {

    private final int intervalTicks;
    private final int maxPerTick;
    private final ArrayDeque<T> pending = new ArrayDeque<>();
    private int ticksSinceSnapshot;

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

        if (pending.isEmpty()) {
            ticksSinceSnapshot++;
            if (ticksSinceSnapshot < intervalTicks) {
                return;
            }

            Collection<? extends T> snapshot = Objects.requireNonNull(
                    currentValues.get(), "currentValues returned null");
            pending.addAll(new LinkedHashSet<>(snapshot));
            ticksSinceSnapshot = 0;
        }

        int refreshed = 0;
        while (refreshed < maxPerTick && !pending.isEmpty()) {
            refresher.accept(pending.removeFirst());
            refreshed++;
        }
    }

    void reset() {
        pending.clear();
        ticksSinceSnapshot = 0;
    }
}
