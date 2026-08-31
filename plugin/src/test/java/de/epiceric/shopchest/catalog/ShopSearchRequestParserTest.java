package de.epiceric.shopchest.catalog;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopSearchRequestParserTest {

    private final ShopSearchRequestParser parser = new ShopSearchRequestParser(
            new MaterialKeyResolver(key -> Map.of(
                    NamespacedKey.minecraft("stone_bricks"), Material.STONE_BRICKS,
                    NamespacedKey.minecraft("music_disc_5"), Material.MUSIC_DISC_5)
                    .get(key),
                    material -> true));

    @Test
    void resolvesTheCompleteJoinedMaterialBeforeTreatingANumberAsAPage() {
        final ShopSearchRequest request = parser.parse(List.of("music", "disc", "5"))
                .orElseThrow();

        assertEquals(Material.MUSIC_DISC_5, request.material().material());
        assertEquals(1, request.requestedPage());
    }

    @Test
    void usesATrailingPositiveIntegerAsThePageOnlyAfterFullResolutionFails() {
        final ShopSearchRequest request = parser.parse(List.of("music_disc_5", "2"))
                .orElseThrow();

        assertEquals(Material.MUSIC_DISC_5, request.material().material());
        assertEquals("minecraft:music_disc_5", request.material().canonicalKey());
        assertEquals(2, request.requestedPage());
    }

    @Test
    void rejectsMissingMaterialsAndNonPositiveOrOverflowingPages() {
        assertTrue(parser.parse(List.of()).isEmpty());
        assertTrue(parser.parse(List.of("stone_bricks", "0")).isEmpty());
        assertTrue(parser.parse(List.of("stone_bricks", "-1")).isEmpty());
        assertTrue(parser.parse(List.of("stone_bricks", "999999999999999999999")).isEmpty());
        assertTrue(parser.parse(List.of("not_a_material", "2")).isEmpty());
    }
}
