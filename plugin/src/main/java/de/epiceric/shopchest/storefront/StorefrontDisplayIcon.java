package de.epiceric.shopchest.storefront;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.config.Config;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Harmless, transient ItemDisplay used as a Storefront Display landmark. */
final class StorefrontDisplayIcon {

    private static final float DISPLAY_BOUND = 1.5F;

    private final ShopChest plugin;
    private final ItemDisplay display;
    private final float animationPhase;
    private final Set<UUID> viewers = new HashSet<>();

    StorefrontDisplayIcon(
            ShopChest plugin,
            UUID ownerId,
            Material material,
            Location location
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(material, "material");
        this.display = Objects.requireNonNull(location.getWorld(), "location world")
                .spawn(location, ItemDisplay.class, entity -> {
                    entity.setPersistent(false);
                    entity.setVisibleByDefault(false);
                    entity.setInvulnerable(true);
                    entity.setGravity(false);
                    entity.setSilent(true);
                    entity.setBillboard(Display.Billboard.FIXED);
                    entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                    entity.setInterpolationDelay(0);
                    entity.setInterpolationDuration(StorefrontDisplayEffects.UPDATE_INTERVAL_TICKS);
                    entity.setDisplayWidth(DISPLAY_BOUND);
                    entity.setDisplayHeight(DISPLAY_BOUND);
                    entity.setItemStack(new ItemStack(material));
                });
        this.animationPhase = StorefrontDisplayEffects.phaseFor(ownerId);
    }

    void refresh(Material material, Location location, long elapsedTicks) {
        if (display.getItemStack().getType() != material) {
            display.setItemStack(new ItemStack(material));
        }
        display.teleport(location);
        applyAnimation(elapsedTicks);
    }

    void setVisible(Player player, boolean visible) {
        final UUID playerId = player.getUniqueId();
        if (visible) {
            if (viewers.add(playerId)) {
                player.showEntity(plugin, display);
            }
        } else if (viewers.remove(playerId) && player.isOnline()) {
            player.hideEntity(plugin, display);
        }
    }

    void forgetViewer(UUID playerId) {
        viewers.remove(playerId);
    }

    boolean hasViewers() {
        return !viewers.isEmpty();
    }

    boolean exists() {
        return display.isValid();
    }

    void applyAnimation(long elapsedTicks) {
        display.setTransformation(StorefrontDisplayEffects.iconTransformation(
                elapsedTicks,
                animationPhase,
                Config.storefrontDisplayIconScale,
                Config.storefrontDisplayIconBobAmplitude,
                Config.storefrontDisplayIconBobPeriodSeconds,
                Config.storefrontDisplayIconRotationPeriodSeconds));
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(StorefrontDisplayEffects.UPDATE_INTERVAL_TICKS);
    }

    void remove() {
        viewers.clear();
        display.remove();
    }
}
