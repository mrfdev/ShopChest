package de.epiceric.shopchest.catalog;

import org.bukkit.inventory.ItemStack;

/** Matches an inventory candidate against an exact configured product template. */
@FunctionalInterface
public interface ExactItemMatcher {

    boolean matches(ItemStack candidate, ItemStack template);
}
