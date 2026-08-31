package de.epiceric.shopchest.listeners;

import de.epiceric.shopchest.ShopChest;
import de.epiceric.shopchest.config.Config;
import de.epiceric.shopchest.config.Placeholder;
import de.epiceric.shopchest.display.TextComponentHelper;
import de.epiceric.shopchest.language.Message;
import de.epiceric.shopchest.language.MessageRegistry;
import de.epiceric.shopchest.language.Replacement;
import de.epiceric.shopchest.utils.Callback;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class NotifyPlayerOnJoinListener implements Listener {

    private final ShopChest plugin;
    private final RevenueNoticeDelay revenueNoticeDelay;

    public NotifyPlayerOnJoinListener(ShopChest plugin) {
        this.plugin = plugin;
        this.revenueNoticeDelay = new RevenueNoticeDelay(
                (task, delayTicks) -> plugin.getServer().getScheduler()
                        .runTaskLater(plugin, task, delayTicks),
                () -> Config.offlineRevenueNotificationDelaySeconds);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        final Player p = e.getPlayer();

        plugin.getShopDatabase().getLastLogout(p, new Callback<Long>(plugin) {
            @Override
            public void onResult(Long result) {
                if (result < 0) {
                    // No logout saved, probably first time joining.
                    return;
                }

                plugin.getShopDatabase().getRevenue(p, result, new Callback<Double>(plugin) {
                    @Override
                    public void onResult(Double result) {
                        if (result != null && Double.isFinite(result)
                                && Math.abs(result) > 0.0000001) {
                            revenueNoticeDelay.schedule(p::isConnected, () -> {
                                final MessageRegistry messageRegistry =
                                        plugin.getLanguageManager().getMessageRegistry();
                                p.sendMessage(TextComponentHelper.getClickableActionMessage(
                                        messageRegistry.getMessage(
                                                Message.REVENUE_WHILE_OFFLINE,
                                                new Replacement(Placeholder.REVENUE, result)),
                                        messageRegistry.getMessage(
                                                Message.REVENUE_WHILE_OFFLINE_ACTION),
                                        messageRegistry.getMessage(
                                                Message.REVENUE_WHILE_OFFLINE_HOVER,
                                                new Replacement(
                                                        Placeholder.COMMAND,
                                                        Config.mainCommandName)),
                                        "/" + Config.mainCommandName + " recent"));
                            });
                        }
                    }
                });
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        plugin.getShopDatabase().logLogout(e.getPlayer(), null);
    }

}
