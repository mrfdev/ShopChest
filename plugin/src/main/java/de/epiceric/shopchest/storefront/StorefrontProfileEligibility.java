package de.epiceric.shopchest.storefront;

/** Eligibility rule for initial profile publication versus retained profile editing. */
public final class StorefrontProfileEligibility {

    private StorefrontProfileEligibility() {
    }

    public static boolean canEdit(
            boolean retainedProfileExists,
            boolean catalogueReady,
            boolean hasEligibleScopedNormalListing
    ) {
        return retainedProfileExists
                || (catalogueReady && hasEligibleScopedNormalListing);
    }
}
