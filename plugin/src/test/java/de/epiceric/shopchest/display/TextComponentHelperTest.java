package de.epiceric.shopchest.display;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TextComponentHelperTest {

    private static final PlainTextComponentSerializer PLAIN_TEXT =
            PlainTextComponentSerializer.plainText();

    @Test
    void buildsAHoverableRunCommandActionAfterTheSummary() {
        final Component message = TextComponentHelper.getClickableActionMessage(
                "Offline revenue: $25.00",
                "[View recent trades]",
                "Click to review transactions.",
                "/shops recent");

        assertEquals(
                "Offline revenue: $25.00 [View recent trades]",
                PLAIN_TEXT.serialize(message));

        final Component action = message.children().get(message.children().size() - 1);
        final ClickEvent<?> clickEvent = action.clickEvent();
        assertNotNull(clickEvent);
        assertEquals(ClickEvent.Action.RUN_COMMAND, clickEvent.action());
        assertEquals(
                "/shops recent",
                assertInstanceOf(ClickEvent.Payload.Text.class, clickEvent.payload()).value());

        final HoverEvent<?> hoverEvent = action.hoverEvent();
        assertNotNull(hoverEvent);
        assertEquals(
                "Click to review transactions.",
                PLAIN_TEXT.serialize((Component) hoverEvent.value()));
    }
}
