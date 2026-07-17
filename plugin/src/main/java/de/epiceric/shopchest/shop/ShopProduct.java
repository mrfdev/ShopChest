package de.epiceric.shopchest.shop;

import de.epiceric.shopchest.ShopChest;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

public class ShopProduct {

    private final ItemStack itemStack;
    private final int amount;

    public ShopProduct(ItemStack itemStack, int amount) {
        this.itemStack = new ItemStack(itemStack);
        this.itemStack.setAmount(1);
        this.amount = amount;
    }

    public ShopProduct(ItemStack itemStack) {
        this(itemStack, itemStack.getAmount());
    }

    /**
     * @return The product name as plain legacy text for logs and legacy messages.
     */
    public String getLocalizedName() {
        return ShopChest.getInstance().getLanguageManager().getItemNameManager().getItemName(getItemStack());
    }

    /**
     * @return The rich product name for client-side vanilla translation.
     */
    public Component getLocalizedNameComponent() {
        return ShopChest.getInstance().getLanguageManager()
                .getItemNameManager()
                .getItemNameComponent(getItemStack());
    }

    /**
     * @return The {@link ItemStack} with an amount of {@code 1}.
     */
    public ItemStack getItemStack() {
        return itemStack;
    }

    /**
     * @return The amount
     */
    public int getAmount() {
        return amount;
    }
    
}
