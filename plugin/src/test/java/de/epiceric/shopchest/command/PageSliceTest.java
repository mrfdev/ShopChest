package de.epiceric.shopchest.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class PageSliceTest {

    @Test
    void returnsTheRequestedPage() {
        final PageSlice<Integer> page = PageSlice.of(List.of(1, 2, 3, 4, 5), 2, 2);

        assertEquals(List.of(3, 4), page.entries());
        assertEquals(2, page.page());
        assertEquals(3, page.pageCount());
        assertEquals(5, page.totalEntries());
    }

    @Test
    void clampsPagesBeyondTheAvailableRange() {
        final PageSlice<Integer> page = PageSlice.of(List.of(1, 2, 3), 99, 2);

        assertEquals(List.of(3), page.entries());
        assertEquals(2, page.page());
    }

    @Test
    void emptyListsStillHaveOnePage() {
        final PageSlice<Integer> page = PageSlice.of(List.of(), 1, 8);

        assertEquals(List.of(), page.entries());
        assertEquals(1, page.page());
        assertEquals(1, page.pageCount());
    }

    @Test
    void rejectsInvalidPageSizes() {
        assertThrows(IllegalArgumentException.class, () -> PageSlice.of(List.of(1), 1, 0));
    }
}
