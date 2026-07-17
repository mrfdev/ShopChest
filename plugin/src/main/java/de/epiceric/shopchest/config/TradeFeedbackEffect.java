package de.epiceric.shopchest.config;

import org.bukkit.Particle;
import org.bukkit.Sound;

/**
 * Player-local presentation for one terminal trade outcome.
 */
public record TradeFeedbackEffect(
        boolean enabled,
        Sound sound,
        float volume,
        float pitch,
        Particle particle,
        int particleCount) {
}
