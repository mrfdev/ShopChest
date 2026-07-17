package de.epiceric.shopchest.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

public final class ShopInteractionCooldown {

    private final Map<UUID, Long> nextAllowedInteraction = new HashMap<>();
    private final LongSupplier nanoTime;

    public ShopInteractionCooldown() {
        this(System::nanoTime);
    }

    ShopInteractionCooldown(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    public boolean tryAcquire(UUID playerId, int cooldownMillis) {
        if (cooldownMillis <= 0) {
            nextAllowedInteraction.remove(playerId);
            return true;
        }

        final long now = nanoTime.getAsLong();
        final Long nextAllowed = nextAllowedInteraction.get(playerId);
        if (nextAllowed != null && now - nextAllowed < 0) {
            return false;
        }

        nextAllowedInteraction.put(
                playerId,
                now + TimeUnit.MILLISECONDS.toNanos(cooldownMillis));
        return true;
    }

    public void clear(UUID playerId) {
        nextAllowedInteraction.remove(playerId);
    }
}
