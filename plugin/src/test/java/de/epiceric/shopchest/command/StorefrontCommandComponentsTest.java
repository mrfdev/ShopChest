package de.epiceric.shopchest.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StorefrontCommandComponentsTest {

    @Test
    void shopListingHoverShowsTheUniqueShopIdWithoutChangingTheText() {
        final Component listing = StorefrontCommandComponents.withShopIdHover(
                Component.text("5x Enchanted Book"),
                31);

        assertEquals(
                "5x Enchanted Book",
                PlainTextComponentSerializer.plainText().serialize(listing));
        final HoverEvent<?> hoverEvent = listing.hoverEvent();
        assertNotNull(hoverEvent);
        assertEquals(HoverEvent.Action.SHOW_TEXT, hoverEvent.action());
        assertEquals(
                "Unique shop ID: #31",
                PlainTextComponentSerializer.plainText().serialize(
                        assertInstanceOf(Component.class, hoverEvent.value())));
    }

    @Test
    void featuredUsageLineOpensTheOwnersShopIdPicker() {
        final Component prompt = StorefrontCommandComponents.featuredPickerPrompt("shops");

        assertEquals(
                "/shops profile featured add <shop-id>",
                PlainTextComponentSerializer.plainText().serialize(prompt));
        assertRunCommand(prompt, "/shops profile shops 1");
        assertNotNull(prompt.hoverEvent());
    }

    @Test
    void featuredListingActionsCarryTheExactValidatedShopId() {
        assertRunCommand(
                StorefrontCommandComponents.addFeaturedAction("shops", 31),
                "/shops profile featured add 31");
        assertRunCommand(
                StorefrontCommandComponents.removeFeaturedAction("shops", 31),
                "/shops profile featured remove 31");
    }

    private static void assertRunCommand(Component component, String expectedCommand) {
        final ClickEvent<?> clickEvent = component.clickEvent();
        assertNotNull(clickEvent);
        assertEquals(ClickEvent.Action.RUN_COMMAND, clickEvent.action());
        assertEquals(
                expectedCommand,
                assertInstanceOf(
                        ClickEvent.Payload.Text.class,
                        clickEvent.payload()).value());
    }
}
