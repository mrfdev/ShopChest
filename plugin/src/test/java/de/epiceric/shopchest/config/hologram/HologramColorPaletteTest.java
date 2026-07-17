package de.epiceric.shopchest.config.hologram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import de.epiceric.shopchest.config.Placeholder;
import de.epiceric.shopchest.config.hologram.line.FormattedLine;
import de.epiceric.shopchest.config.hologram.parser.FormatParser;
import org.junit.jupiter.api.Test;

class HologramColorPaletteTest {

    @Test
    void loadsDefaultsWhenExistingConfigDoesNotHavePaletteKeys() {
        final HologramColorPalette palette = HologramColorPalette.load(key -> null, warning -> {
            throw new AssertionError("Missing values should use defaults without warnings");
        });

        assertEquals(
                HologramColorPalette.toLegacyHex(HologramColorPalette.Role.OWNER.defaultHex()),
                palette.color(HologramColorPalette.Role.OWNER));
        assertEquals(
                HologramColorPalette.toLegacyHex(HologramColorPalette.Role.BUY_VALUE.defaultHex()),
                palette.color(HologramColorPalette.Role.BUY_VALUE));
    }

    @Test
    void acceptsCaseInsensitiveHexWithOrWithoutHash() {
        final Map<String, String> configured = Map.of(
                "owner", "#abcdef",
                "buy-value", "123456");

        final HologramColorPalette palette = HologramColorPalette.load(configured::get, warning -> {
            throw new AssertionError("Valid colors should not warn");
        });

        assertEquals("\u00A7x\u00A7a\u00A7b\u00A7c\u00A7d\u00A7e\u00A7f",
                palette.color(HologramColorPalette.Role.OWNER));
        assertEquals(0xABCDEF, palette.textColor(HologramColorPalette.Role.OWNER).value());
        assertEquals("\u00A7x\u00A71\u00A72\u00A73\u00A74\u00A75\u00A76",
                palette.color(HologramColorPalette.Role.BUY_VALUE));
    }

    @Test
    void fallsBackOnlyTheInvalidRole() {
        final List<String> warnings = new ArrayList<>();
        final HologramColorPalette palette = HologramColorPalette.load(
                key -> key.equals("item") ? "#12ZZ99" : null,
                warnings::add);

        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("hologram-colors.item"));
        assertEquals(
                HologramColorPalette.toLegacyHex(HologramColorPalette.Role.ITEM.defaultHex()),
                palette.color(HologramColorPalette.Role.ITEM));
    }

    @Test
    void upgradesOnlyTheBundledLegacyFormats() {
        assertEquals(
                "%COLOR-QUANTITY%%AMOUNT% x %COLOR-ITEM%%ITEMNAME%%COLOR-RESET%",
                HologramColorPalette.applyToLegacyDefault("%AMOUNT% x %ITEMNAME%"));
        assertEquals(
                "%COLOR-LABEL%Buy: %COLOR-BUY-VALUE%%BUY-PRICE% %COLOR-SEPARATOR%| "
                        + "%COLOR-LABEL%Sell: %COLOR-SELL-VALUE%%SELL-PRICE%%COLOR-RESET%",
                HologramColorPalette.applyToLegacyDefault("Buy %BUY-PRICE% | %SELL-PRICE% Sell"));
        assertEquals(
                "&6Special: %ITEMNAME%",
                HologramColorPalette.applyToLegacyDefault("&6Special: %ITEMNAME%"));
    }

    @Test
    void defaultTextColorsMeetNormalTextContrastAgainstPanelColor() {
        for (HologramColorPalette.Role role : HologramColorPalette.Role.values()) {
            assertTrue(
                    contrastRatio(role.defaultHex(), "#315B7D") >= 4.5,
                    role + " does not meet 4.5:1 contrast");
        }
    }

    @Test
    void leavesDirectPricesForTheVaultEconomyFormatter() {
        final FormattedLine<Placeholder> line = new HologramFormat(null).evaluateFormat(
                "%COLOR-LABEL%Buy: %COLOR-BUY-VALUE%%BUY-PRICE%",
                new FormatParser(),
                new FormatData());
        final Map<Placeholder, Object> values = new EnumMap<>(Placeholder.class);
        values.put(Placeholder.COLOR_LABEL, "<label>");
        values.put(Placeholder.COLOR_BUY_VALUE, "<value>");
        values.put(Placeholder.BUY_PRICE, 123.0);

        assertEquals("<label>Buy: <value>%BUY-PRICE%", line.get(values));
    }

    private static double contrastRatio(String first, String second) {
        final double firstLuminance = relativeLuminance(first);
        final double secondLuminance = relativeLuminance(second);
        return (Math.max(firstLuminance, secondLuminance) + 0.05)
                / (Math.min(firstLuminance, secondLuminance) + 0.05);
    }

    private static double relativeLuminance(String hexColor) {
        final int rgb = Integer.parseInt(hexColor.substring(1), 16);
        final double red = linearize((rgb >> 16) & 0xFF);
        final double green = linearize((rgb >> 8) & 0xFF);
        final double blue = linearize(rgb & 0xFF);
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private static double linearize(int channel) {
        final double value = channel / 255.0;
        return value <= 0.04045
                ? value / 12.92
                : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
