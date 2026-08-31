package de.epiceric.shopchest.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchRequestGuardTest {

    @Test
    void invalidationRejectsOldCallbacksWithoutClearingANewRequest() {
        final SearchRequestGuard guard = new SearchRequestGuard();
        final SearchRequestGuard.Ticket oldRequest = guard.tryStart("player").orElseThrow();

        assertTrue(guard.tryStart("player").isEmpty());

        guard.invalidate();
        final SearchRequestGuard.Ticket newRequest = guard.tryStart("player").orElseThrow();

        assertFalse(guard.isCurrent(oldRequest));
        assertTrue(guard.isCurrent(newRequest));

        guard.finish(oldRequest);
        assertTrue(guard.tryStart("player").isEmpty());

        guard.finish(newRequest);
        assertTrue(guard.tryStart("player").isPresent());
    }
}
