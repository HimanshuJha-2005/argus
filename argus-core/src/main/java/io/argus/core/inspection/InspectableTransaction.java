package io.argus.core.inspection;

import java.util.Map;

/**
 * An HTTP transaction observed at the runtime sensor boundary.
 */
public record InspectableTransaction(
        String httpMethod,
        String routeTemplate,
        Map<String, String> headers,
        byte[] payloadBytes,
        int statusCode
) {
    public InspectableTransaction {
        if (httpMethod == null || httpMethod.isBlank()) {
            throw new IllegalArgumentException("httpMethod must not be blank");
        }
        if (routeTemplate == null || routeTemplate.isBlank()) {
            throw new IllegalArgumentException("routeTemplate must not be blank");
        }
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        payloadBytes = payloadBytes == null ? new byte[0] : payloadBytes.clone();
    }
}
