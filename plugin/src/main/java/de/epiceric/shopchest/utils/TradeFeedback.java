package de.epiceric.shopchest.utils;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import de.epiceric.shopchest.config.Config;
import de.epiceric.shopchest.config.TradeFeedbackEffect;
import de.epiceric.shopchest.shop.Shop;

public final class TradeFeedback {

    private static final double HORIZONTAL_OFFSET = 0.2;
    private static final double VERTICAL_OFFSET = 0.12;
    private static final double PARTICLE_SPEED = 0.01;

    private TradeFeedback() {
    }

    public static void success(Player player, Shop shop) {
        send(player, shop, Config.tradeSuccessFeedback);
    }

    public static void failure(Player player, Shop shop) {
        send(player, shop, Config.tradeFailureFeedback);
    }

    private static void send(Player player, Shop shop, TradeFeedbackEffect effect) {
        if (effect == null || !effect.enabled()) {
            return;
        }

        final Location location = shop.getLocation().clone().add(0.5, 0.85, 0.5);
        if (effect.particle() != null && effect.particleCount() > 0) {
            player.spawnParticle(effect.particle(), location, effect.particleCount(),
                    HORIZONTAL_OFFSET, VERTICAL_OFFSET, HORIZONTAL_OFFSET, PARTICLE_SPEED);
        }
        if (effect.sound() != null && effect.volume() > 0) {
            player.playSound(location, effect.sound(), SoundCategory.BLOCKS, effect.volume(), effect.pitch());
        }
    }
}
