package de.epiceric.shopchest.language.item;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import de.epiceric.shopchest.ShopChest;

public class LocalizedItemNameManager implements ItemNameManager {

    private final Map<String, String> itemTranslations;
    private final Set<String> missingTranslations = ConcurrentHashMap.newKeySet();
    private static final LegacyComponentSerializer LEGACY_COMPONENT_SERIALIZER = LegacyComponentSerializer.legacySection();

    public LocalizedItemNameManager(@NotNull Map<String, String> itemTranslations) {
        this.itemTranslations = itemTranslations;
    }

    @Override
    @Nullable
    public String getItemName(@Nullable ItemStack stack) {
        if (stack == null) {
            return null;
        }

        final ItemMeta meta;
        if (!stack.hasItemMeta() || (meta = stack.getItemMeta()) == null) {
            return getDefaultName(stack);
        }

        final String itemName;
        if ((itemName = serializePlainly(meta.itemName())) != null && !itemName.isEmpty()) {
            return itemName;
        }

        final String displayName;
        if ((displayName = serializePlainly(meta.displayName())) != null && !displayName.isEmpty()) {
            return displayName;
        }

        if (meta instanceof BookMeta) {
            return ((BookMeta) meta).getTitle();
        }

        if (meta instanceof SkullMeta) {
            final SkullMeta skullMeta = (SkullMeta) meta;
            if (!skullMeta.hasOwner()) {
                return getDefaultName(stack);
            }
            skullMeta.getOwningPlayer();
            final String defaultName = getDefaultName(stack);
            final String ownerName = Objects.requireNonNull(skullMeta.getOwningPlayer()).getName();
            if (ownerName == null) {
                return defaultName;
            }
            return String.format(defaultName, ownerName);
        }

        return getDefaultName(stack);
    }

    @NotNull
    private String getDefaultName(@NotNull ItemStack stack) {
        final String key = getTranslationKey(stack);
        final String cachedTranslation = itemTranslations.get(key);
        if (cachedTranslation != null && !cachedTranslation.isEmpty()) {
            return cachedTranslation;
        }
        if (!itemTranslations.isEmpty() && missingTranslations.add(key)) {
            ShopChest.getInstance().getLogger().warning("Could not get the item translation for '" + key
                    + "'. Falling back to a generated item name.");
        }
        return getReadableName(stack);
    }

    @NotNull
    public static String getReadableName(@NotNull ItemStack stack) {
        return getReadableName(stack.getType(), getTranslationKey(stack));
    }

    @NotNull
    private static String getTranslationKey(@NotNull ItemStack stack) {
        final Material type = stack.getType();
        final NamespacedKey key = type.getKey();
        final String prefix = type.isBlock() ? "block" : "item";
        return prefix + "." + key.getNamespace() + "." + key.getKey();
    }

    @NotNull
    private static String getReadableName(@NotNull Material type, @NotNull String translationKey) {
        String name = translationKey;
        final int lastDot = name.lastIndexOf('.');
        if (lastDot >= 0 && lastDot + 1 < name.length()) {
            name = name.substring(lastDot + 1);
        }
        final int lastColon = name.lastIndexOf(':');
        if (lastColon >= 0 && lastColon + 1 < name.length()) {
            name = name.substring(lastColon + 1);
        }
        if (name.isEmpty()) {
            name = type.name().toLowerCase(Locale.ROOT);
        }

        final StringBuilder readableName = new StringBuilder(name.length());
        for (String word : name.split("_")) {
            if (word.isEmpty()) continue;
            if (readableName.length() > 0) {
                readableName.append(' ');
            }
            readableName.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                readableName.append(word.substring(1));
            }
        }
        return readableName.length() == 0 ? type.name() : readableName.toString();
    }

    @Nullable
    private static String serializePlainly(@Nullable Component component) {
        return component == null ? null : LEGACY_COMPONENT_SERIALIZER.serialize(component);
    }

}
