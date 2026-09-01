package de.epiceric.shopchest.display;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextComponentHelper {

    public static final LegacyComponentSerializer LEGACY_COMPONENT_SERIALIZER =
            LegacyComponentSerializer.legacySection();
    private static final Set<Material> DETAILED_ITEM_TOOLTIP_TYPES = EnumSet.of(
            Material.ENCHANTED_BOOK,
            Material.POTION,
            Material.SPLASH_POTION,
            Material.LINGERING_POTION);

    private TextComponentHelper() {
    }

    public static Component getClickableActionMessage(
            String summary,
            String actionLabel,
            String hoverText,
            String command
    ) {
        return LEGACY_COMPONENT_SERIALIZER.deserialize(summary)
                .append(Component.space())
                .append(LEGACY_COMPONENT_SERIALIZER.deserialize(actionLabel)
                        .hoverEvent(LEGACY_COMPONENT_SERIALIZER.deserialize(hoverText))
                        .clickEvent(ClickEvent.runCommand(command)));
    }

    /**
     * Adds Minecraft's authoritative item tooltip where a generic listing name
     * would otherwise hide the variant details players need to distinguish it.
     */
    public static Component withDetailedItemTooltip(Component itemName, ItemStack itemStack) {
        Objects.requireNonNull(itemName, "itemName");
        Objects.requireNonNull(itemStack, "itemStack");
        if (!DETAILED_ITEM_TOOLTIP_TYPES.contains(itemStack.getType())) {
            return itemName;
        }
        return itemName.hoverEvent(itemStack.asHoverEvent());
    }

    public static Consumer<Player> getSendableItemInfo(
            String message,
            String itemPlaceholder,
            ItemStack itemStack,
            String productName
    ) {
        return getSendableItemInfo(
                message,
                itemPlaceholder,
                itemStack,
                LEGACY_COMPONENT_SERIALIZER.deserialize(productName));
    }

    public static Consumer<Player> getSendableItemInfo(
            String message,
            String itemPlaceholder,
            ItemStack itemStack,
            Component productName
    ) {
        final Component replacement = productName.hoverEvent(itemStack.asHoverEvent());
        Component component = Component.empty();
        final Matcher matcher = Pattern.compile(itemPlaceholder, Pattern.LITERAL).matcher(message);
        if (matcher.find()) {
            int cursor = 0;
            do {
                final String prefix = message.substring(cursor, matcher.start());
                if (!prefix.isEmpty()) {
                    component = component.append(LEGACY_COMPONENT_SERIALIZER.deserialize(prefix));
                }
                component = component.append(replacement);
                cursor = matcher.end();
            } while (matcher.find());
            final String suffix = message.substring(cursor);
            if (!suffix.isEmpty()) {
                component = component.append(LEGACY_COMPONENT_SERIALIZER.deserialize(suffix));
            }
        } else {
            component = LEGACY_COMPONENT_SERIALIZER.deserialize(message);
        }
        final Component sendableComponent = component;
        return player -> player.sendMessage(sendableComponent);
    }
}
