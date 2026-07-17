package de.epiceric.shopchest.display;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextComponentHelper {

    public static final LegacyComponentSerializer LEGACY_COMPONENT_SERIALIZER =
            LegacyComponentSerializer.legacySection();

    private TextComponentHelper() {
    }

    public static Consumer<Player> getSendableItemInfo(
            String message,
            String itemPlaceholder,
            ItemStack itemStack,
            String productName
    ) {
        final Component replacement = LEGACY_COMPONENT_SERIALIZER.deserialize(productName)
                .hoverEvent(itemStack.asHoverEvent());
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
