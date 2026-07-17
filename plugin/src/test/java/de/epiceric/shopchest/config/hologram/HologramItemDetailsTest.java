package de.epiceric.shopchest.config.hologram;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HologramItemDetailsTest {

    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    @Test
    void placesSevenEntriesTwoPerLineWithoutDiscardingTheLastOne() {
        assertEquals(
                "Mending, Unbreaking III\nFortune III, Fire Protection V\n"
                        + "Silk Touch, Blast Protection VI\nInfinity",
                plain(HologramItemDetails.formatEntries(
                        entries(
                                "Mending",
                                "Unbreaking III",
                                "Fortune III",
                                "Fire Protection V",
                                "Silk Touch",
                                "Blast Protection VI",
                                "Infinity"),
                        7,
                        2,
                        NamedTextColor.GRAY,
                        hidden -> Component.text("+" + hidden + " more"))));
    }

    @Test
    void addsOverflowBesideTheSeventhEntry() {
        assertEquals(
                "One, Two\nThree, Four\nFive, Six\nSeven, +2 more",
                plain(HologramItemDetails.formatEntries(
                        entries("One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine"),
                        7,
                        2,
                        NamedTextColor.GRAY,
                        hidden -> Component.text("+" + hidden + " more"))));
    }

    @Test
    void formatsPotionDurationsForMinutesAndHours() {
        assertEquals("0:30", HologramItemDetails.formatDuration(600));
        assertEquals("3:00", HologramItemDetails.formatDuration(3600));
        assertEquals("1:02:03", HologramItemDetails.formatDuration(74460));
    }

    @Test
    void formatsCommonAndUnusuallyHighEffectLevels() {
        assertEquals("III", HologramItemDetails.toRomanNumeral(3));
        assertEquals("VII", HologramItemDetails.toRomanNumeral(7));
        assertEquals("4000", HologramItemDetails.toRomanNumeral(4000));
    }

    private static List<Component> entries(String... values) {
        return java.util.Arrays.stream(values).map(value -> (Component) Component.text(value)).toList();
    }

    private static String plain(Component component) {
        return PLAIN_TEXT.serialize(component);
    }
}
