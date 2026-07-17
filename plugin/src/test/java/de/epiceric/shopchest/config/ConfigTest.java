package de.epiceric.shopchest.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ConfigTest {

    @Test
    void normalizesHologramTextScale() {
        assertEquals(0.5f, Config.normalizeHologramTextScale(Double.NaN));
        assertEquals(0.5f, Config.normalizeHologramTextScale(0.25));
        assertEquals(0.8f, Config.normalizeHologramTextScale(0.8));
        assertEquals(1.25f, Config.normalizeHologramTextScale(2.0));
    }

    @Test
    void boundsTradeFeedbackToRestrainedValues() {
        assertEquals(0.45f, Config.normalizeTradeFeedbackValue(Double.NaN, 0.45, 0, 2));
        assertEquals(0.0f, Config.normalizeTradeFeedbackValue(-1, 0.45, 0, 2));
        assertEquals(2.0f, Config.normalizeTradeFeedbackValue(3, 0.45, 0, 2));
        assertEquals(0, Config.normalizeTradeFeedbackParticleCount(-1));
        assertEquals(4, Config.normalizeTradeFeedbackParticleCount(4));
        assertEquals(16, Config.normalizeTradeFeedbackParticleCount(40));
    }

    @Test
    void boundsTradeInteractionCooldown() {
        assertEquals(0, Config.normalizeTradeInteractionCooldownMillis(-1));
        assertEquals(250, Config.normalizeTradeInteractionCooldownMillis(250));
        assertEquals(5_000, Config.normalizeTradeInteractionCooldownMillis(10_000));
    }

    @Test
    void boundsCmiWorthWarningMultipliers() {
        assertEquals(0.5D, Config.normalizeCmiWorthLowMultiplier(Double.NaN));
        assertEquals(0.01D, Config.normalizeCmiWorthLowMultiplier(0.0D));
        assertEquals(0.75D, Config.normalizeCmiWorthLowMultiplier(0.75D));
        assertEquals(1.0D, Config.normalizeCmiWorthLowMultiplier(2.0D));

        assertEquals(20.0D, Config.normalizeCmiWorthHighMultiplier(Double.NaN));
        assertEquals(1.0D, Config.normalizeCmiWorthHighMultiplier(0.5D));
        assertEquals(25.0D, Config.normalizeCmiWorthHighMultiplier(25.0D));
        assertEquals(10_000.0D, Config.normalizeCmiWorthHighMultiplier(20_000.0D));
    }
}
