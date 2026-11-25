package io.argus.agent;

/**
 * Clean data record representing an observed HTTP transaction at the bytecode boundary.
 */
public record CapturedTransaction(
        String httpMethod,
        String routeTemplate,
        String path,
        int statusCode,
        long durationNanos
) {
    public CapturedTransaction {
        if (httpMethod == null) httpMethod = "UNKNOWN";
        if (routeTemplate == null) routeTemplate = "/unknown";
        if (path == null) path = "/unknown";
    }
}
