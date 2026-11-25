package io.argus.agent;

import io.argus.core.ringbuffer.TelemetryRingBuffer;

/**
 * Fast in-process bridge between injected ByteBuddy advice bytecode and the core telemetry pipeline.
 * All methods are lock-free and thread-safe.
 */
public final class SensorBridge {

    private static volatile TelemetryRingBuffer<CapturedTransaction> ringBuffer;

    private SensorBridge() {}

    public static void registerRingBuffer(TelemetryRingBuffer<CapturedTransaction> buffer) {
        ringBuffer = buffer;
    }

    public static TelemetryRingBuffer<CapturedTransaction> getRingBuffer() {
        return ringBuffer;
    }

    public static boolean emitTransaction(
            String httpMethod,
            String routeTemplate,
            String path,
            int statusCode,
            long durationNanos
    ) {
        TelemetryRingBuffer<CapturedTransaction> buffer = ringBuffer;
        if (buffer == null) {
            return false;
        }
        return buffer.offer(new CapturedTransaction(
                httpMethod,
                routeTemplate,
                path,
                statusCode,
                durationNanos
        ));
    }

    public static void reset() {
        ringBuffer = null;
    }
}
