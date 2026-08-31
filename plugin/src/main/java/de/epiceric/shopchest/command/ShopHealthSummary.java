package de.epiceric.shopchest.command;

import java.util.Collection;

/** Whole-list counts derived from read-only shop health snapshots. */
record ShopHealthSummary(
        int healthy,
        int attention,
        int outOfStock,
        int full,
        int blocked,
        int unavailable,
        int unchecked
) {

    static ShopHealthSummary summarize(Collection<ShopListHealth> healthSnapshots) {
        int healthy = 0;
        int attention = 0;
        int outOfStock = 0;
        int full = 0;
        int blocked = 0;
        int unavailable = 0;
        int unchecked = 0;

        for (ShopListHealth health : healthSnapshots) {
            if (health.healthy()) {
                healthy++;
            }
            if (health.needsAttention()) {
                attention++;
            }
            if (health.outOfStock()) {
                outOfStock++;
            }
            if (health.full()) {
                full++;
            }
            if (health.blocked()) {
                blocked++;
            }
            if (health.unavailable()) {
                unavailable++;
            }
            if (health.unchecked()) {
                unchecked++;
            }
        }

        return new ShopHealthSummary(
                healthy,
                attention,
                outOfStock,
                full,
                blocked,
                unavailable,
                unchecked);
    }
}
