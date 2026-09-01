package de.epiceric.shopchest.display;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

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

    @Test
    void addsTheExactItemTooltipToVariantDependentListingNames() {
        for (Material material : List.of(
                Material.ENCHANTED_BOOK,
                Material.POTION,
                Material.SPLASH_POTION,
                Material.LINGERING_POTION)) {
            final TooltipItemStack itemStack = new TooltipItemStack(material);

            final Component itemName = TextComponentHelper.withDetailedItemTooltip(
                    Component.text("Generic item name"),
                    itemStack);

            assertEquals("Generic item name", PLAIN_TEXT.serialize(itemName));
            final HoverEvent<?> hoverEvent = itemName.hoverEvent();
            assertNotNull(hoverEvent, material.name());
            assertEquals(HoverEvent.Action.SHOW_ITEM, hoverEvent.action(), material.name());
            assertSame(itemStack.tooltip.value(), hoverEvent.value(), material.name());
        }
    }

    @Test
    void leavesOrdinaryListingNamesWithoutAnItemTooltip() {
        final Component original = Component.text("Stone bricks");

        final Component itemName = TextComponentHelper.withDetailedItemTooltip(
                original,
                new TooltipItemStack(Material.STONE_BRICKS));

        assertSame(original, itemName);
        assertNull(itemName.hoverEvent());
    }

    /** Registry-free ItemStack boundary fake for component tests. */
    private static final class TooltipItemStack extends ItemStack {

        private final Material type;
        private final HoverEvent<HoverEvent.ShowItem> tooltip;

        private TooltipItemStack(Material type) {
            this.type = type;
            this.tooltip = HoverEvent.showItem(
                    Key.key("minecraft", type.name().toLowerCase(Locale.ROOT)),
                    1);
        }

        @Override
        public Material getType() {
            return type;
        }

        @Override
        public HoverEvent<HoverEvent.ShowItem> asHoverEvent(
                UnaryOperator<HoverEvent.ShowItem> operator
        ) {
            return HoverEvent.showItem(operator.apply(tooltip.value()));
        }
    }
}
