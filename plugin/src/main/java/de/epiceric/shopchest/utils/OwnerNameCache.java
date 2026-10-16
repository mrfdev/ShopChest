package de.epiceric.shopchest.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/** Server-thread presentation data, retained only while a hologram uses it. */
public final class OwnerNameCache {

    private final Function<UUID, String> localLookup;
    private final Map<UUID, Entry> entries = new HashMap<>();

    public OwnerNameCache(Function<UUID, String> localLookup) {
        this.localLookup = Objects.requireNonNull(localLookup);
    }

    public Entry acquire(UUID ownerId) {
        Entry entry = entries.computeIfAbsent(ownerId, id -> new Entry(id, localLookup.apply(id)));
        entry.references++;
        return entry;
    }

    public void release(Entry entry) {
        if (--entry.references == 0) {
            entries.remove(entry.ownerId, entry);
        }
    }

    /** A join or another authoritative local observation supersedes a cached miss or old name. */
    public void remember(UUID ownerId, String name) {
        Entry entry = entries.get(ownerId);
        if (entry != null && name != null && !name.isBlank()) {
            entry.name = name;
        }
    }

    public void clear() {
        entries.clear();
    }

    public static final class Entry {
        private final UUID ownerId;
        private String name;
        private int references;

        private Entry(UUID ownerId, String name) {
            this.ownerId = ownerId;
            // Cache missing names too. Never guess, strip Bedrock prefixes, or resolve by name.
            this.name = name == null || name.isBlank() ? ownerId.toString() : name;
        }

        public String name() {
            return name;
        }
    }
}
