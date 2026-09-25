package de.epiceric.shopchest.command;

import de.epiceric.shopchest.catalog.ListingAvailability;
import de.epiceric.shopchest.catalog.ListingStock;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Builds availability-aware rows for {@code /shops search}. */
final class ShopSearchListingCard {

    static final TextColor VERIFIED = TextColor.color(0x7DDB9A);
    static final TextColor WARNING = TextColor.color(0xFFD166);
    static final TextColor PRIMARY = TextColor.color(0xF1F5F9);
    static final TextColor MUTED = TextColor.color(0x8796A5);
    static final TextColor DIVIDER = TextColor.color(0x355766);

    private ShopSearchListingCard() {
    }

    static List<Component> render(
            int bundleAmount,
            Component itemName,
            String formattedBundlePrice,
            String formattedUnitPrice,
            Component storefront,
            ListingStock stock
    ) {
        Objects.requireNonNull(itemName, "itemName");
        Objects.requireNonNull(formattedBundlePrice, "formattedBundlePrice");
        Objects.requireNonNull(formattedUnitPrice, "formattedUnitPrice");
        Objects.requireNonNull(storefront, "storefront");
        Objects.requireNonNull(stock, "stock");
        if (bundleAmount <= 0) {
            throw new IllegalArgumentException("bundleAmount must be positive");
        }
        if (stock.availability() != ListingAvailability.IN_STOCK
                && stock.availability() != ListingAvailability.UNCHECKED) {
            throw new IllegalArgumentException(
                    "Search cards support only in-stock or unchecked listings");
        }

        final boolean unchecked = stock.availability() == ListingAvailability.UNCHECKED;
        final TextColor accent = unchecked ? WARNING : VERIFIED;
        final List<Component> lines = new ArrayList<>(unchecked ? 3 : 2);
        lines.add(Component.text(unchecked ? "◇ " : "◆ ", accent)
                .append(Component.text(bundleAmount + "x ", PRIMARY))
                .append(itemName.color(PRIMARY))
                .append(Component.text(" for ", MUTED))
                .append(Component.text(
                        formattedBundlePrice,
                        accent,
                        TextDecoration.BOLD))
                .append(Component.text(
                        " (" + formattedUnitPrice + " each)",
                        MUTED)));

        Component status = Component.text("  ", MUTED)
                .append(storefront)
                .append(Component.text("  •  ", DIVIDER));
        if (unchecked) {
            status = status.append(Component.text(
                            "⚠ STOCK UNCHECKED",
                            WARNING,
                            TextDecoration.BOLD)
                    .hoverEvent(HoverEvent.showText(Component.text(
                            "The database confirms this offer and its terms, but the chest "
                                    + "cannot be inspected until its chunk is loaded.",
                            PRIMARY))));
        } else {
            status = status.append(Component.text(
                    "✓ " + stock.completeBundles() + " full "
                            + plural(stock.completeBundles(), "bundle", "bundles")
                            + " available",
                    VERIFIED));
        }
        lines.add(status);

        if (unchecked) {
            lines.add(Component.text(
                    "  ↳ Registered offer; visit to load its chunk and verify stock",
                    MUTED));
        }
        return List.copyOf(lines);
    }

    private static String plural(int amount, String singular, String plural) {
        return amount == 1 ? singular : plural;
    }
}
