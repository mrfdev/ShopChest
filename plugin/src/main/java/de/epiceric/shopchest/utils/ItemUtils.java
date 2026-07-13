package de.epiceric.shopchest.utils;

import java.util.Arrays;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

public class ItemUtils {

    private static final LegacyComponentSerializer LEGACY_COMPONENT_SERIALIZER = LegacyComponentSerializer.legacySection();

    public static Map<Enchantment, Integer> getEnchantments(ItemStack itemStack) {
        if (itemStack.getItemMeta() instanceof EnchantmentStorageMeta) {
            EnchantmentStorageMeta esm = (EnchantmentStorageMeta) itemStack.getItemMeta();
            return esm.getStoredEnchants();
        } else {
            return itemStack.getEnchantments();
        }
    }

    public static PotionType getPotionEffect(ItemStack itemStack) {
        if (itemStack.getItemMeta() instanceof PotionMeta) {    
            return ((PotionMeta)itemStack.getItemMeta()).getBasePotionType();
        }

        return null;
    }

    public static boolean isExtendedPotion(ItemStack itemStack) {
        // Potion extension is represented by the selected base potion type on modern Paper.
        return false;
    }

    public static boolean isBannerPattern(ItemStack itemStack) {
        return itemStack.getType().name().endsWith("BANNER_PATTERN");
    }

    public static boolean isAir(Material type) {
        return Arrays.asList("AIR", "CAVE_AIR", "VOID_AIR").contains(type.name());
    }

    public static int getDamage(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return 0;
        }
        final ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta instanceof Damageable) {
            final Damageable damageable = (Damageable) itemMeta;
            return damageable.hasDamage() ? damageable.getDamage() : 0;
        }
        return 0;
    }

    public static void setDamage(ItemStack itemStack, int damage) {
        if (itemStack == null || damage <= 0) {
            return;
        }
        final ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        if (itemMeta instanceof Damageable) {
            final Damageable damageable = (Damageable) itemMeta;
            damageable.setDamage(damage);
            itemStack.setItemMeta(damageable);
        }
    }

    public static boolean isSameTypeAndDamage(ItemStack first, ItemStack second) {
        return first != null
                && second != null
                && first.getType() == second.getType()
                && getDamage(first) == getDamage(second);
    }

    public static String serializePlainly(Component component) {
        return component == null ? null : LEGACY_COMPONENT_SERIALIZER.serialize(component);
    }

    /**
     * Get the {@link ItemStack} from a String
     * @param item Serialized ItemStack e.g. {@code "STONE"} or {@code "STONE:1"}
     * @return The de-serialized ItemStack or {@code null} if the serialized item is invalid
     */
    public static ItemStack getItemStack(String item) {
        if (item.trim().isEmpty()) return null;

        if (item.contains(":")) {
            Material mat = Material.getMaterial(item.split(":")[0]);
            if (mat == null) return null;
            final ItemStack itemStack = new ItemStack(mat, 1);
            setDamage(itemStack, Integer.parseInt(item.split(":")[1]));
            return itemStack;
        } else {
            Material mat = Material.getMaterial(item);
            if (mat == null) return null;
            return new ItemStack(mat, 1);
        }
    }

}
