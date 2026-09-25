package de.epiceric.shopchest.utils;

import static de.epiceric.shopchest.utils.TradeConfirmationGuard.Direction.BUY;
import static de.epiceric.shopchest.utils.TradeConfirmationGuard.Direction.SELL;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class TradeConfirmationGuardTest {

    private final AtomicLong now = new AtomicLong();
    private final TradeConfirmationGuard<String, String> confirmations =
            new TradeConfirmationGuard<>(now::get, TimeUnit.SECONDS.toNanos(60));
    private final UUID playerId = UUID.randomUUID();

    @Test
    void identicalSecondInteractionConfirmsOnce() {
        final TradeConfirmationGuard.Proposal<String, String> proposal =
                proposal("shop-1", BUY, false, 8, 40.0, "diamond");

        assertFalse(confirmations.confirmOrRemember(playerId, proposal));
        assertTrue(confirmations.confirmOrRemember(playerId, proposal));
        assertFalse(confirmations.confirmOrRemember(playerId, proposal));
    }

    @Test
    void directionStackTermsAndProductMustStillMatch() {
        final TradeConfirmationGuard.Proposal<String, String> original =
                proposal("shop-1", BUY, false, 8, 40.0, "diamond");
        assertFalse(confirmations.confirmOrRemember(playerId, original));

        assertFalse(confirmations.confirmOrRemember(
                playerId,
                proposal("shop-1", SELL, false, 8, 40.0, "diamond")));
        assertFalse(confirmations.confirmOrRemember(
                playerId,
                proposal("shop-1", SELL, true, 64, 320.0, "diamond")));
        assertFalse(confirmations.confirmOrRemember(
                playerId,
                proposal("shop-1", SELL, true, 64, 256.0, "diamond")));
        assertFalse(confirmations.confirmOrRemember(
                playerId,
                proposal("shop-1", SELL, true, 64, 256.0, "emerald")));

        assertTrue(confirmations.confirmOrRemember(
                playerId,
                proposal("shop-1", SELL, true, 64, 256.0, "emerald")));
    }

    @Test
    void changedEffectiveAutoCalculatedQuoteRequiresNewConfirmation() {
        final TradeConfirmationGuard.Proposal<String, String> fullTrade =
                proposal("shop-1", BUY, false, 10, 100.0, "diamond");
        final TradeConfirmationGuard.Proposal<String, String> reducedTrade =
                proposal("shop-1", BUY, false, 5, 50.0, "diamond");

        assertFalse(confirmations.confirmOrRemember(playerId, fullTrade));
        assertFalse(confirmations.confirmOrRemember(playerId, reducedTrade));
        assertTrue(confirmations.confirmOrRemember(playerId, reducedTrade));
    }

    @Test
    void confirmationExpiresAndLifecycleClearsIt() {
        final TradeConfirmationGuard.Proposal<String, String> proposal =
                proposal("shop-1", BUY, false, 8, 40.0, "diamond");
        assertFalse(confirmations.confirmOrRemember(playerId, proposal));

        now.set(TimeUnit.SECONDS.toNanos(60));
        assertFalse(confirmations.confirmOrRemember(playerId, proposal));

        confirmations.clear(playerId);
        assertFalse(confirmations.confirmOrRemember(playerId, proposal));

        confirmations.clearAll();
        assertFalse(confirmations.confirmOrRemember(playerId, proposal));
    }

    @Test
    void differentPlayersAndShopsKeepIndependentPendingProposals() {
        final UUID secondPlayer = UUID.randomUUID();
        final TradeConfirmationGuard.Proposal<String, String> firstShop =
                proposal("shop-1", BUY, false, 8, 40.0, "diamond");
        final TradeConfirmationGuard.Proposal<String, String> secondShop =
                proposal("shop-2", SELL, false, 4, 12.0, "emerald");

        assertFalse(confirmations.confirmOrRemember(playerId, firstShop));
        assertFalse(confirmations.confirmOrRemember(playerId, secondShop));
        assertFalse(confirmations.confirmOrRemember(secondPlayer, firstShop));

        assertTrue(confirmations.confirmOrRemember(playerId, firstShop));
        assertTrue(confirmations.confirmOrRemember(playerId, secondShop));
        assertTrue(confirmations.confirmOrRemember(secondPlayer, firstShop));
    }

    private static TradeConfirmationGuard.Proposal<String, String> proposal(
            String shop,
            TradeConfirmationGuard.Direction direction,
            boolean stack,
            int amount,
            double price,
            String product
    ) {
        return new TradeConfirmationGuard.Proposal<>(
                shop,
                direction,
                stack,
                amount,
                price,
                product);
    }
}
