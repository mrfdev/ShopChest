package de.epiceric.shopchest.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RevenueNoticeDelayTest {

    @Test
    void convertsConfiguredSecondsToTicksAndWaitsBeforeSending() {
        final AtomicReference<Runnable> pendingTask = new AtomicReference<>();
        final AtomicLong scheduledTicks = new AtomicLong(-1L);
        final RevenueNoticeDelay subject = new RevenueNoticeDelay((task, delayTicks) -> {
            pendingTask.set(task);
            scheduledTicks.set(delayTicks);
        }, () -> 3);
        final AtomicInteger sends = new AtomicInteger();

        subject.schedule(() -> true, sends::incrementAndGet);

        assertEquals(0, sends.get());
        assertEquals(60L, scheduledTicks.get());
        assertNotNull(pendingTask.get());

        pendingTask.get().run();

        assertEquals(1, sends.get());
    }

    @Test
    void doesNotSendIfPlayerLeavesDuringDelay() {
        final AtomicReference<Runnable> pendingTask = new AtomicReference<>();
        final AtomicBoolean online = new AtomicBoolean(true);
        final RevenueNoticeDelay subject = new RevenueNoticeDelay(
                (task, delayTicks) -> pendingTask.set(task),
                () -> 3);
        final AtomicInteger sends = new AtomicInteger();

        subject.schedule(online::get, sends::incrementAndGet);
        assertNotNull(pendingTask.get());
        online.set(false);
        pendingTask.get().run();

        assertEquals(0, sends.get());
    }

    @Test
    void sendsImmediatelyWhenDelayIsDisabled() {
        final AtomicBoolean taskScheduled = new AtomicBoolean();
        final RevenueNoticeDelay subject = new RevenueNoticeDelay(
                (task, delayTicks) -> taskScheduled.set(true),
                () -> 0);
        final AtomicInteger sends = new AtomicInteger();

        subject.schedule(() -> true, sends::incrementAndGet);

        assertEquals(1, sends.get());
        assertFalse(taskScheduled.get());
    }
}
