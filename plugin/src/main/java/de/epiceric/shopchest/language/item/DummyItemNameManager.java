package de.epiceric.shopchest.language.item;

import net.kyori.adventure.text.Component;
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

    @Override
    public @Nullable Component getItemNameComponent(@Nullable ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }
        return Component.translatable(itemStack)
                .fallback(LocalizedItemNameManager.getReadableName(itemStack));
    }
}
