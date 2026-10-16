package de.epiceric.shopchest.utils;

import com.destroystokyo.paper.profile.PlayerProfile;
import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.external.cmi.CmiCachedOwnerNames;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.function.Function;

/** Uses existing local identity records only; never touches offline player NBT or profile services. */
public final class LocalOwnerNames implements Function<UUID, String> {
    private final ShopChest plugin;
    private boolean cmiFailed;

    public LocalOwnerNames(ShopChest plugin) {
        this.plugin = plugin;
    }

    @Override
    public String apply(UUID ownerId) {
        Server server = plugin.getServer();
        Player online = server.getPlayer(ownerId);
        if (online != null) {
            return online.getName();
        }

        Plugin cmi = server.getPluginManager().getPlugin("CMI");
        if (!cmiFailed && cmi != null && cmi.isEnabled()) {
            try {
                String name = CmiCachedOwnerNames.find(ownerId);
                if (name != null && !name.isBlank()) {
                    return name;
                }
            } catch (RuntimeException | LinkageError exception) {
                cmiFailed = true;
                plugin.getLogger().warning("CMI cached owner names are unavailable; using Paper's local profile cache.");
                plugin.debug(exception);
            }
        }

        PlayerProfile profile = server.createProfileExact(ownerId, null);
        // This API is explicitly cache-only. Never call complete(), update(), or getOfflinePlayer().
        profile.completeFromCache();
        return ownerId.equals(profile.getId()) ? profile.getName() : null;
    }
}
