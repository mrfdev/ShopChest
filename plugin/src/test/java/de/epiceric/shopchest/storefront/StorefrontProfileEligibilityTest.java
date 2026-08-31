package de.epiceric.shopchest.storefront;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorefrontProfileEligibilityTest {

    @Test
    void initialPublishingNeedsAReadyCatalogueAndScopedNormalListing() {
        assertFalse(StorefrontProfileEligibility.canEdit(false, false, false));
        assertFalse(StorefrontProfileEligibility.canEdit(false, true, false));
        assertTrue(StorefrontProfileEligibility.canEdit(false, true, true));
    }

    @Test
    void aRetainedDormantProfileRemainsEditableAfterItsLastListingIsRemoved() {
        assertTrue(StorefrontProfileEligibility.canEdit(true, false, false));
    }
}
