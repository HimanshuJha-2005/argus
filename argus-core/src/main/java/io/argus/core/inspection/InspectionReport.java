package io.argus.core.inspection;

import java.util.List;

/**
 * The inspection result produced by the deep module.
 */
public record InspectionReport(
        boolean hasLeaks,
        List<SecurityFinding> findings,
        String inferredSchema
) {
    public InspectionReport {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}
