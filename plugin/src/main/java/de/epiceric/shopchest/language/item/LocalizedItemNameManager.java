package de.epiceric.shopchest.language.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IllegalFormatException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LocalizedItemNameManager implements ItemNameManager {

    private static final LegacyComponentSerializer LEGACY_COMPONENT_SERIALIZER =
            LegacyComponentSerializer.legacySection();
    private final Map<String, String> itemOverrides;
    private final int ignoredOverrideCount;
    private ItemNameDiagnostics diagnostics = ItemNameDiagnostics.unavailable();

    public LocalizedItemNameManager(@NotNull Map<String, String> itemTranslations) {
        final Map<String, String> overrides = new LinkedHashMap<>();
        int ignored = 0;
        for (Map.Entry<String, String> entry : itemTranslations.entrySet()) {
            final String key = normalizeOverrideKey(entry.getKey());
            final String value = entry.getValue();
            if (key.isEmpty() || !isUsableOverride(value)) {
                ignored++;
                continue;
            }
            overrides.put(key, value);
        }
        this.itemOverrides = Map.copyOf(overrides);
        this.ignoredOverrideCount = ignored;
    }

    @Override
    @Nullable
    public String getItemName(@Nullable ItemStack stack) {
        if (stack == null) {
            return null;
        }

        final Component customName = getCustomName(stack);
        if (customName != null) {
            return LEGACY_COMPONENT_SERIALIZER.serialize(customName);
        }
        return getDefaultName(stack);
    }

    @Override
    @Nullable
    public Component getItemNameComponent(@Nullable ItemStack stack) {
        if (stack == null) {
            return null;
        }

        final Component customName = getCustomName(stack);
        if (customName != null) {
            return customName;
        }

        final String translationKey = getTranslationKey(stack);
        final String override = findOverride(
                itemOverrides,
                translationKey,
                stack.getType().getKey(),
                stack.getType().name());
        final String ownerName = getSkullOwnerName(stack);
        if (override != null) {
            return LEGACY_COMPONENT_SERIALIZER.deserialize(
                    formatNamedItem(override, ownerName));
        }

        final String fallback = ownerName == null
                ? getReadableName(stack.getType(), translationKey)
                : ownerName + "'s Head";
        TranslatableComponent translated = Component.translatable(stack).fallback(fallback);
        if (ownerName != null) {
            translated = translated.arguments(Component.text(ownerName));
        }
        return translated;
    }

    @Override
    public ItemNameDiagnostics getDiagnostics() {
        return diagnostics;
    }

    public ItemNameDiagnostics auditRuntimeItems() {
        int runtimeItems = 0;
        int translatableItems = 0;
        final List<String> missingTranslationKeys = new ArrayList<>();

        for (Material material : Material.values()) {
            if (!material.isItem()) {
                continue;
            }
            runtimeItems++;
            try {
                final String translationKey = ItemStack.of(material).translationKey();
                if (translationKey == null || translationKey.isBlank()) {
                    missingTranslationKeys.add(material.name());
                } else {
                    translatableItems++;
                }
            } catch (RuntimeException exception) {
                missingTranslationKeys.add(material.name());
            }
        }

        diagnostics = new ItemNameDiagnostics(
                runtimeItems,
                translatableItems,
                itemOverrides.size(),
                ignoredOverrideCount,
                missingTranslationKeys);
        return diagnostics;
    }

    @NotNull
    private String getDefaultName(@NotNull ItemStack stack) {
        final String translationKey = getTranslationKey(stack);
        final String override = findOverride(
                itemOverrides,
                translationKey,
                stack.getType().getKey(),
                stack.getType().name());
        final String ownerName = getSkullOwnerName(stack);
        if (override != null) {
            return formatNamedItem(override, ownerName);
        }
        return ownerName == null
                ? getReadableName(stack.getType(), translationKey)
                : ownerName + "'s Head";
    }

    @Nullable
    private static Component getCustomName(@NotNull ItemStack stack) {
        if (!stack.hasItemMeta()) {
            return null;
        }
        final ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }

        final Component itemName = meta.itemName();
        if (itemName != null && !itemName.equals(Component.empty())) {
            return itemName;
        }

        final Component displayName = meta.displayName();
        if (displayName != null && !displayName.equals(Component.empty())) {
            return displayName;
        }

        if (meta instanceof BookMeta bookMeta) {
            final String title = bookMeta.getTitle();
            if (title != null && !title.isBlank()) {
                return Component.text(title);
            }
        }
        return null;
    }

    @Nullable
    private static String getSkullOwnerName(@NotNull ItemStack stack) {
        if (!(stack.getItemMeta() instanceof SkullMeta skullMeta) || !skullMeta.hasOwner()) {
            return null;
        }
        return skullMeta.getOwningPlayer() == null
                ? null
                : skullMeta.getOwningPlayer().getName();
    }

    @NotNull
    public static String getReadableName(@NotNull ItemStack stack) {
        return getReadableName(stack.getType(), getTranslationKey(stack));
    }

    @NotNull
    static String getTranslationKey(@NotNull ItemStack stack) {
        final String translationKey = stack.translationKey();
        if (translationKey != null && !translationKey.isBlank()) {
            return translationKey;
        }
        final Material type = stack.getType();
        final NamespacedKey key = type.getKey();
        final String prefix = type.isBlock() ? "block" : "item";
        return prefix + "." + key.getNamespace() + "." + key.getKey();
    }

    @NotNull
    static String getReadableName(@NotNull Material type, @NotNull String translationKey) {
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
            if (word.isEmpty()) {
                continue;
            }
            if (!readableName.isEmpty()) {
                readableName.append(' ');
            }
            readableName.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                readableName.append(word.substring(1));
            }
        }
        return readableName.isEmpty() ? type.name() : readableName.toString();
    }

    @Nullable
    static String findOverride(
            Map<String, String> overrides,
            String translationKey,
            NamespacedKey materialKey,
            String materialName
    ) {
        final List<String> candidates = List.of(
                translationKey,
                materialKey.toString(),
                materialKey.getKey(),
                materialName);
        for (String candidate : candidates) {
            final String override = overrides.get(normalizeOverrideKey(candidate));
            if (isUsableOverride(override)) {
                return override;
            }
        }
        return null;
    }

    static boolean isUsableOverride(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        final String normalized = value.strip().toLowerCase(Locale.ROOT);
        return !normalized.equals("error")
                && !normalized.equals("unknown")
                && !normalized.equals("unknown item")
                && !normalized.equals("not configured")
                && !normalized.equals("null");
    }

    private static String normalizeOverrideKey(@Nullable String key) {
        return key == null ? "" : key.strip().toLowerCase(Locale.ROOT);
    }

    private static String formatNamedItem(String value, @Nullable String ownerName) {
        if (ownerName == null || !value.contains("%")) {
            return value;
        }
        try {
            return String.format(Locale.ROOT, value, ownerName);
        } catch (IllegalFormatException ignored) {
            return value;
        }
    }
}
