package de.epiceric.shopchest.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ShopInteractionCooldownTest {

    @Test
    void limitsEachPlayerUntilTheCooldownExpires() {
        AtomicLong now = new AtomicLong();
        ShopInteractionCooldown cooldown = new ShopInteractionCooldown(now::get);
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();

        assertTrue(cooldown.tryAcquire(firstPlayer, 250));
        assertFalse(cooldown.tryAcquire(firstPlayer, 250));
        assertTrue(cooldown.tryAcquire(secondPlayer, 250));

        now.set(TimeUnit.MILLISECONDS.toNanos(249));
        assertFalse(cooldown.tryAcquire(firstPlayer, 250));

        now.set(TimeUnit.MILLISECONDS.toNanos(250));
        assertTrue(cooldown.tryAcquire(firstPlayer, 250));
    }

    @Test
    void disabledCooldownAndClearingAllowImmediateInteraction() {
        AtomicLong now = new AtomicLong();
        ShopInteractionCooldown cooldown = new ShopInteractionCooldown(now::get);
        UUID playerId = UUID.randomUUID();

        assertTrue(cooldown.tryAcquire(playerId, 250));
        assertTrue(cooldown.tryAcquire(playerId, 0));
        assertTrue(cooldown.tryAcquire(playerId, 250));

        cooldown.clear(playerId);
        assertTrue(cooldown.tryAcquire(playerId, 250));
    }
}
