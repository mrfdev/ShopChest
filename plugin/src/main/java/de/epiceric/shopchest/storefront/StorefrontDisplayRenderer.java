package de.epiceric.shopchest.storefront;

import de.epiceric.shopchest.config.hologram.HologramColorPalette;
import de.epiceric.shopchest.display.HologramTextFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Creates the compact, bounded text panel shown over a Storefront Display. */
public final class StorefrontDisplayRenderer {

    public static final int MAX_PANEL_LINES = 8;
    private static final int MAX_LINE_CHARACTERS = 48;
    private static final int MAX_TAGLINE_LINES = 2;
    private static final int MAX_DESCRIPTION_LINES = 2;
    private static final String ELLIPSIS = "...";

    private StorefrontDisplayRenderer() {
    }

    public static Component render(
            StorefrontProfile profile,
            String ownerName,
            StorefrontDisplayStats stats,
            String commandName,
            HologramColorPalette colors
    ) {
        return renderPanel(profile, ownerName, stats, commandName, colors).text();
    }

    static RenderedPanel renderPanel(
            StorefrontProfile profile,
            String ownerName,
            StorefrontDisplayStats stats,
            String commandName,
            HologramColorPalette colors
    ) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(colors, "colors");
        final String safeOwner = boundedLine(ownerName, 32);
        final String safeCommand = boundedLine(commandName, 32);
        final String title = profile.textHidden() || profile.name() == null
                ? possessive(safeOwner) + " Shops"
                : boundedLine(profile.name(), MAX_LINE_CHARACTERS);

        final List<Component> lines = new ArrayList<>(MAX_PANEL_LINES);
        lines.add(Component.text(title, colors.textColor(HologramColorPalette.Role.OWNER))
                .decorate(TextDecoration.BOLD));
        lines.add(Component.text("Owner: ", colors.textColor(HologramColorPalette.Role.LABEL))
                .append(Component.text(
                        safeOwner,
                        colors.textColor(HologramColorPalette.Role.OWNER))));

        if (!profile.textHidden()) {
            for (String taglineLine : wrap(
                    profile.tagline(), MAX_LINE_CHARACTERS, MAX_TAGLINE_LINES)) {
                lines.add(Component.text(
                        taglineLine,
                        colors.textColor(HologramColorPalette.Role.DETAILS)));
            }
            for (String descriptionLine : wrap(
                    profile.description(), MAX_LINE_CHARACTERS, MAX_DESCRIPTION_LINES)) {
                lines.add(Component.text(
                        descriptionLine,
                        colors.textColor(HologramColorPalette.Role.DETAILS)));
            }
            if (profile.directions() != null && lines.size() < MAX_PANEL_LINES - 2) {
                lines.add(Component.text("Find us: ", colors.textColor(HologramColorPalette.Role.LABEL))
                        .append(Component.text(
                                boundedLine(profile.directions(), MAX_LINE_CHARACTERS - 9),
                                colors.textColor(HologramColorPalette.Role.DETAILS))));
            }
        }

        while (lines.size() > MAX_PANEL_LINES - 2) {
            lines.remove(lines.size() - 1);
        }
        lines.add(statsLine(stats, colors));
        lines.add(Component.text(
                "Right-click: /" + safeCommand + " profile shopowner " + safeOwner,
                colors.textColor(HologramColorPalette.Role.SEPARATOR)));
        return new RenderedPanel(HologramTextFormatter.toPanelComponent(lines), lines.size());
    }

    static List<String> wrap(String value, int maximumCharacters, int maximumLines) {
        if (value == null || value.isBlank() || maximumCharacters <= 0 || maximumLines <= 0) {
            return List.of();
        }
        final String normalized = HologramTextFormatter.sanitizeLine(value)
                .strip()
                .replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return List.of();
        }

        final List<String> lines = new ArrayList<>(maximumLines);
        int cursor = 0;
        while (cursor < normalized.length() && lines.size() < maximumLines) {
            final int remaining = normalized.codePointCount(cursor, normalized.length());
            if (remaining <= maximumCharacters) {
                lines.add(normalized.substring(cursor));
                cursor = normalized.length();
                break;
            }
            int end = normalized.offsetByCodePoints(cursor, maximumCharacters);
            final int whitespace = normalized.lastIndexOf(' ', end);
            if (whitespace > cursor) {
                end = whitespace;
            }
            lines.add(normalized.substring(cursor, end).strip());
            cursor = end;
            while (cursor < normalized.length() && normalized.charAt(cursor) == ' ') {
                cursor++;
            }
        }
        if (cursor < normalized.length() && !lines.isEmpty()) {
            final int last = lines.size() - 1;
            lines.set(last, ellipsize(lines.get(last), maximumCharacters));
        }
        return List.copyOf(lines);
    }

    private static Component statsLine(
            StorefrontDisplayStats stats,
            HologramColorPalette colors
    ) {
        return Component.text(
                        stats.shops() + " " + plural(stats.shops(), "shop", "shops"),
                        colors.textColor(HologramColorPalette.Role.LABEL))
                .append(Component.text(" | ", colors.textColor(HologramColorPalette.Role.SEPARATOR)))
                .append(Component.text(
                        stats.selling() + " selling",
                        colors.textColor(HologramColorPalette.Role.BUY_VALUE)))
                .append(Component.text(" | ", colors.textColor(HologramColorPalette.Role.SEPARATOR)))
                .append(Component.text(
                        stats.buying() + " buying",
                        colors.textColor(HologramColorPalette.Role.SELL_VALUE)));
    }

    private static String boundedLine(String value, int maximumCharacters) {
        final String normalized = value == null
                ? "Unknown"
                : HologramTextFormatter.sanitizeLine(value).strip().replaceAll("\\s+", " ");
        final String fallback = normalized.isEmpty() ? "Unknown" : normalized;
        if (fallback.codePointCount(0, fallback.length()) <= maximumCharacters) {
            return fallback;
        }
        return ellipsize(fallback, maximumCharacters);
    }

    private static String ellipsize(String value, int maximumCharacters) {
        if (maximumCharacters <= ELLIPSIS.length()) {
            return ELLIPSIS.substring(0, maximumCharacters);
        }
        final int limit = maximumCharacters - ELLIPSIS.length();
        final int codePoints = value.codePointCount(0, value.length());
        final int end = value.offsetByCodePoints(0, Math.min(codePoints, limit));
        return value.substring(0, end).stripTrailing() + ELLIPSIS;
    }

    private static String possessive(String ownerName) {
        return ownerName.endsWith("s") ? ownerName + "'" : ownerName + "'s";
    }

    private static String plural(int count, String singular, String plural) {
        return count == 1 ? singular : plural;
    }

    record RenderedPanel(Component text, int lineCount) {
    }
}
