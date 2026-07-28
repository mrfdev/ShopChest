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
    void boundsLiveDisplayControls() {
        assertEquals(0, Config.normalizeHologramTextOpacity(-1));
        assertEquals(128, Config.normalizeHologramTextOpacity(128));
        assertEquals(255, Config.normalizeHologramTextOpacity(999));

        assertEquals(1.21D, Config.normalizeFloatingIconHeight(Double.NaN));
        assertEquals(0.25D, Config.normalizeFloatingIconHeight(-2.0D));
        assertEquals(4.0D, Config.normalizeFloatingIconHeight(10.0D));

        assertEquals(0.45F, Config.normalizeFloatingIconScale(Double.NaN));
        assertEquals(0.1F, Config.normalizeFloatingIconScale(0.01D));
        assertEquals(2.0F, Config.normalizeFloatingIconScale(3.0D));

        assertEquals(0.0F, Config.normalizeFloatingIconBobAmplitude(-1.0D));
        assertEquals(0.5F, Config.normalizeFloatingIconBobAmplitude(1.0D));
        assertEquals(0.5D, Config.normalizeFloatingIconBobPeriodSeconds(0.1D));
        assertEquals(60.0D, Config.normalizeFloatingIconBobPeriodSeconds(90.0D));
        assertEquals(0.5D, Config.normalizeFloatingIconRotationPeriodSeconds(0.1D));
        assertEquals(120.0D, Config.normalizeFloatingIconRotationPeriodSeconds(180.0D));
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
