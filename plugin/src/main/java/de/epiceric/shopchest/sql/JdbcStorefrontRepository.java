package de.epiceric.shopchest.sql;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.config.Config;
import de.epiceric.shopchest.storefront.StorefrontProfile;
import de.epiceric.shopchest.utils.Callback;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class JdbcStorefrontRepository {

    private static final String PROFILES_SUFFIX = "storefront_profiles";
    private static final String FEATURED_SUFFIX = "storefront_featured";

    private final ShopChest plugin;
    private final Database database;

    public JdbcStorefrontRepository(ShopChest plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
    }

    public void findProfile(UUID ownerId, Callback<Optional<StorefrontProfile>> callback) {
        runAsync(callback, connection -> findProfile(
                connection, Config.databaseTablePrefix, ownerId));
    }

    public void saveProfile(StorefrontProfile profile, Callback<Void> callback) {
        runAsync(callback, connection -> {
            saveProfile(connection, Config.databaseTablePrefix, profile);
            return null;
        });
    }

    public void findFeatured(UUID ownerId, Callback<List<Integer>> callback) {
        runAsync(callback, connection -> findFeatured(
                connection, Config.databaseTablePrefix, ownerId));
    }

    public void findSuspendedOwners(Callback<Set<UUID>> callback) {
        runAsync(callback, connection -> findSuspendedOwners(
                connection, Config.databaseTablePrefix));
    }

    public void findProfiles(Callback<Map<UUID, StorefrontProfile>> callback) {
        runAsync(callback, connection -> findProfiles(
                connection, Config.databaseTablePrefix));
    }

    public void replaceFeatured(
            UUID ownerId,
            List<Integer> shopIds,
            Callback<Void> callback
    ) {
        runAsync(callback, connection -> {
            replaceFeatured(connection, Config.databaseTablePrefix, ownerId, shopIds);
            return null;
        });
    }

    private <T> void runAsync(Callback<T> callback, SqlOperation<T> operation) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (database.dataSource == null || database.dataSource.isClosed()) {
                    callback.callSyncError(new IllegalStateException(
                            "Shop database is not currently available"));
                    return;
                }
                try (Connection connection = database.dataSource.getConnection()) {
                    callback.callSyncResult(operation.run(connection));
                } catch (SQLException | RuntimeException exception) {
                    plugin.debug(exception);
                    callback.callSyncError(exception);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    @FunctionalInterface
    private interface SqlOperation<T> {
        T run(Connection connection) throws SQLException;
    }

    public static void initialize(Connection connection, String tablePrefix) throws SQLException {
        final String profiles = table(tablePrefix, PROFILES_SUFFIX);
        final String featured = table(tablePrefix, FEATURED_SUFFIX);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + profiles + " ("
                    + "owner_uuid VARCHAR(36) PRIMARY KEY NOT NULL,"
                    + "storefront_name VARCHAR(32),"
                    + "tagline VARCHAR(80),"
                    + "description VARCHAR(180),"
                    + "directions VARCHAR(120),"
                    + "text_hidden INTEGER NOT NULL,"
                    + "suspended INTEGER NOT NULL,"
                    + "updated_at BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + featured + " ("
                    + "owner_uuid VARCHAR(36) NOT NULL,"
                    + "position INTEGER NOT NULL,"
                    + "shop_id INTEGER NOT NULL,"
                    + "PRIMARY KEY(owner_uuid,position),"
                    + "UNIQUE(owner_uuid,shop_id))");
        }
    }

    public static Optional<StorefrontProfile> findProfile(
            Connection connection,
            String tablePrefix,
            UUID ownerId
    ) throws SQLException {
        final String query = "SELECT storefront_name,tagline,description,directions,"
                + "text_hidden,suspended,updated_at FROM "
                + table(tablePrefix, PROFILES_SUFFIX) + " WHERE owner_uuid=?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, ownerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                try {
                    return Optional.of(new StorefrontProfile(
                            ownerId,
                            resultSet.getString("storefront_name"),
                            resultSet.getString("tagline"),
                            resultSet.getString("description"),
                            resultSet.getString("directions"),
                            resultSet.getBoolean("text_hidden"),
                            resultSet.getBoolean("suspended"),
                            resultSet.getLong("updated_at")));
                } catch (IllegalArgumentException ignored) {
                    return Optional.of(corruptProfileFallback(
                            ownerId, resultSet.getLong("updated_at")));
                }
            }
        }
    }

    public static void saveProfile(
            Connection connection,
            String tablePrefix,
            StorefrontProfile profile
    ) throws SQLException {
        final String query = "REPLACE INTO " + table(tablePrefix, PROFILES_SUFFIX)
                + " (owner_uuid,storefront_name,tagline,description,directions,"
                + "text_hidden,suspended,updated_at) VALUES(?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, profile.ownerId().toString());
            statement.setString(2, profile.name());
            statement.setString(3, profile.tagline());
            statement.setString(4, profile.description());
            statement.setString(5, profile.directions());
            statement.setBoolean(6, profile.textHidden());
            statement.setBoolean(7, profile.suspended());
            statement.setLong(8, profile.updatedAt());
            statement.executeUpdate();
        }
    }

    public static Set<UUID> findSuspendedOwners(
            Connection connection,
            String tablePrefix
    ) throws SQLException {
        final Set<UUID> owners = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT owner_uuid FROM " + table(tablePrefix, PROFILES_SUFFIX)
                        + " WHERE suspended=?")) {
            statement.setBoolean(1, true);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    try {
                        owners.add(UUID.fromString(resultSet.getString("owner_uuid")));
                    } catch (IllegalArgumentException ignored) {
                        // Invalid profile rows are isolated from public discovery.
                    }
                }
            }
        }
        return Set.copyOf(owners);
    }

    public static Map<UUID, StorefrontProfile> findProfiles(
            Connection connection,
            String tablePrefix
    ) throws SQLException {
        final Map<UUID, StorefrontProfile> profiles = new LinkedHashMap<>();
        final String query = "SELECT owner_uuid,storefront_name,tagline,description,"
                + "directions,text_hidden,suspended,updated_at FROM "
                + table(tablePrefix, PROFILES_SUFFIX) + " ORDER BY owner_uuid";
        try (PreparedStatement statement = connection.prepareStatement(query);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                final UUID ownerId;
                try {
                    ownerId = UUID.fromString(resultSet.getString("owner_uuid"));
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                try {
                    profiles.put(ownerId, new StorefrontProfile(
                            ownerId,
                            resultSet.getString("storefront_name"),
                            resultSet.getString("tagline"),
                            resultSet.getString("description"),
                            resultSet.getString("directions"),
                            resultSet.getBoolean("text_hidden"),
                            resultSet.getBoolean("suspended"),
                            resultSet.getLong("updated_at")));
                } catch (IllegalArgumentException ignored) {
                    profiles.put(ownerId, corruptProfileFallback(
                            ownerId, resultSet.getLong("updated_at")));
                }
            }
        }
        return Map.copyOf(profiles);
    }

    public static List<Integer> findFeatured(
            Connection connection,
            String tablePrefix,
            UUID ownerId
    ) throws SQLException {
        final String query = "SELECT shop_id FROM " + table(tablePrefix, FEATURED_SUFFIX)
                + " WHERE owner_uuid=? ORDER BY position";
        final List<Integer> featured = new ArrayList<>(3);
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, ownerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    featured.add(resultSet.getInt("shop_id"));
                }
            }
        }
        return List.copyOf(featured);
    }

    public static void replaceFeatured(
            Connection connection,
            String tablePrefix,
            UUID ownerId,
            List<Integer> shopIds
    ) throws SQLException {
        validateFeatured(shopIds);
        final String table = table(tablePrefix, FEATURED_SUFFIX);
        final boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM " + table + " WHERE owner_uuid=?")) {
                delete.setString(1, ownerId.toString());
                delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO " + table
                            + " (owner_uuid,position,shop_id) VALUES(?,?,?)")) {
                for (int position = 0; position < shopIds.size(); position++) {
                    insert.setString(1, ownerId.toString());
                    insert.setInt(2, position);
                    insert.setInt(3, shopIds.get(position));
                    insert.addBatch();
                }
                insert.executeBatch();
            }
            connection.commit();
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static void validateFeatured(List<Integer> shopIds) {
        if (shopIds == null || shopIds.size() > 3) {
            throw new IllegalArgumentException("A storefront can feature at most three shops");
        }
        final Set<Integer> unique = new HashSet<>();
        for (Integer shopId : shopIds) {
            if (shopId == null || shopId < 0 || !unique.add(shopId)) {
                throw new IllegalArgumentException("Featured shop IDs must be unique and non-negative");
            }
        }
    }

    private static StorefrontProfile corruptProfileFallback(UUID ownerId, long updatedAt) {
        return StorefrontProfile.empty(ownerId, updatedAt)
                .withModeration(true, true, updatedAt);
    }

    static String table(String prefix, String suffix) {
        if (prefix == null || !prefix.matches("[A-Za-z0-9_-]*")) {
            throw new IllegalArgumentException("Invalid database table prefix");
        }
        return prefix + suffix;
    }
}
