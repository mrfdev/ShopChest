package de.epiceric.shopchest.catalog;

/** Shared public-discovery eligibility policy for normal Customer-Buy Offers. */
public final class PublicCatalogueEligibility {

    private PublicCatalogueEligibility() {
    }

    public static boolean isEligible(PublicShopCandidate candidate) {
        return candidate != null
                && candidate.kind() == PublicShopKind.NORMAL
                && !candidate.storefrontSuspended()
                && candidate.bundleAmount() > 0
                && candidate.customerBuyPrice() > 0.0D
                && Double.isFinite(candidate.customerBuyPrice());
    }
}
