package de.epiceric.shopchest.diagnostics;

import de.epiceric.shopchest.sql.ShopAuditRecord;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopAuditSummaryTest {

    @Test
    void deduplicatesAttentionAndCategoryCountsWhileKeepingUncheckedSeparate() {
        final List<ShopAuditFinding> findings = List.of(
                finding(1, ShopAuditFinding.Inspection.CHECKED, Set.of()),
                finding(2, ShopAuditFinding.Inspection.UNCHECKED, Set.of()),
                finding(3, ShopAuditFinding.Inspection.UNCHECKED,
                        Set.of(ShopAuditIssue.INVALID_PRODUCT)),
                finding(4, ShopAuditFinding.Inspection.CHECKED, Set.of(
                        ShopAuditIssue.MISSING_CONTAINER,
                        ShopAuditIssue.INVALID_OWNER,
                        ShopAuditIssue.INVALID_TERMS,
                        ShopAuditIssue.CONFLICTING_RECORD)),
                finding(5, ShopAuditFinding.Inspection.CHECKED, Set.of(
                        ShopAuditIssue.UNSUPPORTED_CONTAINER,
                        ShopAuditIssue.INCOMPLETE_CONTAINER,
                        ShopAuditIssue.BLOCKED_DISPLAY,
                        ShopAuditIssue.SHADOWED_RECORD,
                        ShopAuditIssue.INACTIVE_RECORD)));

        final ShopAuditSummary summary = ShopAuditSummary.summarize(findings);

        assertEquals(5, summary.scanned());
        assertEquals(1, summary.healthy());
        assertEquals(3, summary.attention());
        assertEquals(2, summary.unchecked());
        assertEquals(4, summary.reviewRows());
        assertEquals(0, summary.missingWorlds());
        assertEquals(1, summary.missingContainers());
        assertEquals(1, summary.unsupportedContainers());
        assertEquals(1, summary.blocked());
        assertEquals(1, summary.invalidProducts());
        assertEquals(1, summary.invalidRecords());
        assertEquals(2, summary.staleCandidates());
    }

    @Test
    void findingCopiesIssueSetsAndDoesNotCallUncheckedHealthy() {
        final EnumSet<ShopAuditIssue> source = EnumSet.of(
                ShopAuditIssue.WORLD_UNAVAILABLE);
        final ShopAuditFinding finding = finding(
                1,
                ShopAuditFinding.Inspection.UNAVAILABLE,
                source);
        source.add(ShopAuditIssue.INVALID_PRODUCT);

        assertEquals(Set.of(ShopAuditIssue.WORLD_UNAVAILABLE), finding.issues());
        assertTrue(finding.needsAttention());
        assertTrue(finding.needsReview());
        assertFalse(finding.healthy());
        assertFalse(finding.unchecked());
    }

    private ShopAuditFinding finding(
            int id,
            ShopAuditFinding.Inspection inspection,
            Set<ShopAuditIssue> issues
    ) {
        return new ShopAuditFinding(
                new ShopAuditRecord(
                        id,
                        String.valueOf(id),
                        "00000000-0000-0000-0000-000000000001",
                        "product",
                        "1",
                        "world",
                        String.valueOf(id),
                        "64",
                        String.valueOf(id),
                        "10",
                        "5",
                        "NORMAL"),
                inspection,
                issues);
    }
}
