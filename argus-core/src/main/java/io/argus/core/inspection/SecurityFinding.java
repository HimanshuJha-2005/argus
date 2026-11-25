package io.argus.core.inspection;

/**
 * A security vulnerability or data leak detected in a transaction.
 */
public record SecurityFinding(
        FindingCategory category,
        String description,
        String matchedSample,
        Severity severity
) {
    public SecurityFinding {
        if (category == null) throw new IllegalArgumentException("category must not be null");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("description must not be blank");
        if (severity == null) throw new IllegalArgumentException("severity must not be null");
    }
}
