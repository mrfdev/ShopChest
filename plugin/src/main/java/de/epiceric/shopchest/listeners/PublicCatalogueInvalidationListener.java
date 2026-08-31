package de.epiceric.shopchest.listeners;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.event.ShopCreateEvent;
import de.epiceric.shopchest.event.ShopRemoveEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Debounces derived-catalogue rebuilds after authoritative shop changes. */
public final class PublicCatalogueInvalidationListener implements Listener {

    private final ShopChest plugin;

    public PublicCatalogueInvalidationListener(ShopChest plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShopCreated(ShopCreateEvent event) {
        plugin.getPublicCatalogue().requestRefresh();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShopRemoved(ShopRemoveEvent event) {
        plugin.getPublicCatalogue().requestRefresh();
    }
}
