package de.epiceric.shopchest.catalog;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogueRefreshPolicyTest {

    private final CatalogueRefreshPolicy policy = CatalogueRefreshPolicy.standard();

    @Test
    void refreshesPeriodicallyEveryFifteenMinutes() {
        assertEquals(Duration.ofMinutes(15), policy.periodicRefreshInterval());
    }

    @Test
    void announcesInitialReadinessAndChangesButNotUnchangedRefreshes() {
        assertTrue(policy.shouldAnnounce(null, 5));
        assertFalse(policy.shouldAnnounce(5, 5));
        assertTrue(policy.shouldAnnounce(5, 6));
        assertTrue(policy.shouldAnnounce(6, 0));
    }
}
