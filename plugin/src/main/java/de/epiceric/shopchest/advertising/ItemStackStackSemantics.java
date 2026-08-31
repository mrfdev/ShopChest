package de.epiceric.shopchest.advertising;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/** Bukkit adapter for the pure currency and inventory planning domain. */
public final class ItemStackStackSemantics implements StackSemantics<ItemStack> {

    public static final ItemStackStackSemantics INSTANCE = new ItemStackStackSemantics();

    private ItemStackStackSemantics() {
    }

    @Override
    public boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getAmount() <= 0 || stack.getType().isAir();
    }

    @Override
    public int amount(ItemStack stack) {
        return stack.getAmount();
    }

    @Override
    public ItemStack copyOf(ItemStack stack) {
        return stack == null ? null : stack.clone();
    }

    @Override
    public ItemStack withAmount(ItemStack stack, int amount) {
        final ItemStack copy = stack.clone();
        copy.setAmount(amount);
        return copy;
    }

    @Override
    public boolean isSimilar(ItemStack candidate, ItemStack template) {
        return candidate.isSimilar(template);
    }

    @Override
    public boolean exactlyEquals(ItemStack left, ItemStack right) {
        return Objects.equals(left, right);
    }
}
