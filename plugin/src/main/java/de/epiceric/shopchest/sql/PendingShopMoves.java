package de.epiceric.shopchest.sql;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Orders/coalesces only outstanding anchor writes; never retains ordinary containers. */
final class PendingShopMoves {
    record Anchor(String world, int x, int y, int z) {}

    private static final class Pending {
        volatile Anchor latest;
        int requests; // Accessed only inside ConcurrentHashMap.compute.
    }

    private final ConcurrentHashMap<Integer, Pending> pending = new ConcurrentHashMap<>();

    Runnable prepare(int shopId, Anchor anchor, Consumer<Anchor> write) {
        Pending state = pending.compute(shopId, (id, current) -> {
            if (current == null) current = new Pending();
            current.latest = anchor;
            current.requests++;
            return current;
        });
        return () -> {
            try {
                // Only background workers take this lock; the server thread never
                // waits for SQL. A newer anchor always follows an in-flight write.
                synchronized (state) {
                    if (pending.get(shopId) == state && state.latest == anchor) write.accept(anchor);
                }
            } finally {
                pending.computeIfPresent(shopId, (id, current) -> {
                    if (current != state) return current;
                    return --current.requests == 0 ? null : current;
                });
            }
        };
    }

    void clear() {
        pending.clear();
    }

    int size() {
        return pending.size();
    }
}
