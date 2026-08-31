package de.epiceric.shopchest.sql;

import de.epiceric.shopchest.storefront.StorefrontProfile;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcStorefrontRepositoryTest {

    @Test
    void savesOneIndependentProfilePerOwnerUuid() throws Exception {
        final UUID owner = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            JdbcStorefrontRepository.initialize(connection, "shopchest_");

            JdbcStorefrontRepository.saveProfile(
                    connection,
                    "shopchest_",
                    new StorefrontProfile(
                            owner,
                            "Lion Forge",
                            "Need protection?",
                            "JahLion's special gear shop!",
                            "Look for the lion head on the left",
                            false,
                            false,
                            1234L));

            assertEquals(
                    "Lion Forge",
                    JdbcStorefrontRepository.findProfile(connection, "shopchest_", owner)
                            .orElseThrow()
                            .name());
        }
    }

    @Test
    void replacesAtMostThreeFeaturedShopIdsInOwnerSelectedOrder() throws Exception {
        final UUID owner = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            JdbcStorefrontRepository.initialize(connection, "shopchest_");

            JdbcStorefrontRepository.replaceFeatured(
                    connection, "shopchest_", owner, List.of(17, 4, 99));

            assertEquals(
                    List.of(17, 4, 99),
                    JdbcStorefrontRepository.findFeatured(
                            connection, "shopchest_", owner));
        }
    }

    @Test
    void suspensionCanBeReadWithoutLoadingOrChangingShopRows() throws Exception {
        final UUID owner = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            JdbcStorefrontRepository.initialize(connection, "shopchest_");
            JdbcStorefrontRepository.saveProfile(
                    connection,
                    "shopchest_",
                    StorefrontProfile.empty(owner, 1L).withModeration(false, true, 2L));

            assertEquals(
                    java.util.Set.of(owner),
                    JdbcStorefrontRepository.findSuspendedOwners(connection, "shopchest_"));
        }
    }

    @Test
    void failsClosedWhenPersistedPublicProfileTextIsCorrupt() throws Exception {
        final UUID owner = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            JdbcStorefrontRepository.initialize(connection, "shopchest_");
            try (var statement = connection.prepareStatement(
                    "INSERT INTO shopchest_storefront_profiles "
                            + "(owner_uuid,storefront_name,tagline,description,directions,"
                            + "text_hidden,suspended,updated_at) VALUES(?,?,?,?,?,?,?,?)")) {
                statement.setString(1, owner.toString());
                statement.setString(2, "Unsafe\nStorefront");
                statement.setString(3, null);
                statement.setString(4, null);
                statement.setString(5, null);
                statement.setBoolean(6, false);
                statement.setBoolean(7, false);
                statement.setLong(8, 1L);
                statement.executeUpdate();
            }

            assertTrue(JdbcStorefrontRepository.findProfile(
                    connection, "shopchest_", owner).orElseThrow().suspended());
            assertTrue(JdbcStorefrontRepository.findProfiles(
                    connection, "shopchest_").get(owner).suspended());
        }
    }
}
