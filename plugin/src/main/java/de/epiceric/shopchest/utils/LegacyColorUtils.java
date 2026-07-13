package de.epiceric.shopchest.utils;

public final class LegacyColorUtils {

    private static final char COLOR_CHAR = '\u00A7';
    private static final String COLOR_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";

    private LegacyColorUtils() {
    }

    public static String translateAlternateColorCodes(char alternateColorChar, String text) {
        if (text == null) {
            return null;
        }

        final char[] characters = text.toCharArray();
        for (int i = 0; i < characters.length - 1; i++) {
            if (characters[i] == alternateColorChar && COLOR_CODES.indexOf(characters[i + 1]) >= 0) {
                characters[i] = COLOR_CHAR;
                characters[i + 1] = Character.toLowerCase(characters[i + 1]);
            }
        }
        return new String(characters);
    }

    public static String color(char colorCode, String text) {
        return COLOR_CHAR + String.valueOf(Character.toLowerCase(colorCode)) + text;
    }
}
