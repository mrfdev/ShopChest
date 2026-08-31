package de.epiceric.shopchest.storefront;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketplaceScopePolicyTest {

    @Test
    void marketplaceModeRequiresBothConfiguredWorldAndRegionWhileGlobalIncludesEither() {
        final MarketplaceScopePolicy marketplace = MarketplaceScopePolicy.marketplace(
                "general", "shops");

        assertTrue(marketplace.includes("general", Set.of("shops", "spawn")));
        assertFalse(marketplace.includes("spawn", Set.of("shops")));
        assertFalse(marketplace.includes("general", Set.of("market")));
        assertTrue(MarketplaceScopePolicy.global().includes("resource", Set.of()));
    }
}
