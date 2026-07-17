package de.epiceric.shopchest.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class HologramTextFormatterTest {

    @Test
    void joinsLinesIntoOnePanelAndResetsFormatting() {
        assertEquals(
                "\u00A7aVendor\u00A7r\n16 x Glass\u00A7r\nBuy 10 | 5 Sell",
                HologramTextFormatter.toPanelText(List.of(
                        "\u00A7aVendor",
                        "16 x Glass",
                        "Buy 10 | 5 Sell")));
    }

    @Test
    void replacesInjectedLineBreaksAndControlCharacters() {
        assertEquals(
                "A renamed item with extra lines",
                HologramTextFormatter.sanitizeItemName(" A renamed\nitem\twith\u0000extra   lines ", 0));
    }

    @Test
    void truncatesVisibleCharactersWithoutCountingLegacyColors() {
        assertEquals(
                "\u00A7aThe best quali...",
                HologramTextFormatter.sanitizeItemName("\u00A7aThe best quality you can imagine", 17));
    }

    @Test
    void removesWhitespaceBeforeColoredText() {
        assertEquals(
                "\u00A7aColored name",
                HologramTextFormatter.sanitizeItemName("\u00A7a   Colored name", 48));
    }

    @Test
    void doesNotSplitUnicodeCodePoints() {
        assertEquals(
                "AB\uD83E\uDE9F...",
                HologramTextFormatter.sanitizeItemName("AB\uD83E\uDE9F colorful renamed glass", 6));
    }

    @Test
    void zeroDisablesTheVisibleLengthLimit() {
        assertEquals(
                "A very long item name",
                HologramTextFormatter.sanitizeItemName("A very long item name", 0));
    }

    @Test
    void insertsRichComponentsWithoutFlatteningThePanelLayout() {
        final Component result = HologramTextFormatter.replaceComponents(
                "Details: %ITEM-DETAILS%",
                java.util.Map.of("%ITEM-DETAILS%", Component.text("Fortune III\nMending")));

        assertEquals(
                "Details: Fortune III\nMending",
                PlainTextComponentSerializer.plainText().serialize(result));
    }

    @Test
    void preservesVanillaTranslationComponentsForClientSideRendering() {
        final TranslatableComponent translated = Component
                .translatable("item.minecraft.golden_dandelion")
                .fallback("Golden Dandelion");

        final TranslatableComponent sanitized = assertInstanceOf(
                TranslatableComponent.class,
                HologramTextFormatter.sanitizeItemName(translated, 12));

        assertEquals("item.minecraft.golden_dandelion", sanitized.key());
        assertEquals("Golden Dandelion", sanitized.fallback());
    }
}
