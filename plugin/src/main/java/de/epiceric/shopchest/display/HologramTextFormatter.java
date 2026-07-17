package de.epiceric.shopchest.display;

import de.epiceric.shopchest.utils.LegacyColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.List;
import java.util.Map;

public final class HologramTextFormatter {

    private static final char COLOR_CHAR = '\u00A7';
    private static final String COLOR_CODES = "0123456789abcdefklmnorx";
    private static final String ELLIPSIS = "...";
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    private HologramTextFormatter() {
    }

    public static String sanitizeLine(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        final StringBuilder result = new StringBuilder(text.length());
        text.codePoints().forEach(codePoint -> {
            if (Character.isISOControl(codePoint) || codePoint == '\u2028' || codePoint == '\u2029') {
                result.append(' ');
            } else {
                result.appendCodePoint(codePoint);
            }
        });
        return result.toString();
    }

    public static String sanitizeItemName(String text, int maximumVisibleLength) {
        final String sanitized = collapseWhitespace(sanitizeLine(text)).strip();
        if (maximumVisibleLength <= 0 || visibleLength(sanitized) <= maximumVisibleLength) {
            return sanitized;
        }

        if (maximumVisibleLength <= ELLIPSIS.length()) {
            return ELLIPSIS.substring(0, maximumVisibleLength);
        }

        final int textLimit = maximumVisibleLength - ELLIPSIS.length();
        final StringBuilder result = new StringBuilder(sanitized.length());
        int visibleCharacters = 0;

        for (int offset = 0; offset < sanitized.length();) {
            final char character = sanitized.charAt(offset);
            if (character == COLOR_CHAR && offset + 1 < sanitized.length()
                    && isLegacyColorCode(sanitized.charAt(offset + 1))) {
                result.append(character).append(sanitized.charAt(offset + 1));
                offset += 2;
                continue;
            }

            if (visibleCharacters >= textLimit) {
                break;
            }

            final int codePoint = sanitized.codePointAt(offset);
            result.appendCodePoint(codePoint);
            offset += Character.charCount(codePoint);
            visibleCharacters++;
        }

        return result.toString().stripTrailing() + ELLIPSIS;
    }

    public static Component sanitizeItemName(Component component, int maximumVisibleLength) {
        if (component == null || component.equals(Component.empty())) {
            return Component.empty();
        }
        if (component instanceof TranslatableComponent) {
            return component;
        }
        return fromLegacy(sanitizeItemName(toLegacy(component), maximumVisibleLength));
    }

    public static String toPanelText(List<String> lines) {
        final StringBuilder panel = new StringBuilder();
        for (String line : lines) {
            final String sanitized = sanitizeLine(line);
            if (sanitized.isEmpty()) {
                continue;
            }
            if (!panel.isEmpty()) {
                panel.append(COLOR_CHAR).append('r').append('\n');
            }
            panel.append(sanitized);
        }
        return panel.toString();
    }

    public static Component toPanelComponent(List<Component> lines) {
        Component panel = Component.empty();
        boolean hasLine = false;
        for (Component line : lines) {
            if (line == null || line.equals(Component.empty())) {
                continue;
            }
            if (hasLine) {
                panel = panel.append(Component.newline());
            }
            panel = panel.append(line);
            hasLine = true;
        }
        return panel;
    }

    public static Component fromLegacy(String text) {
        return LEGACY_SERIALIZER.deserialize(LegacyColorUtils.translateAlternateColorCodes(
                '&', sanitizeLine(text)));
    }

    public static String toLegacy(Component component) {
        return LEGACY_SERIALIZER.serialize(component == null ? Component.empty() : component);
    }

    public static Component replaceComponents(String legacyText, Map<String, Component> replacements) {
        final String text = sanitizeLine(legacyText);
        Component result = Component.empty();
        int cursor = 0;

        while (cursor < text.length()) {
            String nextToken = null;
            int nextIndex = text.length();
            for (String token : replacements.keySet()) {
                final int tokenIndex = text.indexOf(token, cursor);
                if (tokenIndex >= 0 && tokenIndex < nextIndex) {
                    nextToken = token;
                    nextIndex = tokenIndex;
                }
            }

            if (nextToken == null) {
                result = result.append(fromLegacy(text.substring(cursor)));
                break;
            }
            if (nextIndex > cursor) {
                result = result.append(fromLegacy(text.substring(cursor, nextIndex)));
            }
            result = result.append(replacements.getOrDefault(nextToken, Component.empty()));
            cursor = nextIndex + nextToken.length();
        }

        return result;
    }

    static int visibleLength(String text) {
        int length = 0;
        for (int offset = 0; offset < text.length();) {
            if (text.charAt(offset) == COLOR_CHAR && offset + 1 < text.length()
                    && isLegacyColorCode(text.charAt(offset + 1))) {
                offset += 2;
                continue;
            }
            final int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            length++;
        }
        return length;
    }

    private static String collapseWhitespace(String text) {
        final StringBuilder result = new StringBuilder(text.length());
        boolean pendingSpace = false;
        boolean hasVisibleContent = false;

        for (int offset = 0; offset < text.length();) {
            if (text.charAt(offset) == COLOR_CHAR && offset + 1 < text.length()
                    && isLegacyColorCode(text.charAt(offset + 1))) {
                result.append(text.charAt(offset)).append(text.charAt(offset + 1));
                offset += 2;
                continue;
            }

            final int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (Character.isWhitespace(codePoint)) {
                pendingSpace = hasVisibleContent;
                continue;
            }
            if (pendingSpace) {
                result.append(' ');
                pendingSpace = false;
            }
            result.appendCodePoint(codePoint);
            hasVisibleContent = true;
        }
        return result.toString();
    }

    private static boolean isLegacyColorCode(char character) {
        return COLOR_CODES.indexOf(Character.toLowerCase(character)) >= 0;
    }
}
