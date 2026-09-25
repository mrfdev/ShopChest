package de.epiceric.shopchest.sql;

import de.epiceric.shopchest.storefront.StorefrontProfile;
import de.epiceric.shopchest.storefront.StorefrontDisplay;
import de.epiceric.shopchest.storefront.StorefrontDisplayConflictException;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcStorefrontRepositoryTest {

    private static final UUID WORLD =
            UUID.fromString("00000000-0000-0000-0000-000000000026");

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

    @Test
    void persistsExactlyOneStorefrontDisplayPerOwner() throws Exception {
        final UUID owner = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        final StorefrontDisplay display = display(owner, 10, 64, -20);
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            JdbcStorefrontRepository.initialize(connection, "shopchest_");

            JdbcStorefrontRepository.createDisplay(connection, "shopchest_", display);

            assertEquals(
                    display,
                    JdbcStorefrontRepository.findDisplay(
                            connection, "shopchest_", owner).orElseThrow());
            assertEquals(
                    List.of(display),
                    JdbcStorefrontRepository.findDisplays(connection, "shopchest_"));
            final StorefrontDisplayConflictException conflict = assertThrows(
                    StorefrontDisplayConflictException.class,
                    () -> JdbcStorefrontRepository.createDisplay(
                            connection,
                            "shopchest_",
                            display(owner, 40, 70, 40)));
            assertEquals(StorefrontDisplayConflictException.Kind.OWNER, conflict.kind());
        }
    }

    @Test
    void preventsTwoDisplaysFromUsingTheSameEnderChest() throws Exception {
        final UUID firstOwner = UUID.fromString("11111111-1111-1111-1111-111111111111");
        final UUID secondOwner = UUID.fromString("22222222-2222-2222-2222-222222222222");
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            JdbcStorefrontRepository.initialize(connection, "shopchest_");
            JdbcStorefrontRepository.createDisplay(
                    connection, "shopchest_", display(firstOwner, 5, 80, 5));

            final StorefrontDisplayConflictException conflict = assertThrows(
                    StorefrontDisplayConflictException.class,
                    () -> JdbcStorefrontRepository.createDisplay(
                            connection,
                            "shopchest_",
                            display(secondOwner, 5, 80, 5)));

            assertEquals(StorefrontDisplayConflictException.Kind.LOCATION, conflict.kind());
        }
    }

    @Test
    void deletingAStorefrontDisplayReleasesItsOwnerAndAnchor() throws Exception {
        final UUID firstOwner = UUID.fromString("33333333-3333-3333-3333-333333333333");
        final UUID secondOwner = UUID.fromString("44444444-4444-4444-4444-444444444444");
        final StorefrontDisplay first = display(firstOwner, 9, 90, 9);
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            JdbcStorefrontRepository.initialize(connection, "shopchest_");
            JdbcStorefrontRepository.createDisplay(connection, "shopchest_", first);

            assertEquals(
                    first,
                    JdbcStorefrontRepository.deleteDisplay(
                            connection, "shopchest_", firstOwner).orElseThrow());
            assertFalse(JdbcStorefrontRepository.findDisplay(
                    connection, "shopchest_", firstOwner).isPresent());

            JdbcStorefrontRepository.createDisplay(
                    connection, "shopchest_", display(secondOwner, 9, 90, 9));
            assertTrue(JdbcStorefrontRepository.findDisplay(
                    connection, "shopchest_", secondOwner).isPresent());
        }
    }

    private static StorefrontDisplay display(UUID owner, int x, int y, int z) {
        return new StorefrontDisplay(owner, WORLD, "world", x, y, z, 1234L);
    }
}
