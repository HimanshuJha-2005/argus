package io.argus.core.ringbuffer;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

/**
 * High-performance lock-free ring buffer for in-process telemetry events.
 *
 * Concurrency Characteristics:
 * - Multi-Producer: Lock-free CAS on write sequence (tail).
 * - Single-Consumer: Lock-free read on sequence (head).
 * - Zero-Blocking Invariant: If saturated, drops incoming events via atomic counter,
 *   guaranteeing user request threads are never blocked or delayed.
 *
 * @param <T> event type
 */
final class LockFreeRingBuffer<T> implements TelemetryRingBuffer<T> {

    private final int capacity;
    private final int mask;
    private final AtomicReferenceArray<T> entries;

    private final AtomicLong tail = new AtomicLong(0);
    private final AtomicLong head = new AtomicLong(0);

    private final LongAdder publishedCount = new LongAdder();
    private final LongAdder droppedCount = new LongAdder();

    LockFreeRingBuffer(int capacity) {
        if (capacity <= 0 || (capacity & (capacity - 1)) != 0) {
            throw new IllegalArgumentException("Ring buffer capacity must be a positive power of two: " + capacity);
        }
        this.capacity = capacity;
        this.mask = capacity - 1;
        this.entries = new AtomicReferenceArray<>(capacity);
    }

    @Override
    public boolean offer(T event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }

        while (true) {
            long currentTail = tail.get();
            long currentHead = head.get();
            long currentSize = currentTail - currentHead;

            if (currentSize >= capacity) {
                // Buffer is saturated: drop immediately without blocking
                droppedCount.increment();
                return false;
            }

            if (tail.compareAndSet(currentTail, currentTail + 1)) {
                int index = (int) (currentTail & mask);
                entries.set(index, event);
                publishedCount.increment();
                return true;
            }
            // CAS contention: retry loop
        }
    }

    @Override
    public int drainTo(Consumer<T> consumer, int maxElements) {
        if (consumer == null) {
            throw new IllegalArgumentException("consumer must not be null");
        }
        if (maxElements <= 0) {
            return 0;
        }

        int drained = 0;
        while (drained < maxElements) {
            long currentHead = head.get();
            long currentTail = tail.get();

            if (currentHead >= currentTail) {
                break; // Buffer is empty
            }

            int index = (int) (currentHead & mask);
            T item = entries.get(index);
            if (item == null) {
                // Producer claimed slot but has not completed volatile set yet
                break;
            }

            // Clear entry to avoid memory retention and advance read cursor
            entries.set(index, null);
            head.lazySet(currentHead + 1);
            consumer.accept(item);
            drained++;
        }
        return drained;
    }

    @Override
    public int size() {
        long currentSize = tail.get() - head.get();
        if (currentSize < 0) return 0;
        if (currentSize > capacity) return capacity;
        return (int) currentSize;
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public long publishedCount() {
        return publishedCount.sum();
    }

    @Override
    public long droppedCount() {
        return droppedCount.sum();
    }
}
