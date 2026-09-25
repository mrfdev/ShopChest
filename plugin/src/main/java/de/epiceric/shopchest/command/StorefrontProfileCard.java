package de.epiceric.shopchest.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Builds the compact, interactive chat card used by {@code /shops profile}. */
final class StorefrontProfileCard {

    static final TextColor ACCENT = TextColor.color(0x55E6E6);
    static final TextColor TITLE = TextColor.color(0xFFB84D);
    static final TextColor PRIMARY = TextColor.color(0xF1F5F9);
    static final TextColor LABEL = TextColor.color(0x9FB3C8);
    static final TextColor MUTED = TextColor.color(0x71869B);
    static final TextColor DIVIDER = TextColor.color(0x355766);
    static final TextColor SUCCESS = TextColor.color(0x7DDB9A);
    static final TextColor WARNING = TextColor.color(0xFFD166);
    static final TextColor DANGER = TextColor.color(0xFF7A85);

    private static final String RULE = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";

    private StorefrontProfileCard() {
    }

    static List<Component> render(
            Content content,
            Metrics metrics,
            String commandName,
            UUID ownerId,
            boolean marketplaceLocation
    ) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(commandName, "commandName");
        Objects.requireNonNull(ownerId, "ownerId");

        final List<Component> lines = new ArrayList<>();
        lines.add(sectionRule("STOREFRONT"));
        lines.add(Component.text("◆ ", TITLE)
                .append(Component.text(
                        content.storefrontName(), TITLE, TextDecoration.BOLD)));
        lines.add(labelValue("OWNER", content.ownerName()));

        if (content.textHidden()) {
            lines.add(Component.text("! ", WARNING, TextDecoration.BOLD)
                    .append(Component.text(
                            "Storefront details are temporarily hidden by staff.",
                            WARNING).decoration(TextDecoration.BOLD, false)));
        } else {
            if (hasText(content.tagline())) {
                lines.add(Component.text("✦ ", ACCENT)
                        .append(Component.text(content.tagline(), PRIMARY)));
            }
            if (hasText(content.description())) {
                lines.add(labelValue("ABOUT", content.description()));
            }
            if (hasText(content.directions())) {
                lines.add(labelValue("FIND US", content.directions()));
            }
        }

        lines.add(Component.empty());
        lines.add(sectionRule("SHOP STATUS"));
        lines.add(shopSummary(metrics));
        if (metrics.customerBuyOffers() > 0) {
            lines.add(stockSummary(metrics));
        }
        if (metrics.customerSellOffers() > 0) {
            lines.add(capacitySummary(metrics));
        }

        lines.add(Component.empty());
        lines.add(actions(metrics.totalShops(), commandName, ownerId, marketplaceLocation));
        lines.add(Component.text(RULE, DIVIDER));
        return List.copyOf(lines);
    }

    private static Component sectionRule(String section) {
        return Component.text("━━ ", DIVIDER)
                .append(Component.text(section, ACCENT, TextDecoration.BOLD))
                .append(Component.text(" ━━━━━━━━━━━━━━━━━━━━━━━", DIVIDER));
    }

    private static Component labelValue(String label, String value) {
        return Component.text(label + "  ", LABEL, TextDecoration.BOLD)
                .append(Component.text(value, PRIMARY)
                        .decoration(TextDecoration.BOLD, false));
    }

    private static Component shopSummary(Metrics metrics) {
        return rowLabel("SHOPS")
                .append(metric(metrics.totalShops(), "total", PRIMARY))
                .append(separator())
                .append(metric(metrics.customerBuyOffers(), "selling", SUCCESS))
                .append(separator())
                .append(metric(metrics.customerSellOffers(), "buying", ACCENT));
    }

    private static Component stockSummary(Metrics metrics) {
        Component line = rowLabel("STOCK")
                .append(metric(metrics.inStock(), "ready", SUCCESS));
        if (metrics.outOfStock() > 0) {
            line = line.append(separator())
                    .append(metric(metrics.outOfStock(), "empty", DANGER));
        }
        if (metrics.uncheckedStock() > 0) {
            line = line.append(separator())
                    .append(metric(metrics.uncheckedStock(), "unchecked", WARNING));
        }
        if (metrics.unavailableStock() > 0) {
            line = line.append(separator())
                    .append(metric(metrics.unavailableStock(), "unavailable", MUTED));
        }
        return line;
    }

    private static Component capacitySummary(Metrics metrics) {
        Component line = rowLabel("SPACE")
                .append(metric(metrics.canAccept(), "ready", SUCCESS));
        if (metrics.full() > 0) {
            line = line.append(separator())
                    .append(metric(metrics.full(), "full", DANGER));
        }
        if (metrics.uncheckedCapacity() > 0) {
            line = line.append(separator())
                    .append(metric(metrics.uncheckedCapacity(), "unchecked", WARNING));
        }
        if (metrics.unavailableCapacity() > 0) {
            line = line.append(separator())
                    .append(metric(metrics.unavailableCapacity(), "unavailable", MUTED));
        }
        return line;
    }

    private static Component rowLabel(String label) {
        return Component.text(label + "  ", LABEL, TextDecoration.BOLD);
    }

    private static Component metric(long value, String description, TextColor valueColor) {
        return Component.text(Long.toString(value), valueColor, TextDecoration.BOLD)
                .append(Component.text(" " + description, MUTED)
                        .decoration(TextDecoration.BOLD, false));
    }

    private static Component separator() {
        return Component.text("  •  ", DIVIDER);
    }

    private static Component actions(
            long totalShops,
            String commandName,
            UUID ownerId,
            boolean marketplaceLocation
    ) {
        final Component browse = button("BROWSE " + totalShops + " SHOPS", ACCENT)
                .hoverEvent(HoverEvent.showText(Component.text(
                        "Show four shop listings per page", PRIMARY)))
                .clickEvent(ClickEvent.runCommand(
                        "/" + commandName + " profile shopowner "
                                + ownerId + " shops 1"));
        if (!marketplaceLocation) {
            return browse;
        }
        final Component visit = button("VISIT MARKETPLACE", TITLE)
                .hoverEvent(HoverEvent.showText(Component.text(
                        "Travel to the public shop marketplace", PRIMARY)))
                .clickEvent(ClickEvent.runCommand("/warp shops"));
        return browse.append(Component.text("  ", DIVIDER)).append(visit);
    }

    private static Component button(String label, TextColor color) {
        return Component.text("[ ", MUTED)
                .append(Component.text(label, color, TextDecoration.BOLD))
                .append(Component.text(" ]", MUTED));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    record Content(
            String storefrontName,
            String ownerName,
            String tagline,
            String description,
            String directions,
            boolean textHidden
    ) {
        Content {
            Objects.requireNonNull(storefrontName, "storefrontName");
            Objects.requireNonNull(ownerName, "ownerName");
        }
    }

    record Metrics(
            long totalShops,
            long customerBuyOffers,
            long customerSellOffers,
            long inStock,
            long outOfStock,
            long uncheckedStock,
            long unavailableStock,
            long canAccept,
            long full,
            long uncheckedCapacity,
            long unavailableCapacity
    ) {
    }
}
