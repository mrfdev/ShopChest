package de.epiceric.shopchest.nms;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface TextComponentHelper {

    LegacyComponentSerializer LEGACY_COMPONENT_SERIALIZER = LegacyComponentSerializer.legacySection();

    default Consumer<Player> getSendableItemInfo(String message, String itemPlaceHolder, ItemStack itemStack, String productName){
        final Component replacement = LEGACY_COMPONENT_SERIALIZER.deserialize(productName)
                .hoverEvent(itemStack.asHoverEvent());
        Component component = Component.empty();
        final Matcher matcher = Pattern.compile(itemPlaceHolder, Pattern.LITERAL).matcher(message);
        if (matcher.find()) {
            int cursor = 0;
            do {
                final String pre = message.substring(cursor, matcher.start());
                if (!pre.isEmpty()) {
                    component = component.append(LEGACY_COMPONENT_SERIALIZER.deserialize(pre));
                }
                component = component.append(replacement);
                cursor = matcher.end();
            } while (matcher.find());
            final String end = message.substring(cursor);
            if (!end.isEmpty()) {
                component = component.append(LEGACY_COMPONENT_SERIALIZER.deserialize(end));
            }
        } else {
            component = LEGACY_COMPONENT_SERIALIZER.deserialize(message);
        }
        final Component sendableComponent = component;
        return player -> player.sendMessage(sendableComponent);
    }

}
