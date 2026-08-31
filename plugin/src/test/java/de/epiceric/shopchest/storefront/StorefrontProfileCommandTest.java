package de.epiceric.shopchest.storefront;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

class StorefrontProfileCommandTest {

    @Test
    void parsesMultiWordProfileUpdatesWithoutLosingPlayerText() {
        final StorefrontProfileCommand command = StorefrontProfileCommand.parse(new String[]{
                "profile", "set", "description", "JahLion's", "special", "gear", "shop!"
        });

        assertEquals(
                new StorefrontProfileCommand.SetField(
                        StorefrontProfileField.DESCRIPTION,
                        "JahLion's special gear shop!"),
                command);
    }

    @Test
    void rejectsTextThatCouldFormatOrInjectPublicProfileOutput() {
        assertThrows(IllegalArgumentException.class, () ->
                StorefrontTextPolicy.normalize(StorefrontProfileField.TAGLINE, "&aCheap armor"));
        assertThrows(IllegalArgumentException.class, () ->
                StorefrontTextPolicy.normalize(StorefrontProfileField.TAGLINE, "Visit https://example.test"));
        assertThrows(IllegalArgumentException.class, () ->
                StorefrontTextPolicy.normalize(StorefrontProfileField.TAGLINE, "Hello\nthere"));
        assertThrows(IllegalArgumentException.class, () ->
                StorefrontTextPolicy.normalize(StorefrontProfileField.TAGLINE, "%player_name% sale"));
    }

    @Test
    void acceptsPlayerFacingAdvertisementAndLocationAliases() {
        assertEquals(StorefrontProfileField.TAGLINE,
                StorefrontProfileField.parse("advertisement"));
        assertEquals(StorefrontProfileField.DIRECTIONS,
                StorefrontProfileField.parse("location"));
    }

    @Test
    void rejectsCorruptPersistedProfileTextAtTheDomainBoundary() {
        assertThrows(IllegalArgumentException.class, () -> new StorefrontProfile(
                new UUID(0L, 1L),
                "Unsafe\nStore",
                null,
                null,
                null,
                false,
                false,
                1L));
    }
}
