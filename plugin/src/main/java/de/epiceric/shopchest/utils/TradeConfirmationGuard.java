package de.epiceric.shopchest.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Stores short-lived, one-use confirmations for exact shop trade proposals.
 *
 * @param <S> stable logical shop identity
 * @param <P> immutable product identity or defensive product snapshot
 */
public final class TradeConfirmationGuard<S, P> {

    static final long DEFAULT_TTL_NANOS = TimeUnit.SECONDS.toNanos(60);

    public enum Direction {
        BUY,
        SELL,
    }

    private final Map<UUID, List<PendingConfirmation<S, P>>> pendingByPlayer =
            new HashMap<>();
    private final LongSupplier nanoTime;
    private final long ttlNanos;

    public TradeConfirmationGuard() {
        this(System::nanoTime, DEFAULT_TTL_NANOS);
    }

    TradeConfirmationGuard(LongSupplier nanoTime, long ttlNanos) {
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        if (ttlNanos <= 0) {
            throw new IllegalArgumentException("ttlNanos must be positive");
        }
        this.ttlNanos = ttlNanos;
    }

    /**
     * Consumes and confirms an identical pending proposal, or remembers this
     * proposal and requires another matching interaction.
     */
    public boolean confirmOrRemember(UUID playerId, Proposal<S, P> proposal) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(proposal, "proposal");

        final long now = nanoTime.getAsLong();
        final List<PendingConfirmation<S, P>> pending = pendingByPlayer.computeIfAbsent(
                playerId,
                ignored -> new ArrayList<>());
        pending.removeIf(confirmation -> confirmation.isExpired(now));

        boolean confirmed = false;
        for (Iterator<PendingConfirmation<S, P>> iterator = pending.iterator();
                iterator.hasNext();) {
            final PendingConfirmation<S, P> candidate = iterator.next();
            if (!Objects.equals(
                    candidate.proposal().shopIdentity(),
                    proposal.shopIdentity())) {
                continue;
            }

            iterator.remove();
            confirmed = candidate.proposal().equals(proposal);
            break;
        }

        if (confirmed) {
            if (pending.isEmpty()) {
                pendingByPlayer.remove(playerId);
            }
            return true;
        }

        pending.add(new PendingConfirmation<>(proposal, now + ttlNanos));
        return false;
    }

    public void clear(UUID playerId) {
        pendingByPlayer.remove(playerId);
    }

    public void clearAll() {
        pendingByPlayer.clear();
    }

    public record Proposal<S, P>(
            S shopIdentity,
            Direction direction,
            boolean stack,
            int amount,
            double price,
            P productIdentity
    ) {

        public Proposal {
            Objects.requireNonNull(shopIdentity, "shopIdentity");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(productIdentity, "productIdentity");
        }
    }

    private record PendingConfirmation<S, P>(
            Proposal<S, P> proposal,
            long expiresAtNanos
    ) {

        private boolean isExpired(long now) {
            return now - expiresAtNanos >= 0;
        }
    }
}
