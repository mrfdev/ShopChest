package de.epiceric.shopchest.storefront;

import de.epiceric.shopchest.catalog.PublicShopCandidate;
import de.epiceric.shopchest.catalog.PublicShopKind;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeaturedListingChoicesTest {

    private static final UUID OWNER =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_OWNER =
            UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Test
    void offersOnlyOwnedEligibleCustomerBuyShopIdsInNumericOrder() {
        assertEquals(
                List.of(2, 12),
                FeaturedListingChoices.eligibleShopIds(OWNER, List.of(
                        candidate(12, OWNER, PublicShopKind.NORMAL, 25.0D, false),
                        candidate(2, OWNER, PublicShopKind.NORMAL, 10.0D, false),
                        candidate(12, OWNER, PublicShopKind.NORMAL, 25.0D, false),
                        candidate(3, OTHER_OWNER, PublicShopKind.NORMAL, 10.0D, false),
                        candidate(4, OWNER, PublicShopKind.NORMAL, 0.0D, false),
                        candidate(5, OWNER, PublicShopKind.ADMIN, 10.0D, false),
                        candidate(6, OWNER, PublicShopKind.NORMAL, 10.0D, true))));
    }

    @Test
    void usesTheSameEligibilityDecisionForClickableActions() {
        assertTrue(FeaturedListingChoices.isEligible(
                OWNER, candidate(7, OWNER, PublicShopKind.NORMAL, 15.0D, false)));
        assertFalse(FeaturedListingChoices.isEligible(
                OWNER, candidate(7, OTHER_OWNER, PublicShopKind.NORMAL, 15.0D, false)));
        assertFalse(FeaturedListingChoices.isEligible(
                OWNER, candidate(7, OWNER, PublicShopKind.NORMAL, 0.0D, false)));
    }

    private static PublicShopCandidate candidate(
            int shopId,
            UUID ownerId,
            PublicShopKind kind,
            double buyPrice,
            boolean suspended
    ) {
        return new PublicShopCandidate(
                shopId,
                ownerId,
                Material.STONE_BRICKS,
                1,
                buyPrice,
                kind,
                suspended);
    }
}
