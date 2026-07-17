package de.epiceric.shopchest.config.hologram;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

public final class HologramItemDetails {

    private final List<Component> enchantments;
    private final List<Component> potionEffects;
    private final TextColor separatorColor;

    private HologramItemDetails(
            List<Component> enchantments,
            List<Component> potionEffects,
            TextColor separatorColor
    ) {
        this.enchantments = List.copyOf(enchantments);
        this.potionEffects = List.copyOf(potionEffects);
        this.separatorColor = separatorColor;
    }

    public static HologramItemDetails from(ItemStack itemStack, TextColor detailColor, TextColor separatorColor) {
        if (itemStack == null) {
            return new HologramItemDetails(Collections.emptyList(), Collections.emptyList(), separatorColor);
        }

        final ItemMeta itemMeta = itemStack.getItemMeta();
        final List<Component> enchantments = getEnchantments(itemStack, itemMeta).entrySet().stream()
                .map(entry -> entry.getKey().displayName(entry.getValue()).color(detailColor))
                .toList();
        final List<Component> potionEffects = itemMeta instanceof PotionMeta potionMeta
                ? potionMeta.getAllEffects().stream()
                        .map(effect -> formatPotionEffect(effect, detailColor))
                        .toList()
                : Collections.emptyList();

        return new HologramItemDetails(enchantments, potionEffects, separatorColor);
    }

    public boolean hasEnchantments() {
        return !enchantments.isEmpty();
    }

    public boolean hasPotionEffects() {
        return !potionEffects.isEmpty();
    }

    public boolean isEmpty() {
        return enchantments.isEmpty() && potionEffects.isEmpty();
    }

    public Component enchantments(int maximumEntries, int entriesPerLine, IntFunction<Component> overflowFactory) {
        return formatEntries(enchantments, maximumEntries, entriesPerLine, separatorColor, overflowFactory);
    }

    public Component potionEffects(int maximumEntries, int entriesPerLine, IntFunction<Component> overflowFactory) {
        return formatEntries(potionEffects, maximumEntries, entriesPerLine, separatorColor, overflowFactory);
    }

    public Component combined(int maximumEntries, int entriesPerLine, IntFunction<Component> overflowFactory) {
        final List<Component> entries = new ArrayList<>(enchantments.size() + potionEffects.size());
        entries.addAll(enchantments);
        entries.addAll(potionEffects);
        return formatEntries(entries, maximumEntries, entriesPerLine, separatorColor, overflowFactory);
    }

    static Component formatEntries(
            List<Component> sourceEntries,
            int maximumEntries,
            int entriesPerLine,
            TextColor separatorColor,
            IntFunction<Component> overflowFactory
    ) {
        if (sourceEntries.isEmpty()) {
            return Component.empty();
        }

        final int boundedMaximum = Math.max(1, maximumEntries);
        final int boundedPerLine = Math.max(1, entriesPerLine);
        final int visibleEntryCount = Math.min(sourceEntries.size(), boundedMaximum);
        final List<Component> visibleEntries = new ArrayList<>(sourceEntries.subList(0, visibleEntryCount));
        final int hiddenEntryCount = sourceEntries.size() - visibleEntryCount;
        if (hiddenEntryCount > 0) {
            visibleEntries.add(overflowFactory.apply(hiddenEntryCount));
        }

        Component result = Component.empty();
        for (int index = 0; index < visibleEntries.size(); index++) {
            if (index > 0) {
                result = result.append(index % boundedPerLine == 0
                        ? Component.newline()
                        : Component.text(", ", separatorColor));
            }
            result = result.append(visibleEntries.get(index));
        }
        return result;
    }

    static Component formatPotionEffect(PotionEffect effect, TextColor detailColor) {
        Component result = Component.translatable(effect.getType()).color(detailColor);
        final int level = effect.getAmplifier() + 1;
        if (level > 1) {
            result = result.append(Component.text(" " + toRomanNumeral(level), detailColor));
        }
        if (!effect.getType().isInstant()) {
            final String duration = effect.isInfinite() ? "\u221E" : formatDuration(effect.getDuration());
            result = result.append(Component.text(" (" + duration + ")", detailColor));
        }
        return result;
    }

    static String formatDuration(int durationTicks) {
        final int totalSeconds = Math.max(0, durationTicks / 20);
        final int hours = totalSeconds / 3600;
        final int minutes = (totalSeconds % 3600) / 60;
        final int seconds = totalSeconds % 60;
        return hours > 0
                ? "%d:%02d:%02d".formatted(hours, minutes, seconds)
                : "%d:%02d".formatted(minutes, seconds);
    }

    static String toRomanNumeral(int value) {
        if (value <= 0 || value > 3999) {
            return Integer.toString(value);
        }

        final int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        final String[] numerals = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        final StringBuilder result = new StringBuilder();
        int remaining = value;
        for (int index = 0; index < values.length; index++) {
            while (remaining >= values[index]) {
                result.append(numerals[index]);
                remaining -= values[index];
            }
        }
        return result.toString();
    }

    private static Map<Enchantment, Integer> getEnchantments(ItemStack itemStack, ItemMeta itemMeta) {
        if (itemMeta instanceof EnchantmentStorageMeta storageMeta) {
            return storageMeta.getStoredEnchants();
        }
        return itemStack.getEnchantments();
    }
}
