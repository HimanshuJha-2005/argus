package io.argus.core.ringbuffer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelemetryRingBufferTest {

    @Test
    @DisplayName("Capacity must be a positive power of two")
    void shouldEnforcePowerOfTwoCapacity() {
        assertThatThrownBy(() -> TelemetryRingBuffer.create(100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("power of two");

        assertThatThrownBy(() -> TelemetryRingBuffer.create(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should publish and drain events in FIFO order")
    void shouldPublishAndDrainFIFO() {
        TelemetryRingBuffer<String> buffer = TelemetryRingBuffer.create(4);

        assertThat(buffer.offer("event-1")).isTrue();
        assertThat(buffer.offer("event-2")).isTrue();
        assertThat(buffer.size()).isEqualTo(2);
        assertThat(buffer.publishedCount()).isEqualTo(2);

        List<String> drained = new ArrayList<>();
        int count = buffer.drainTo(drained::add, 10);

        assertThat(count).isEqualTo(2);
        assertThat(drained).containsExactly("event-1", "event-2");
        assertThat(buffer.size()).isZero();
    }

    @Test
    @DisplayName("When buffer is saturated, should drop incoming events and increment dropped counter without blocking")
    void shouldDropEventsWhenSaturated() {
        TelemetryRingBuffer<String> buffer = TelemetryRingBuffer.create(2);

        assertThat(buffer.offer("event-1")).isTrue();
        assertThat(buffer.offer("event-2")).isTrue();

        // 3rd offer exceeds capacity of 2
        boolean accepted = buffer.offer("event-3");

        assertThat(accepted)
                .as("Saturated buffer must reject new offer immediately")
                .isFalse();

        assertThat(buffer.droppedCount()).isEqualTo(1);
        assertThat(buffer.publishedCount()).isEqualTo(2);

        // Verify the original elements remain safe
        List<String> drained = new ArrayList<>();
        buffer.drainTo(drained::add, 10);
        assertThat(drained).containsExactly("event-1", "event-2");
    }

    @Test
    @DisplayName("Multi-producer concurrent offer must preserve invariant: published + dropped == totalAttempts")
    void shouldHandleConcurrentProducersWithoutLoss() throws InterruptedException {
        int capacity = 1024;
        TelemetryRingBuffer<Integer> buffer = TelemetryRingBuffer.create(capacity);

        int threads = 8;
        int itemsPerThread = 1000;
        int totalOffers = threads * itemsPerThread;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startGate.await();
                    for (int i = 0; i < itemsPerThread; i++) {
                        buffer.offer(threadId * itemsPerThread + i);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertThat(endGate.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(buffer.publishedCount() + buffer.droppedCount())
                .as("Every attempted offer must be accounted for as published or dropped")
                .isEqualTo(totalOffers);

        AtomicInteger drainedCount = new AtomicInteger(0);
        buffer.drainTo(item -> drainedCount.incrementAndGet(), totalOffers);

        assertThat(drainedCount.get()).isEqualTo((int) buffer.publishedCount());
    }
}
