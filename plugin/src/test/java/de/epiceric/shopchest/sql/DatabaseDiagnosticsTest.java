package de.epiceric.shopchest.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseDiagnosticsTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void reportsSchemaShopLogAndPoolStateWithoutReadingShopDetails() throws Exception {
        final HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + temporaryDirectory.resolve("diagnostics.db"));
        config.setMaximumPoolSize(2);

        try (HikariDataSource source = new HikariDataSource(config)) {
            createFixture(source);

            final DatabaseDiagnostics diagnostics = Database.queryDiagnostics(
                    source,
                    "shopchest_shops",
                    "shopchest_economy_logs",
                    "shopchest_fields",
                    true);

            assertTrue(diagnostics.initialized());
            assertTrue(diagnostics.connectionValid());
            assertTrue(diagnostics.latencyMillis() >= 0);
            assertEquals(4, diagnostics.schemaVersion());
            assertEquals(3, diagnostics.totalShops());
            assertEquals(2, diagnostics.normalShops());
            assertEquals(1, diagnostics.adminShops());
            assertEquals(2, diagnostics.owners());
            assertEquals(5, diagnostics.economyLogs());
            assertTrue(diagnostics.totalConnections() >= 1);
            assertEquals(0, diagnostics.waitingThreads());
        }
    }

    private void createFixture(HikariDataSource source) throws Exception {
        try (Connection connection = source.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE shopchest_fields (
                        field TEXT PRIMARY KEY,
                        value INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE shopchest_shops (
                        id INTEGER PRIMARY KEY,
                        vendor TEXT NOT NULL,
                        shoptype TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE shopchest_economy_logs (
                        id INTEGER PRIMARY KEY
                    )
                    """);
            statement.executeUpdate(
                    "INSERT INTO shopchest_fields(field,value) VALUES ('version',4)");
            statement.executeUpdate("""
                    INSERT INTO shopchest_shops(id,vendor,shoptype) VALUES
                        (1,'owner-a','NORMAL'),
                        (2,'owner-a','NORMAL'),
                        (3,'owner-b','ADMIN')
                    """);
            statement.executeUpdate("""
                    INSERT INTO shopchest_economy_logs(id) VALUES
                        (1),(2),(3),(4),(5)
                    """);
        }
    }
}
