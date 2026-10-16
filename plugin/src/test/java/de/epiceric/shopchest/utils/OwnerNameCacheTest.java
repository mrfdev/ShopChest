package de.epiceric.shopchest.utils;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class OwnerNameCacheTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0009-01faeaf01111");

    @Test
    void sharesNamesAndMissesUntilTheLastHologramReleasesTheOwner() {
        AtomicInteger lookups = new AtomicInteger();
        OwnerNameCache cache = new OwnerNameCache(id -> { lookups.incrementAndGet(); return " "; });
        var first = cache.acquire(OWNER);
        var second = cache.acquire(OWNER);
        assertSame(first, second);
        assertEquals(OWNER.toString(), first.name());
        cache.remember(OWNER, ".BedrockName");
        assertEquals(".BedrockName", second.name());
        cache.remember(OWNER, "");
        cache.remember(OWNER, null);
        assertEquals(".BedrockName", first.name());
        cache.release(first);
        assertSame(second, cache.acquire(OWNER));
        assertEquals(1, lookups.get());
        cache.release(second);
        cache.release(second);
        assertNotSame(second, cache.acquire(OWNER));
        assertEquals(2, lookups.get());
    }

    @Test
    void staleReleasesAfterShutdownCannotEvictNewEntriesAndNamesNeverMergeUuids() {
        OwnerNameCache cache = new OwnerNameCache(id -> "SharedName");
        var old = cache.acquire(OWNER);
        cache.clear();
        var current = cache.acquire(OWNER);
        cache.release(old);
        assertSame(current, cache.acquire(OWNER));
        UUID otherId = UUID.fromString("f411068c-86d3-4c4b-8c3e-746abd44cf80");
        var other = cache.acquire(otherId);
        cache.remember(OWNER, ".Renamed");
        assertEquals("SharedName", other.name());
        assertEquals(".Renamed", current.name());
    }
}
