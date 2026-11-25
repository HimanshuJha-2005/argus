package io.argus.agent;

import io.argus.core.ringbuffer.TelemetryRingBuffer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DispatcherServletAdviceTest {

    private TelemetryRingBuffer<CapturedTransaction> ringBuffer;

    @BeforeEach
    void setUp() {
        ringBuffer = TelemetryRingBuffer.create(64);
        SensorBridge.registerRingBuffer(ringBuffer);
    }

    @AfterEach
    void tearDown() {
        SensorBridge.reset();
    }

    @Test
    @DisplayName("Should extract Spring Route Template from bestMatchingPattern attribute and emit CapturedTransaction")
    void shouldExtractRouteTemplateAndEmitTransaction() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(DispatcherServletAdvice.BEST_MATCHING_PATTERN_ATTR, "/api/v1/orders/{orderId}");

        HttpServletRequest fakeRequest = createFakeRequest("POST", "/api/v1/orders/ord_998124", attributes);
        HttpServletResponse fakeResponse = createFakeResponse(201);

        long startNanos = System.nanoTime() - 1_000_000L; // 1ms duration

        // When
        DispatcherServletAdvice.onExit(startNanos, fakeRequest, fakeResponse);

        // Then
        assertThat(ringBuffer.size())
                .as("Transaction must be offered to the ring buffer")
                .isEqualTo(1);

        List<CapturedTransaction> drained = new ArrayList<>();
        ringBuffer.drainTo(drained::add, 10);

        assertThat(drained).hasSize(1);
        CapturedTransaction tx = drained.getFirst();

        assertThat(tx.httpMethod()).isEqualTo("POST");
        assertThat(tx.routeTemplate()).isEqualTo("/api/v1/orders/{orderId}");
        assertThat(tx.path()).isEqualTo("/api/v1/orders/ord_998124");
        assertThat(tx.statusCode()).isEqualTo(201);
        assertThat(tx.durationNanos()).isGreaterThan(0L);
    }

    @Test
    @DisplayName("Should fallback to literal path when bestMatchingPattern attribute is absent")
    void shouldFallbackToLiteralPathWhenNoPatternFound() {
        HttpServletRequest fakeRequest = createFakeRequest("GET", "/actuator/health", Map.of());
        HttpServletResponse fakeResponse = createFakeResponse(200);

        DispatcherServletAdvice.onExit(System.nanoTime(), fakeRequest, fakeResponse);

        List<CapturedTransaction> drained = new ArrayList<>();
        ringBuffer.drainTo(drained::add, 10);

        assertThat(drained).hasSize(1);
        CapturedTransaction tx = drained.getFirst();
        assertThat(tx.routeTemplate()).isEqualTo("/actuator/health");
        assertThat(tx.path()).isEqualTo("/actuator/health");
    }

    // Helper dynamic proxies for lightweight servlet test fakes (no heavy servlet container needed)
    private static HttpServletRequest createFakeRequest(String method, String uri, Map<String, Object> attributes) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, methodObj, args) -> switch (methodObj.getName()) {
                    case "getMethod" -> method;
                    case "getRequestURI" -> uri;
                    case "getAttribute" -> attributes.get(args[0]);
                    default -> null;
                }
        );
    }

    private static HttpServletResponse createFakeResponse(int status) {
        return (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, methodObj, args) -> switch (methodObj.getName()) {
                    case "getStatus" -> status;
                    default -> null;
                }
        );
    }
}
