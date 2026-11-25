package io.argus.core.inspection;

/**
 * Deep module interface for payload inspection: entropy scanning, PII heuristics,
 * and OpenAPI schema inference behind a single seam.
 */
public interface PayloadInspector {
    InspectionReport inspect(InspectableTransaction transaction);

    static PayloadInspector createDefault() {
        return new DefaultPayloadInspector();
    }
}
