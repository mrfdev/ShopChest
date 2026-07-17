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
}
