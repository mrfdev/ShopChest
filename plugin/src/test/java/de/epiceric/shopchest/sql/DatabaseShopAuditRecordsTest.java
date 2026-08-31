package de.epiceric.shopchest.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseShopAuditRecordsTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsMalformedRowsWithoutParsingOrChangingThem() throws Exception {
        final HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:"
                + temporaryDirectory.resolve("shop-audit.db"));

        try (HikariDataSource source = new HikariDataSource(config)) {
            createFixture(source);

            final List<ShopAuditRecord> records;
            try (Connection connection = source.getConnection()) {
                records = Database.queryShopAuditRecords(connection, "shops");
            }

            assertEquals(5, records.size());
            assertEquals(List.of("1", "2", "3", "4", "5"), records.stream()
                    .map(ShopAuditRecord::rawId)
                    .toList());
            assertEquals("not-a-uuid", records.get(1).vendor());
            assertEquals("not-base64", records.get(1).product());
            assertEquals("UNKNOWN", records.get(1).shopType());
            assertEquals(-5, records.get(2).parsedAmount());
            assertEquals("not-an-integer", records.get(3).rawAmount());
            assertEquals(null, records.get(3).parsedAmount());
            assertEquals("bad-x", records.get(3).rawX());
            assertEquals(null, records.get(3).parsedX());
            assertEquals("bad-price", records.get(3).rawBuyPrice());
            assertEquals(null, records.get(3).parsedBuyPrice());
            assertEquals(null, records.get(4).rawAmount());
            assertEquals(null, records.get(4).parsedAmount());
            assertEquals(null, records.get(4).rawX());
            assertEquals(null, records.get(4).parsedX());
            assertEquals(null, records.get(4).rawBuyPrice());
            assertEquals(null, records.get(4).parsedBuyPrice());
            assertThrows(UnsupportedOperationException.class, () -> records.clear());

            try (Connection connection = source.getConnection();
                    Statement statement = connection.createStatement();
                    var resultSet = statement.executeQuery("SELECT COUNT(*) FROM shops")) {
                resultSet.next();
                assertEquals(5, resultSet.getInt(1));
            }
        }
    }

    private void createFixture(HikariDataSource source) throws Exception {
        try (Connection connection = source.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE shops (
                        id INTEGER PRIMARY KEY,
                        vendor TEXT,
                        product TEXT,
                        amount INTEGER,
                        world TEXT,
                        x INTEGER,
                        y INTEGER,
                        z INTEGER,
                        buyprice REAL,
                        sellprice REAL,
                        shoptype TEXT
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO shops VALUES
                        (3,'00000000-0000-0000-0000-000000000003','product-c',-5,
                         'world',3,64,3,10,0,'NORMAL'),
                        (1,'00000000-0000-0000-0000-000000000001','product-a',1,
                         'world',1,64,1,10,5,'NORMAL'),
                        (2,'not-a-uuid','not-base64',1,
                         'missing',2,64,2,0,0,'UNKNOWN'),
                        (4,'00000000-0000-0000-0000-000000000004','product-d',
                         'not-an-integer','world','bad-x',64,4,'bad-price',5,'NORMAL'),
                        (5,'00000000-0000-0000-0000-000000000005','product-e',
                         NULL,'world',NULL,64,5,NULL,5,'NORMAL')
                    """);
        }
    }
}
