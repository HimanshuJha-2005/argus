package io.argus.sample;

import io.argus.agent.ArgusAgent;
import io.argus.agent.CapturedTransaction;
import io.argus.agent.SensorBridge;
import io.argus.core.inspection.FindingCategory;
import io.argus.core.inspection.InspectableTransaction;
import io.argus.core.inspection.InspectionReport;
import io.argus.core.inspection.PayloadInspector;
import io.argus.core.inspection.Severity;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ArgusAgentIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private static final PayloadInspector PAYLOAD_INSPECTOR = PayloadInspector.createDefault();

    @BeforeAll
    static void attachArgusSensor() {
        // Dynamically attach the Argus bytecode agent before Spring servlet handling executes
        Instrumentation inst = ByteBuddyAgent.install();
        ArgusAgent.premain(null, inst);
    }

    @Test
    @DisplayName("GET /api/v1/orders/{orderId}: Sensor must capture transaction and resolve Route Template")
    void shouldCaptureCleanOrderRouteTemplate() {
        String url = "http://localhost:" + port + "/api/v1/orders/ord_987654";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        List<CapturedTransaction> transactions = drainCapturedTransactions();
        assertThat(transactions)
                .as("SensorBridge must have captured the transaction")
                .isNotEmpty();

        CapturedTransaction tx = transactions.stream()
                .filter(t -> t.path().equals("/api/v1/orders/ord_987654"))
                .findFirst()
                .orElseThrow();

        assertThat(tx.httpMethod()).isEqualTo("GET");
        assertThat(tx.routeTemplate()).isEqualTo("/api/v1/orders/{orderId}");
        assertThat(tx.statusCode()).isEqualTo(200);
        assertThat(tx.durationNanos()).isGreaterThan(0L);

        // Verify payload has no leaks
        InspectionReport report = PAYLOAD_INSPECTOR.inspect(new InspectableTransaction(
                tx.httpMethod(),
                tx.routeTemplate(),
                Map.of(),
                response.getBody().getBytes(StandardCharsets.UTF_8),
                tx.statusCode()
        ));
        assertThat(report.hasLeaks()).isFalse();
    }

    @Test
    @DisplayName("GET /api/v1/internal/dump: Sensor must capture route and PayloadInspector must flag live Stripe secret leak")
    void shouldCaptureAndDetectLiveSecretLeakInDump() {
        String url = "http://localhost:" + port + "/api/v1/internal/dump";
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("sk" + "_live_dummy");

        List<CapturedTransaction> transactions = drainCapturedTransactions();
        CapturedTransaction tx = transactions.stream()
                .filter(t -> t.path().equals("/api/v1/internal/dump"))
                .findFirst()
                .orElseThrow();

        assertThat(tx.routeTemplate()).isEqualTo("/api/v1/internal/dump");

        // Warm up JIT for line-rate benchmark
        InspectableTransaction txToInspect = new InspectableTransaction(
                tx.httpMethod(),
                tx.routeTemplate(),
                Map.of(),
                response.getBody().getBytes(StandardCharsets.UTF_8),
                tx.statusCode()
        );
        PAYLOAD_INSPECTOR.inspect(txToInspect);

        // Measure steady-state inspection latency
        long start = System.nanoTime();
        InspectionReport report = PAYLOAD_INSPECTOR.inspect(txToInspect);
        long latencyNanos = System.nanoTime() - start;

        assertThat(report.hasLeaks()).isTrue();
        assertThat(report.findings()).hasSize(1);
        assertThat(report.findings().getFirst().category()).isEqualTo(FindingCategory.SECRET_LEAK);
        assertThat(report.findings().getFirst().severity()).isEqualTo(Severity.CRITICAL);
        assertThat(report.findings().getFirst().matchedSample()).contains("sk_live_");

        // Verify sub-millisecond execution (< 1ms = 1,000,000 nanos)
        assertThat(latencyNanos)
                .as("Secret leak detection must execute in under 1 millisecond on warm path")
                .isLessThan(1_000_000L);
    }

    @Test
    @DisplayName("POST /api/v1/users/{userId}: Sensor captures POST route template and infers schema")
    void shouldCapturePostRouteAndInferSchema() {
        String url = "http://localhost:" + port + "/api/v1/users/usr_alpha_99";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String requestBody = """
                {
                    "name": "Alice Developer",
                    "email": "alice@startup.io",
                    "role": "SYSTEMS_LEAD"
                }
                """;

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(201);

        List<CapturedTransaction> transactions = drainCapturedTransactions();
        CapturedTransaction tx = transactions.stream()
                .filter(t -> t.path().equals("/api/v1/users/usr_alpha_99"))
                .findFirst()
                .orElseThrow();

        assertThat(tx.httpMethod()).isEqualTo("POST");
        assertThat(tx.routeTemplate()).isEqualTo("/api/v1/users/{userId}");
        assertThat(tx.statusCode()).isEqualTo(201);

        // Verify request payload schema inference
        InspectionReport requestReport = PAYLOAD_INSPECTOR.inspect(new InspectableTransaction(
                "POST",
                "/api/v1/users/{userId}",
                Map.of(),
                requestBody.getBytes(StandardCharsets.UTF_8),
                201
        ));

        assertThat(requestReport.hasLeaks()).isFalse();
        assertThat(requestReport.inferredSchema())
                .contains("\"type\":\"object\"")
                .contains("\"properties\":")
                .contains("\"name\":{\"type\":\"string\"}")
                .contains("\"email\":{\"type\":\"string\"}");
    }

    private static List<CapturedTransaction> drainCapturedTransactions() {
        List<CapturedTransaction> list = new ArrayList<>();
        if (SensorBridge.getRingBuffer() != null) {
            SensorBridge.getRingBuffer().drainTo(list::add, 100);
        }
        return list;
    }
}
