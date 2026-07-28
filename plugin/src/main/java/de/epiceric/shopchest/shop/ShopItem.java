package de.epiceric.shopchest.shop;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.config.Config;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Objects;

public class ShopItem {

    private static final float DISPLAY_BOUND = 1.0F;

    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
    private final ItemStack itemStack;
    private Location location;
    private final ItemDisplay display;
    private final ShopChest plugin;
    private final float animationPhase;

    public ShopItem(ShopChest plugin, ItemStack itemStack, Location location) {
        this.plugin = plugin;
        this.itemStack = itemStack.clone();
        this.location = location.clone();
        this.display = Objects.requireNonNull(location.getWorld()).spawn(location, ItemDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.setVisibleByDefault(false);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setSilent(true);
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            entity.setInterpolationDelay(0);
            entity.setInterpolationDuration(ShopItemAnimation.UPDATE_INTERVAL_TICKS);
            entity.setDisplayWidth(DISPLAY_BOUND);
            entity.setDisplayHeight(DISPLAY_BOUND);
            entity.setItemStack(this.itemStack);
        });
        this.animationPhase = ShopItemAnimation.phaseFor(display.getUniqueId());
        plugin.getShopItemAnimator().register(this);
    }

    /**
     * @return Clone of the location, where the shop item should be (it could have been moved by something, even though it shouldn't)
     */
    public Location getLocation() {
        return location.clone();
    }

    /**
     * Moves the display while preserving its viewers and animation state.
     */
    public void setLocation(Location location) {
        this.location = location.clone();
        display.teleport(this.location);
    }

    /**
     * @return A clone of this Item's {@link ItemStack}
     */
    public ItemStack getItemStack() {
        return itemStack.clone();
    }

    /**
     * @param p Player to check
     * @return Whether the item is visible to the player
     */
    public boolean isVisible(Player p) {
        return viewers.contains(p.getUniqueId());
    }

    /**
     * @param p Player to which the item should be shown
     */
    public void showPlayer(Player p) {
        showPlayer(p, false);
    }

    /**
     * @param p Player to which the item should be shown
     * @param force whether to force or not
     */
    public void showPlayer(Player p, boolean force) {
        if (viewers.add(p.getUniqueId()) || force) {
            plugin.getShopItemAnimator().prepareForViewer(this);
            p.showEntity(plugin, display);
        }
    }

    /**
     * @param p Player from which the item should be hidden
     */
    public void hidePlayer(Player p) {
        hidePlayer(p, false);
    }

    /**
     * @param p Player from which the item should be hidden
     * @param force whether to force or not
     */
    public void hidePlayer(Player p, boolean force) {
        if (viewers.remove(p.getUniqueId()) || force) {
            if (p.isOnline()) {
                p.hideEntity(plugin, display);
            }
        }
    }

    public void resetVisible(Player p) {
        viewers.remove(p.getUniqueId());
    }

    /**
     * Removes the item. <br>
     * Item will be hidden from all players
     */
    public void remove() {
        viewers.clear();
        plugin.getShopItemAnimator().unregister(this);
        display.remove();
    }

    /**
     * Respawns the item at the set location for a player
     * @param p Player, for which the item should be reset
     */
    public void resetForPlayer(Player p) {
        hidePlayer(p);
        showPlayer(p);
    }

    public boolean exists() {
        return display.isValid();
    }

    boolean hasViewers() {
        return !viewers.isEmpty();
    }

    void applyAnimation(long elapsedTicks) {
        display.setTransformation(ShopItemAnimation.at(
                elapsedTicks,
                animationPhase,
                Config.floatingIconScale,
                Config.floatingIconBobbingEnabled,
                Config.floatingIconBobAmplitude,
                Config.floatingIconBobPeriodSeconds,
                Config.floatingIconRotationEnabled,
                Config.floatingIconRotationPeriodSeconds));
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ShopItemAnimation.UPDATE_INTERVAL_TICKS);
    }

}
