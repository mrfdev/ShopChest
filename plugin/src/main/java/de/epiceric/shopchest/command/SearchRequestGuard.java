package de.epiceric.shopchest.command;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Invalidates asynchronous search callbacks without letting an old callback release a new request. */
final class SearchRequestGuard {

    private final Map<String, Ticket> inFlight = new HashMap<>();
    private volatile long generation;

    synchronized Optional<Ticket> tryStart(String viewer) {
        if (inFlight.containsKey(viewer)) {
            return Optional.empty();
        }
        final Ticket ticket = new Ticket(viewer, generation);
        inFlight.put(viewer, ticket);
        return Optional.of(ticket);
    }

    boolean isCurrent(Ticket ticket) {
        return ticket != null && ticket.generation() == generation;
    }

    synchronized void finish(Ticket ticket) {
        if (ticket != null) {
            inFlight.remove(ticket.viewer(), ticket);
        }
    }

    synchronized void invalidate() {
        generation++;
        inFlight.clear();
    }

    record Ticket(String viewer, long generation) {
    }
}
