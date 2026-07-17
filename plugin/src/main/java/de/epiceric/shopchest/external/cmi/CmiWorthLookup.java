package de.epiceric.shopchest.external.cmi;

import org.bukkit.inventory.ItemStack;

import java.util.OptionalDouble;

@FunctionalInterface
interface CmiWorthLookup {

    OptionalDouble findSellWorth(ItemStack itemStack);
}
