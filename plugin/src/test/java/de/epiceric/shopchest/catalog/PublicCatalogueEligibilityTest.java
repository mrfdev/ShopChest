package de.epiceric.shopchest.catalog;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicCatalogueEligibilityTest {

    private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void includesOnlyUnsuspendedNormalCustomerBuyOffersWithValidTerms() {
        assertTrue(PublicCatalogueEligibility.isEligible(candidate(
                PublicShopKind.NORMAL, 16, 24.0, false)));

        assertFalse(PublicCatalogueEligibility.isEligible(candidate(
                PublicShopKind.ADMIN, 16, 24.0, false)));
        assertFalse(PublicCatalogueEligibility.isEligible(candidate(
                PublicShopKind.NORMAL, 16, 0.0, false)));
        assertFalse(PublicCatalogueEligibility.isEligible(candidate(
                PublicShopKind.NORMAL, 0, 24.0, false)));
        assertFalse(PublicCatalogueEligibility.isEligible(candidate(
                PublicShopKind.NORMAL, 16, 24.0, true)));
    }

    @Test
    void rejectsNonFiniteOfferTerms() {
        assertFalse(PublicCatalogueEligibility.isEligible(candidate(
                PublicShopKind.NORMAL, 16, Double.NaN, false)));
        assertFalse(PublicCatalogueEligibility.isEligible(candidate(
                PublicShopKind.NORMAL, 16, Double.POSITIVE_INFINITY, false)));
    }

    private static PublicShopCandidate candidate(
            PublicShopKind kind,
            int bundleAmount,
            double customerBuyPrice,
            boolean suspended) {
        return new PublicShopCandidate(
                7,
                OWNER,
                Material.STONE_BRICKS,
                bundleAmount,
                customerBuyPrice,
                kind,
                suspended);
    }
}
