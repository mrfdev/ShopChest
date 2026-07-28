package de.epiceric.shopchest.sql;

import de.epiceric.shopchest.shop.ShopTerms;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseShopTermsTest {

    @Test
    void updatesAllTermsInOneStatement() throws Exception {
        try (Connection connection = databaseWithShop()) {
            Database.updateShopTerms(
                    connection,
                    "shops",
                    7,
                    new ShopTerms(16, 80.5, 20));

            try (Statement statement = connection.createStatement();
                    ResultSet result = statement.executeQuery(
                            "SELECT amount, buyprice, sellprice FROM shops WHERE id = 7")) {
                result.next();
                assertEquals(16, result.getInt("amount"));
                assertEquals(80.5, result.getDouble("buyprice"));
                assertEquals(20, result.getDouble("sellprice"));
            }
        }
    }

    @Test
    void failsClosedWhenTheShopNoLongerExists() throws Exception {
        try (Connection connection = databaseWithShop()) {
            assertThrows(
                    SQLException.class,
                    () -> Database.updateShopTerms(
                            connection,
                            "shops",
                            99,
                            new ShopTerms(16, 80, 20)));
        }
    }

    private static Connection databaseWithShop() throws Exception {
        final Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE shops (
                        id INTEGER PRIMARY KEY,
                        amount INTEGER NOT NULL,
                        buyprice REAL NOT NULL,
                        sellprice REAL NOT NULL
                    )
                    """);
            statement.executeUpdate(
                    "INSERT INTO shops (id, amount, buyprice, sellprice) VALUES (7, 5, 10, 4)");
        }
        return connection;
    }
}
