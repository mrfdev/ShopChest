package de.epiceric.shopchest.config.hologram;

import net.kyori.adventure.text.format.TextColor;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public final class HologramColorPalette {

    public static final String CONFIG_PREFIX = "hologram-colors.";
    public static final String RESET = "\u00A7r";

    private final Map<Role, String> legacyColors;

    private HologramColorPalette(Map<Role, String> legacyColors) {
        this.legacyColors = new EnumMap<>(legacyColors);
    }

    public static HologramColorPalette load(
            Function<String, String> configuredColorProvider,
            Consumer<String> warningLogger
    ) {
        final Map<Role, String> colors = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            final String configuredColor = configuredColorProvider.apply(role.configKey());
            final String selectedColor;
            if (configuredColor == null || configuredColor.isBlank()) {
                selectedColor = role.defaultHex();
            } else if (isHexColor(configuredColor)) {
                selectedColor = normalizeHex(configuredColor);
            } else {
                warningLogger.accept("Invalid " + CONFIG_PREFIX + role.configKey() + " color '"
                        + configuredColor + "'. Using " + role.defaultHex() + ".");
                selectedColor = role.defaultHex();
            }
            colors.put(role, toLegacyHex(selectedColor));
        }
        return new HologramColorPalette(colors);
    }

    public String color(Role role) {
        return legacyColors.get(role);
    }

    public TextColor textColor(Role role) {
        return TextColor.color(Integer.parseInt(roleHex(role).substring(1), 16));
    }

    public static boolean isHexColor(String value) {
        return value != null && value.strip().matches("#?[0-9a-fA-F]{6}");
    }

    public static String toLegacyHex(String value) {
        final String hex = normalizeHex(value).substring(1).toLowerCase(Locale.ROOT);
        final StringBuilder legacy = new StringBuilder("\u00A7x");
        for (int i = 0; i < hex.length(); i++) {
            legacy.append('\u00A7').append(hex.charAt(i));
        }
        return legacy.toString();
    }

    public static String applyToLegacyDefault(String format) {
        return switch (format) {
            case "%VENDOR%" ->
                    "%COLOR-OWNER%%VENDOR%%COLOR-RESET%";
            case "&cAdmin Shop" ->
                    "%COLOR-ADMIN%Admin Shop%COLOR-RESET%";
            case "%AMOUNT% x %ITEMNAME%" ->
                    "%COLOR-QUANTITY%%AMOUNT% x %COLOR-ITEM%%ITEMNAME%%COLOR-RESET%";
            case "Buy %BUY-PRICE% | %SELL-PRICE% Sell" ->
                    "%COLOR-LABEL%Buy: %COLOR-BUY-VALUE%%BUY-PRICE% %COLOR-SEPARATOR%| "
                            + "%COLOR-LABEL%Sell: %COLOR-SELL-VALUE%%SELL-PRICE%%COLOR-RESET%";
            case "Buy %BUY-PRICE%" ->
                    "%COLOR-LABEL%Buy: %COLOR-BUY-VALUE%%BUY-PRICE%%COLOR-RESET%";
            case "Sell %SELL-PRICE%" ->
                    "%COLOR-LABEL%Sell: %COLOR-SELL-VALUE%%SELL-PRICE%%COLOR-RESET%";
            default -> format;
        };
    }

    private static String normalizeHex(String value) {
        final String stripped = value.strip();
        return (stripped.startsWith("#") ? stripped : "#" + stripped).toUpperCase(Locale.ROOT);
    }

    private String roleHex(Role role) {
        final String legacy = legacyColors.get(role);
        final StringBuilder hex = new StringBuilder("#");
        for (int index = 3; index < legacy.length(); index += 2) {
            hex.append(legacy.charAt(index));
        }
        return hex.toString();
    }

    public enum Role {
        OWNER("owner", "#DCE7FF"),
        QUANTITY("quantity", "#D8E1EA"),
        ITEM("item", "#FFE29A"),
        DETAILS("details", "#D8CCFF"),
        LABEL("label", "#C7D8E5"),
        BUY_VALUE("buy-value", "#B8EBCB"),
        SELL_VALUE("sell-value", "#FFC9B8"),
        SEPARATOR("separator", "#BDD0DE"),
        ADMIN("admin", "#FFC2CF"),
        UNAVAILABLE("unavailable", "#FFD0D0");

        private final String configKey;
        private final String defaultHex;

        Role(String configKey, String defaultHex) {
            this.configKey = configKey;
            this.defaultHex = defaultHex;
        }

        public String configKey() {
            return configKey;
        }

        public String defaultHex() {
            return defaultHex;
        }
    }
}
