package de.epiceric.shopchest.command;

import java.util.List;

record PageSlice<T>(List<T> entries, int page, int pageCount, int totalEntries) {

    static <T> PageSlice<T> of(List<T> entries, int requestedPage, int pageSize) {
        if (pageSize < 1) {
            throw new IllegalArgumentException("Page size must be positive");
        }

        final int pageCount = Math.max(1, (int) Math.ceil(entries.size() / (double) pageSize));
        final int page = Math.clamp(requestedPage, 1, pageCount);
        final int fromIndex = Math.min((page - 1) * pageSize, entries.size());
        final int toIndex = Math.min(fromIndex + pageSize, entries.size());

        return new PageSlice<>(List.copyOf(entries.subList(fromIndex, toIndex)), page, pageCount, entries.size());
    }
}
