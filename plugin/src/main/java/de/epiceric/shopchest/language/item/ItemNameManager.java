package de.epiceric.shopchest.language.item;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface ItemNameManager {

    @Nullable
    String getItemName(@Nullable ItemStack itemStack);

    @Nullable
    default Component getItemNameComponent(@Nullable ItemStack itemStack) {
        final String name = getItemName(itemStack);
        return name == null ? null : Component.text(name);
    }

    default ItemNameDiagnostics getDiagnostics() {
        return ItemNameDiagnostics.unavailable();
    }
}
