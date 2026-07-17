package de.epiceric.shopchest.utils;

import de.epiceric.shopchest.ShopChest;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ShopUpdater {

    private final static int MAX_QUEUE_SIZE = 10_000;
    private static final int MAX_TASKS_PER_TICK = 1_000;

    private final ShopChest plugin;
    private final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();

    private BukkitTask task;

    public ShopUpdater(ShopChest plugin) {
        this.plugin = plugin;
    }

    /**
     * Start task, except if it is already
     */
    public void start() {
        if (!isRunning()) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::runQueuedTasks, 1L, 1L);
        }
    }

    /**
     * Stop any running task then start it again
     */
    public void restart() {
        stop();
        start();
    }

    /**
     * Stop task properly
     */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        queue.clear();
    }

    /**
     * @return whether task is running or not
     */
    public boolean isRunning() {
        return task != null && !task.isCancelled();
    }

    /**
     * Queue a task to update shops for the given player
     *
     * @param player Player to show updates
     */
    public void updateShops(Player player) {
        queue(() -> plugin.getShopUtils().updateShops(player));
    }

    /**
     * Queue a task to update shops for players in the given world
     *
     * @param world World in whose players to show updates
     */
    public void updateShops(World world) {
        queue(() -> {
            for (Player player : world.getPlayers()) {
                plugin.getShopUtils().updateShops(player);
            }
        });
    }

    /**
     * Queue a task to update shops for all players
     */
    public void updateShops() {
        queue(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                plugin.getShopUtils().updateShops(player);
            }
        });
    }

    /**
     * Register a task to run before next loop
     *
     * @param runnable task to run
     */
    public void queue(Runnable runnable) {
        queue.add(runnable);
    }

    private void runQueuedTasks() {
        if (queue.size() > MAX_QUEUE_SIZE) {
            plugin.getLogger().warning("Discarding an excessive ShopChest display-update backlog.");
            queue.clear();
            return;
        }

        int processed = 0;
        Runnable runnable;
        while (processed < MAX_TASKS_PER_TICK && (runnable = queue.poll()) != null) {
            try {
                runnable.run();
            } catch (RuntimeException exception) {
                plugin.getLogger().severe("A queued ShopChest display update failed: "
                        + exception.getMessage());
                plugin.debug(exception);
            }
            processed++;
        }
    }
}
