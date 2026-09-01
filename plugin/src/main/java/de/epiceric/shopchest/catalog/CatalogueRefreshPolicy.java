package de.epiceric.shopchest.catalog;

import java.time.Duration;
import java.util.Objects;

/** Periodic refresh cadence and low-noise readiness announcement policy. */
final class CatalogueRefreshPolicy {

    private static final Duration DEFAULT_PERIODIC_REFRESH_INTERVAL = Duration.ofMinutes(15);

    private final Duration periodicRefreshInterval;

    private CatalogueRefreshPolicy(Duration periodicRefreshInterval) {
        this.periodicRefreshInterval = Objects.requireNonNull(
                periodicRefreshInterval, "periodicRefreshInterval");
        if (periodicRefreshInterval.isZero() || periodicRefreshInterval.isNegative()) {
            throw new IllegalArgumentException("periodicRefreshInterval must be positive");
        }
    }

    static CatalogueRefreshPolicy standard() {
        return new CatalogueRefreshPolicy(DEFAULT_PERIODIC_REFRESH_INTERVAL);
    }

    Duration periodicRefreshInterval() {
        return periodicRefreshInterval;
    }

    boolean shouldAnnounce(Integer previousEligibleListings, int eligibleListings) {
        if (eligibleListings < 0) {
            throw new IllegalArgumentException("eligibleListings cannot be negative");
        }
        return previousEligibleListings == null
                || previousEligibleListings.intValue() != eligibleListings;
    }
}
