package de.epiceric.shopchest.storefront;

import de.epiceric.shopchest.catalog.PublicCatalogueEligibility;
import de.epiceric.shopchest.catalog.PublicShopCandidate;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Shared owner and Customer-Buy eligibility policy for Featured Listing choices. */
public final class FeaturedListingChoices {

    private FeaturedListingChoices() {
    }

    public static boolean isEligible(UUID ownerId, PublicShopCandidate candidate) {
        Objects.requireNonNull(ownerId, "ownerId");
        return candidate != null
                && ownerId.equals(candidate.ownerId())
                && candidate.shopId() > 0
                && PublicCatalogueEligibility.isEligible(candidate);
    }

    public static List<Integer> eligibleShopIds(
            UUID ownerId,
            Collection<PublicShopCandidate> candidates
    ) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(candidates, "candidates");
        return candidates.stream()
                .filter(candidate -> isEligible(ownerId, candidate))
                .map(PublicShopCandidate::shopId)
                .distinct()
                .sorted()
                .toList();
    }
}
