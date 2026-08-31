package de.epiceric.shopchest.diagnostics;

import java.util.List;

/** Completed point-in-time audit for the selected record scope. */
public record ShopAuditReport(
        List<ShopAuditFinding> findings,
        List<ShopAuditFinding> reviewFindings,
        ShopAuditSummary summary
) {

    public ShopAuditReport {
        findings = List.copyOf(findings);
        reviewFindings = List.copyOf(reviewFindings);
        if (summary == null) {
            throw new IllegalArgumentException("Audit summary must not be null");
        }
    }

    public static ShopAuditReport from(List<ShopAuditFinding> findings) {
        final List<ShopAuditFinding> ordered = findings.stream()
                .sorted(java.util.Comparator.comparingLong(
                        finding -> finding.record().rowNumber()))
                .toList();
        final List<ShopAuditFinding> reviewFindings = ordered.stream()
                .filter(ShopAuditFinding::needsReview)
                .toList();
        return new ShopAuditReport(
                ordered,
                reviewFindings,
                ShopAuditSummary.summarize(ordered));
    }
}
