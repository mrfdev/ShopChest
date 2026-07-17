package de.epiceric.shopchest.sql;

import java.util.List;

public record RecentTransactionPage(
        List<RecentTransaction> entries,
        int page,
        int pageCount,
        int totalEntries
) {

    public RecentTransactionPage {
        entries = List.copyOf(entries);
    }
}
