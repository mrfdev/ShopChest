package de.epiceric.shopchest.external.cmi;

import com.Zrips.CMI.CMI;
import com.Zrips.CMI.Modules.Worth.WorthItem;
import org.bukkit.inventory.ItemStack;

import java.util.OptionalDouble;

/**
 * Small boundary around CMI's documented worth API. Keeping the CMI types in a
 * separate class allows ShopChest to run normally when the optional plugin is
 * absent.
 */
final class CmiApiWorthLookup implements CmiWorthLookup {

    CmiApiWorthLookup() {
        if (CMI.getInstance() == null || CMI.getInstance().getWorthManager() == null) {
            throw new IllegalStateException("CMI WorthManager is unavailable");
        }
    }

    @Override
    public OptionalDouble findSellWorth(ItemStack itemStack) {
        ItemStack unitItem = itemStack.clone();
        unitItem.setAmount(1);

        WorthItem worth = CMI.getInstance().getWorthManager().getWorth(unitItem);
        if (worth == null) {
            return OptionalDouble.empty();
        }

        Double sellPrice = worth.getSellPrice();
        if (sellPrice == null || !Double.isFinite(sellPrice) || sellPrice <= 0.0D) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(sellPrice);
    }
}
