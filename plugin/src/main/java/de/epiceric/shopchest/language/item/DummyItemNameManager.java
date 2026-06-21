package de.epiceric.shopchest.language.item;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public class DummyItemNameManager implements ItemNameManager {

    @Override
    public @Nullable String getItemName(@Nullable ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }
        return LocalizedItemNameManager.getReadableName(itemStack);
    }

    
}
