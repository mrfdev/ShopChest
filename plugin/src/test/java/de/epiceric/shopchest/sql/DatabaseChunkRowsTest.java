package de.epiceric.shopchest.sql;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseChunkRowsTest {

    @Test
    void queriesOnlyRowsInsideTheSnapshottedWorldAndChunkBounds() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE shops ("
                    + "id INTEGER PRIMARY KEY,vendor TEXT,product TEXT,amount INTEGER,"
                    + "world TEXT,x INTEGER,y INTEGER,z INTEGER,buyprice REAL,"
                    + "sellprice REAL,shoptype TEXT)");
            insert(statement, 1, "general", 15, 15);
            insert(statement, 2, "general", 16, 16);
            insert(statement, 3, "general", -1, -1);
            insert(statement, 4, "general", -16, -16);
            insert(statement, 5, "resource", 15, 15);

            final List<Database.PersistedShopRow> rows = Database.queryPersistedShopRows(
                    connection,
                    "shops",
                    List.of(
                            new Database.ChunkQueryCoordinate("general", 0, 0),
                            new Database.ChunkQueryCoordinate("general", -1, -1)));

            assertEquals(List.of(1, 3, 4), rows.stream()
                    .map(Database.PersistedShopRow::id)
                    .toList());
        }
    }

    @Test
    void emptyChunkSnapshotDoesNotQueryOrReturnRows() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            assertEquals(List.of(), Database.queryPersistedShopRows(
                    connection, "missing_table", List.of()));
        }
    }

    private static void insert(
            Statement statement,
            int id,
            String world,
            int x,
            int z
    ) throws Exception {
        statement.executeUpdate("INSERT INTO shops VALUES ("
                + id + ",'00000000-0000-0000-0000-000000000001','item',1,'"
                + world + "'," + x + ",64," + z + ",1,0,'NORMAL')");
    }
}
