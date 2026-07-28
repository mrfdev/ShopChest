package de.epiceric.shopchest.shop;

import de.epiceric.shopchest.ShopChest;
import org.bukkit.scheduler.BukkitTask;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Drives every visible shop icon from one server task. Display interpolation
 * keeps motion smooth between the deliberately infrequent metadata updates.
 */
public final class ShopItemAnimator {

    private final ShopChest plugin;
    private final Set<ShopItem> items = new LinkedHashSet<>();

    private BukkitTask task;
    private long elapsedTicks;

    public ShopItemAnimator(ShopChest plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null && !task.isCancelled()) {
            return;
        }
        task = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::tick,
                ShopItemAnimation.UPDATE_INTERVAL_TICKS,
                ShopItemAnimation.UPDATE_INTERVAL_TICKS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        items.clear();
    }

    void register(ShopItem item) {
        items.add(item);
        item.applyAnimation(elapsedTicks);
    }

    void unregister(ShopItem item) {
        items.remove(item);
    }

    void prepareForViewer(ShopItem item) {
        item.applyAnimation(elapsedTicks);
    }

    int registeredItems() {
        return items.size();
    }

    public void refresh() {
        for (ShopItem item : items) {
            if (item.exists()) {
                item.applyAnimation(elapsedTicks);
            }
        }
    }

    private void tick() {
        elapsedTicks += ShopItemAnimation.UPDATE_INTERVAL_TICKS;

        final Iterator<ShopItem> iterator = items.iterator();
        while (iterator.hasNext()) {
            final ShopItem item = iterator.next();
            if (!item.exists()) {
                iterator.remove();
            } else if (item.hasViewers()) {
                item.applyAnimation(elapsedTicks);
            }
        }
    }
}
