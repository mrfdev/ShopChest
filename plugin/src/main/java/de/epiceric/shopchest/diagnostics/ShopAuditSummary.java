package de.epiceric.shopchest.diagnostics;

import java.util.Collection;
import java.util.Set;

/** Deduplicated record counts and overlapping reason counts for an audit. */
public record ShopAuditSummary(
        int scanned,
        int healthy,
        int attention,
        int unchecked,
        int reviewRows,
        int missingWorlds,
        int missingContainers,
        int unsupportedContainers,
        int blocked,
        int invalidProducts,
        int invalidRecords,
        int staleCandidates
) {

    private static final Set<ShopAuditIssue> UNSUPPORTED_ISSUES = Set.of(
            ShopAuditIssue.UNSUPPORTED_CONTAINER,
            ShopAuditIssue.INCOMPLETE_CONTAINER);
    private static final Set<ShopAuditIssue> INVALID_RECORD_ISSUES = Set.of(
            ShopAuditIssue.INVALID_OWNER,
            ShopAuditIssue.INVALID_SHOP_TYPE,
            ShopAuditIssue.INVALID_TERMS,
            ShopAuditIssue.INVALID_LOCATION,
            ShopAuditIssue.INVALID_RECORD);
    private static final Set<ShopAuditIssue> STALE_ISSUES = Set.of(
            ShopAuditIssue.CONFLICTING_RECORD,
            ShopAuditIssue.SHADOWED_RECORD,
            ShopAuditIssue.INACTIVE_RECORD);

    public static ShopAuditSummary summarize(Collection<ShopAuditFinding> findings) {
        final Accumulator accumulator = new Accumulator();
        for (ShopAuditFinding finding : findings) {
            accumulator.accept(finding);
        }
        return accumulator.toSummary();
    }

    /** Incremental summary used by the per-tick audit finalization phase. */
    public static final class Accumulator {
        private int scanned;
        private int healthy;
        private int attention;
        private int unchecked;
        private int reviewRows;
        private int missingWorlds;
        private int missingContainers;
        private int unsupportedContainers;
        private int blocked;
        private int invalidProducts;
        private int invalidRecords;
        private int staleCandidates;

        public void accept(ShopAuditFinding finding) {
            scanned++;
            if (finding.healthy()) {
                healthy++;
            }
            if (finding.needsAttention()) {
                attention++;
            }
            if (finding.unchecked()) {
                unchecked++;
            }
            if (finding.needsReview()) {
                reviewRows++;
            }

            final Set<ShopAuditIssue> issues = finding.issues();
            if (issues.contains(ShopAuditIssue.WORLD_UNAVAILABLE)) {
                missingWorlds++;
            }
            if (issues.contains(ShopAuditIssue.MISSING_CONTAINER)) {
                missingContainers++;
            }
            if (containsAny(issues, UNSUPPORTED_ISSUES)) {
                unsupportedContainers++;
            }
            if (issues.contains(ShopAuditIssue.BLOCKED_DISPLAY)) {
                blocked++;
            }
            if (issues.contains(ShopAuditIssue.INVALID_PRODUCT)) {
                invalidProducts++;
            }
            if (containsAny(issues, INVALID_RECORD_ISSUES)) {
                invalidRecords++;
            }
            if (containsAny(issues, STALE_ISSUES)) {
                staleCandidates++;
            }
        }

        public ShopAuditSummary toSummary() {
            return new ShopAuditSummary(
                    scanned,
                    healthy,
                    attention,
                    unchecked,
                    reviewRows,
                    missingWorlds,
                    missingContainers,
                    unsupportedContainers,
                    blocked,
                    invalidProducts,
                    invalidRecords,
                    staleCandidates);
        }
    }

    private static boolean containsAny(
            Set<ShopAuditIssue> actual,
            Set<ShopAuditIssue> candidates
    ) {
        for (ShopAuditIssue candidate : candidates) {
            if (actual.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
