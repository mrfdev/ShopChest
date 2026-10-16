package de.epiceric.shopchest.sql;

import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PendingShopMovesTest {
    private final PendingShopMoves.Anchor first = new PendingShopMoves.Anchor("world", 0, 64, 0);
    private final PendingShopMoves.Anchor second = new PendingShopMoves.Anchor("world", 1, 64, 0);

    @Test
    void outOfOrderWorkersCannotOverwriteTheLatestAnchor() {
        PendingShopMoves moves = new PendingShopMoves();
        List<PendingShopMoves.Anchor> writes = new ArrayList<>();
        Runnable old = moves.prepare(7, first, writes::add);
        Runnable latest = moves.prepare(7, second, writes::add);
        latest.run();
        old.run();
        assertEquals(List.of(second), writes);
        assertEquals(0, moves.size());
    }

    @Test
    void aNewRequestNeverWaitsForAnInFlightDatabaseWrite() throws Exception {
        PendingShopMoves moves = new PendingShopMoves();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<PendingShopMoves.Anchor> writes = new ArrayList<>();
        try (var workers = Executors.newFixedThreadPool(2)) {
            var old = workers.submit(moves.prepare(7, first, anchor -> {
                entered.countDown();
                try {
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    throw new AssertionError(exception);
                }
                writes.add(anchor);
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            try {
                Runnable request = assertTimeoutPreemptively(java.time.Duration.ofSeconds(1),
                        () -> moves.prepare(7, second, writes::add));
                var latest = workers.submit(request);
                release.countDown();
                old.get(5, TimeUnit.SECONDS);
                latest.get(5, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }
        assertEquals(List.of(first, second), writes);
        assertEquals(0, moves.size());
    }

    @Test
    void shutdownAndFailedWritesDoNotRetainPendingWork() {
        PendingShopMoves moves = new PendingShopMoves();
        Runnable cancelled = moves.prepare(7, first, anchor -> fail("shutdown write"));
        moves.clear();
        cancelled.run();
        Runnable failing = moves.prepare(7, second, anchor -> { throw new IllegalStateException("SQL failed"); });
        assertThrows(IllegalStateException.class, failing::run);
        assertEquals(0, moves.size());
    }

    @Test
    void movingAnAnchorPreservesTermsAndNeverResurrectsARemovedShop() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE shops (id INT PRIMARY KEY, world TEXT, x INT, y INT, z INT, vendor TEXT, price INT)");
            statement.execute("INSERT INTO shops VALUES (7, 'world', 0, 64, 0, '00000000-0000-0000-0009-01faeaf01111', 12)");
            Database.moveShop(connection, "shops", 7, second);
            try (var rows = statement.executeQuery("SELECT * FROM shops")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt("x"));
                assertEquals("00000000-0000-0000-0009-01faeaf01111", rows.getString("vendor"));
                assertEquals(12, rows.getInt("price"));
            }
            statement.execute("DELETE FROM shops WHERE id = 7");
            Database.moveShop(connection, "shops", 7, first);
            try (var rows = statement.executeQuery("SELECT count(*) FROM shops")) {
                assertEquals(0, rows.getInt(1));
            }
        }
    }
}
