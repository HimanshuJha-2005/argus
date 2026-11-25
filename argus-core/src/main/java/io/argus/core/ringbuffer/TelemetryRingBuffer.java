package io.argus.core.ringbuffer;

import java.util.function.Consumer;

/**
 * Deep module interface for high-throughput, bounded, lock-free telemetry buffering.
 * Guarantees zero-blocking on producer threads: saturated buffers drop events via
 * atomic counters rather than delaying user request threads.
 *
 * @param <T> the telemetry event type
 */
public interface TelemetryRingBuffer<T> {

    /**
     * Attempts to publish an event to the ring buffer.
     * Non-blocking: if the buffer is full, immediately returns false and increments dropped count.
     *
     * @param event the event to publish
     * @return true if successfully queued, false if dropped due to capacity saturation
     */
    boolean offer(T event);

    /**
     * Drains up to maxElements from the buffer to the provided consumer.
     *
     * @param consumer recipient for drained elements
     * @param maxElements maximum number of elements to drain
     * @return number of elements drained
     */
    int drainTo(Consumer<T> consumer, int maxElements);

    /**
     * @return current number of items buffered
     */
    int size();

    /**
     * @return maximum capacity of the ring buffer
     */
    int capacity();

    /**
     * @return total events successfully published since creation
     */
    long publishedCount();

    /**
     * @return total events dropped due to buffer saturation
     */
    long droppedCount();

    /**
     * Factory method to create a lock-free ring buffer with the specified capacity.
     *
     * @param capacity must be a positive power of two
     * @param <E> event type
     * @return a new TelemetryRingBuffer instance
     */
    static <E> TelemetryRingBuffer<E> create(int capacity) {
        return new LockFreeRingBuffer<>(capacity);
    }
}
