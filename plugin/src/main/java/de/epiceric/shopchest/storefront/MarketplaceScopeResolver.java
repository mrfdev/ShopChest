package de.epiceric.shopchest.storefront;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.config.Config;
import org.bukkit.Location;
import org.codemc.worldguardwrapper.WorldGuardWrapper;

import java.util.Set;
import java.util.stream.Collectors;

/** Evaluates the configured discovery scope without ever loading a world or chunk. */
public final class MarketplaceScopeResolver {

    private final ShopChest plugin;

    public MarketplaceScopeResolver(ShopChest plugin) {
        this.plugin = plugin;
    }

    public boolean includes(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        final MarketplaceScopePolicy policy = Config.storefrontGlobalLocationScope
                ? MarketplaceScopePolicy.global()
                : marketplacePolicy();
        if (policy.mode() == MarketplaceScopePolicy.Mode.GLOBAL) {
            return true;
        }
        if (!plugin.hasWorldGuard()) {
            return false;
        }
        try {
            final Set<String> regions = WorldGuardWrapper.getInstance()
                    .getRegions(location)
                    .stream()
                    .map(region -> region.getId())
                    .collect(Collectors.toUnmodifiableSet());
            return policy.includes(location.getWorld().getName(), regions);
        } catch (RuntimeException exception) {
            plugin.debug(exception);
            return false;
        }
    }

    public boolean isMarketplace(Location location) {
        if (location == null || location.getWorld() == null || !plugin.hasWorldGuard()) {
            return false;
        }
        try {
            final Set<String> regions = WorldGuardWrapper.getInstance()
                    .getRegions(location)
                    .stream()
                    .map(region -> region.getId())
                    .collect(Collectors.toUnmodifiableSet());
            return marketplacePolicy().includes(location.getWorld().getName(), regions);
        } catch (RuntimeException exception) {
            plugin.debug(exception);
            return false;
        }
    }

    private MarketplaceScopePolicy marketplacePolicy() {
        return MarketplaceScopePolicy.marketplace(
                Config.storefrontMarketplaceWorld,
                Config.storefrontMarketplaceRegion);
    }
}
