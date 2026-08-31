package de.epiceric.shopchest.listeners;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

final class RevenueNoticeDelay {

    private static final long SERVER_TICKS_PER_SECOND = 20L;

    @FunctionalInterface
    interface DelayedTaskRunner {

        void runLater(Runnable task, long delayTicks);
    }

    private final DelayedTaskRunner taskRunner;
    private final IntSupplier configuredDelaySeconds;

    RevenueNoticeDelay(DelayedTaskRunner taskRunner, IntSupplier configuredDelaySeconds) {
        this.taskRunner = taskRunner;
        this.configuredDelaySeconds = configuredDelaySeconds;
    }

    void schedule(BooleanSupplier stillOnline, Runnable send) {
        final long delayTicks = Math.max(0, configuredDelaySeconds.getAsInt())
                * SERVER_TICKS_PER_SECOND;
        if (delayTicks == 0L) {
            sendIfStillOnline(stillOnline, send);
            return;
        }

        taskRunner.runLater(() -> sendIfStillOnline(stillOnline, send), delayTicks);
    }

    private static void sendIfStillOnline(BooleanSupplier stillOnline, Runnable send) {
        if (stillOnline.getAsBoolean()) {
            send.run();
        }
    }
}
