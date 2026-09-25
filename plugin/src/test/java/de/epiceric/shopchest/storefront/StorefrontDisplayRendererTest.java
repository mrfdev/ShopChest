package de.epiceric.shopchest.storefront;

import de.epiceric.shopchest.config.hologram.HologramColorPalette;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorefrontDisplayRendererTest {

    private static final UUID OWNER =
            UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final HologramColorPalette COLORS = HologramColorPalette.load(
            ignored -> null,
            ignored -> { });

    @Test
    void rendersProfileOwnerAndLiveShopStatistics() {
        final StorefrontProfile profile = new StorefrontProfile(
                OWNER,
                "Lion Forge",
                "Armor and tools",
                "A carefully stocked storefront near spawn.",
                "Follow the lantern path",
                false,
                false,
                1L);

        final String text = plain(StorefrontDisplayRenderer.render(
                profile,
                "JahLion",
                new StorefrontDisplayStats(4, 3, 2),
                "shops",
                COLORS));

        assertTrue(text.contains("Lion Forge"));
        assertTrue(text.contains("Owner: JahLion"));
        assertTrue(text.contains("Armor and tools"));
        assertTrue(text.contains("4 shops"));
        assertTrue(text.contains("3 selling"));
        assertTrue(text.contains("2 buying"));
        assertTrue(text.contains("Right-click:"));
        assertTrue(text.contains("/shops profile shopowner JahLion"));
    }

    @Test
    void hiddenProfileTextNeverLeaksIntoTheDisplay() {
        final StorefrontProfile profile = new StorefrontProfile(
                OWNER,
                "Secret Name",
                "Secret Tagline",
                "Secret Description",
                "Secret Directions",
                true,
                false,
                1L);

        final String text = plain(StorefrontDisplayRenderer.render(
                profile,
                "JahLion",
                new StorefrontDisplayStats(1, 1, 0),
                "shops",
                COLORS));

        assertTrue(text.contains("JahLion's Shops"));
        assertFalse(text.contains("Secret"));
    }

    @Test
    void wrapsAndBoundsLongDescriptionsForACompactInWorldPanel() {
        final StorefrontProfile profile = new StorefrontProfile(
                OWNER,
                null,
                null,
                "A very carefully curated collection of unusual blocks, tools, armor, food, and decorative items for builders.",
                null,
                false,
                false,
                1L);

        final String text = plain(StorefrontDisplayRenderer.render(
                profile,
                "JahLion",
                new StorefrontDisplayStats(1, 1, 1),
                "shops",
                COLORS));

        assertTrue(text.lines().count() <= StorefrontDisplayRenderer.MAX_PANEL_LINES);
        assertTrue(text.contains("..."));
    }

    @Test
    void wrapsAFullAdvertisementAcrossTwoLinesWithoutEarlyEllipsis() {
        final String advertisement = "Where to put it, oh right, SHULKER BOXES, "
                + "rare blocks, tools, armor, and food.";
        final StorefrontProfile profile = new StorefrontProfile(
                OWNER,
                "Lion's Den",
                advertisement,
                null,
                null,
                false,
                false,
                1L);

        final String text = plain(StorefrontDisplayRenderer.render(
                profile,
                "JahLion",
                new StorefrontDisplayStats(34, 34, 0),
                "shops",
                COLORS));
        final List<String> wrapped = StorefrontDisplayRenderer.wrap(
                advertisement, 48, 2);

        assertEquals(2, wrapped.size());
        assertFalse(wrapped.get(0).endsWith("..."));
        assertFalse(wrapped.get(1).endsWith("..."));
        assertEquals(advertisement, String.join(" ", wrapped));
        assertTrue(text.contains(wrapped.get(0) + "\n" + wrapped.get(1)));
        assertTrue(text.lines().count() <= StorefrontDisplayRenderer.MAX_PANEL_LINES);
    }

    private static String plain(net.kyori.adventure.text.Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
