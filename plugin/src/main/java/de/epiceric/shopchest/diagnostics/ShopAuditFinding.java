package de.epiceric.shopchest.diagnostics;

import de.epiceric.shopchest.sql.ShopAuditRecord;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Immutable result of inspecting one persisted shop record. */
public record ShopAuditFinding(
        ShopAuditRecord record,
        Inspection inspection,
        Set<ShopAuditIssue> issues
) {

    public ShopAuditFinding {
        if (record == null || inspection == null || issues == null) {
            throw new IllegalArgumentException("Audit finding values must not be null");
        }
        issues = issues.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(issues));
    }

    public boolean healthy() {
        return inspection == Inspection.CHECKED && issues.isEmpty();
    }

    public boolean needsAttention() {
        return !issues.isEmpty();
    }

    public boolean unchecked() {
        return inspection == Inspection.UNCHECKED;
    }

    public boolean needsReview() {
        return needsAttention() || unchecked();
    }

    public enum Inspection {
        CHECKED,
        UNCHECKED,
        UNAVAILABLE
    }
}
