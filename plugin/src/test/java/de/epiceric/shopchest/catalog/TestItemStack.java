package de.epiceric.shopchest.catalog;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Registry-free ItemStack boundary fake for pure catalogue tests. */
final class TestItemStack extends ItemStack {

    private final Material type;
    private final String variant;
    private int amount;

    TestItemStack(Material type, int amount) {
        this(type, amount, "default");
    }

    TestItemStack(Material type, int amount, String variant) {
        this.type = type;
        this.amount = amount;
        this.variant = variant;
    }

    @Override
    public Material getType() {
        return type;
    }

    @Override
    public int getAmount() {
        return amount;
    }

    @Override
    public void setAmount(int amount) {
        this.amount = amount;
    }

    @Override
    public boolean isSimilar(ItemStack other) {
        return other instanceof TestItemStack stack
                && type == stack.type
                && variant.equals(stack.variant);
    }

    @Override
    public TestItemStack clone() {
        return new TestItemStack(type, amount, variant);
    }
}
