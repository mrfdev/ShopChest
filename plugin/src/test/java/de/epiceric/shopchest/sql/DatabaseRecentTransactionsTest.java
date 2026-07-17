package de.epiceric.shopchest.sql;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseRecentTransactionsTest {

    private static final UUID PLAYER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Test
    void returnsNewestRelevantRowsAndExcludesAdminVendorActivity() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE logs (
                        id INTEGER PRIMARY KEY,
                        shop_id INTEGER NOT NULL,
                        timestamp TEXT NOT NULL,
                        time INTEGER NOT NULL,
                        player_name TEXT NOT NULL,
                        player_uuid TEXT NOT NULL,
                        product_name TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        vendor_name TEXT NOT NULL,
                        vendor_uuid TEXT NOT NULL,
                        admin INTEGER NOT NULL,
                        world TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        price REAL NOT NULL,
                        type TEXT NOT NULL
                    )
                    """);

            insert(connection, 1, 100, PLAYER, OTHER, false, "BUY");
            insert(connection, 2, 200, OTHER, PLAYER, false, "SELL");
            insert(connection, 3, 300, OTHER, PLAYER, true, "BUY");
            insert(connection, 4, 350, OTHER, OTHER, false, "BUY");
            insert(connection, 5, 400, PLAYER, OTHER, true, "SELL");

            final RecentTransactionPage firstPage = Database.queryRecentTransactions(
                    connection, "logs", PLAYER, 1, 2);
            assertEquals(3, firstPage.totalEntries());
            assertEquals(2, firstPage.pageCount());
            assertEquals(1, firstPage.page());
            assertEquals(java.util.List.of(5L, 2L),
                    firstPage.entries().stream().map(RecentTransaction::id).toList());
            assertEquals(105, firstPage.entries().getFirst().shopId());
            assertEquals("spawn", firstPage.entries().getFirst().world());

            final RecentTransactionPage clampedLastPage = Database.queryRecentTransactions(
                    connection, "logs", PLAYER, 99, 2);
            assertEquals(2, clampedLastPage.page());
            assertEquals(java.util.List.of(1L),
                    clampedLastPage.entries().stream().map(RecentTransaction::id).toList());
        }
    }

    private static void insert(
            Connection connection,
            int id,
            long time,
            UUID player,
            UUID vendor,
            boolean admin,
            String type
    ) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO logs VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            statement.setInt(1, id);
            statement.setInt(2, 100 + id);
            statement.setString(3, "2026-07-17 09:00:00");
            statement.setLong(4, time);
            statement.setString(5, "Player-" + id);
            statement.setString(6, player.toString());
            statement.setString(7, "Oak Log");
            statement.setInt(8, 5);
            statement.setString(9, "Vendor-" + id);
            statement.setString(10, vendor.toString());
            statement.setBoolean(11, admin);
            statement.setString(12, "spawn");
            statement.setInt(13, 10);
            statement.setInt(14, 64);
            statement.setInt(15, -20);
            statement.setDouble(16, 12.5);
            statement.setString(17, type);
            statement.executeUpdate();
        }
    }
}
