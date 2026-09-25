package de.epiceric.shopchest.command;

import de.epiceric.shopchest.catalog.ListingAvailability;
import de.epiceric.shopchest.catalog.ListingStock;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopSearchListingCardTest {

    private static final PlainTextComponentSerializer PLAIN =
            PlainTextComponentSerializer.plainText();

    @Test
    void rendersVerifiedStockAsAvailable() {
        final List<Component> lines = ShopSearchListingCard.render(
                16,
                Component.text("Blaze Rod"),
                "$80.00",
                "$5.00",
                Component.text("The Nether Shop"),
                new ListingStock(ListingAvailability.IN_STOCK, 48, 3));

        assertEquals(2, lines.size());
        assertEquals("◆ 16x Blaze Rod for $80.00 ($5.00 each)", PLAIN.serialize(lines.get(0)));
        assertEquals("  The Nether Shop  •  ✓ 3 full bundles available",
                PLAIN.serialize(lines.get(1)));
        assertEquals(ShopSearchListingCard.VERIFIED, lines.get(0).color());
    }

    @Test
    void makesAnUncheckedDatabaseOfferExplicitAndActionable() {
        final Component storefront = Component.text("The Nether Shop")
                .clickEvent(ClickEvent.runCommand("/shops profile shopowner seller"));
        final List<Component> lines = ShopSearchListingCard.render(
                16,
                Component.text("Blaze Rod"),
                "$80.00",
                "$5.00",
                storefront,
                ListingStock.unchecked());

        assertEquals(3, lines.size());
        assertEquals("◇ 16x Blaze Rod for $80.00 ($5.00 each)", PLAIN.serialize(lines.get(0)));
        assertEquals("  The Nether Shop  •  ⚠ STOCK UNCHECKED", PLAIN.serialize(lines.get(1)));
        assertEquals("  ↳ Registered offer; visit to load its chunk and verify stock",
                PLAIN.serialize(lines.get(2)));
        assertEquals(ShopSearchListingCard.WARNING, lines.get(0).color());
        assertNotNull(lines.get(1).children().get(2).hoverEvent());
        assertTrue(hasClickCommand(lines.get(1), "/shops profile shopowner seller"));
    }

    private static boolean hasClickCommand(Component component, String command) {
        final ClickEvent click = component.clickEvent();
        if (click != null
                && click.payload() instanceof ClickEvent.Payload.Text text
                && text.value().equals(command)) {
            return true;
        }
        return component.children().stream()
                .anyMatch(child -> hasClickCommand(child, command));
    }
}
