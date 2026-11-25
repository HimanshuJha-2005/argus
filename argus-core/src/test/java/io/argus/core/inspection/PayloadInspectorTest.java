package io.argus.core.inspection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PayloadInspectorTest {

    private final PayloadInspector inspector = PayloadInspector.createDefault();

    @Test
    @DisplayName("Should detect high-severity secret leak when live API key appears in response payload")
    void shouldDetectLiveSecretKeyLeak() {
        // Given a Spring Boot endpoint response leaking a Stripe secret key
        String dummyLiveKey = "sk" + "_live_dummy_test_sample_token_998124";
        String jsonPayload = "{\n" +
                "    \"userId\": \"usr_99812\",\n" +
                "    \"status\": \"ACTIVE\",\n" +
                "    \"apiKey\": \"" + dummyLiveKey + "\"\n" +
                "}";

        InspectableTransaction transaction = new InspectableTransaction(
                "GET",
                "/api/v1/users/{id}",
                Map.of("Content-Type", "application/json"),
                jsonPayload.getBytes(StandardCharsets.UTF_8),
                200
        );

        // When
        InspectionReport report = inspector.inspect(transaction);

        // Then: Must flag leak and pinpoint the secret finding
        assertThat(report.hasLeaks())
                .as("Report should flag that a leak exists")
                .isTrue();

        assertThat(report.findings())
                .hasSize(1);

        SecurityFinding finding = report.findings().getFirst();
        assertThat(finding.category()).isEqualTo(FindingCategory.SECRET_LEAK);
        assertThat(finding.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(finding.matchedSample()).contains("sk_live_");
    }

    @Test
    @DisplayName("Should pass cleanly without leaks for normal clean payload")
    void shouldPassCleanPayload() {
        String cleanJson = """
                {
                    "userId": "usr_99812",
                    "username": "johndoe",
                    "role": "MEMBER"
                }
                """;

        InspectableTransaction transaction = new InspectableTransaction(
                "GET",
                "/api/v1/users/{id}",
                Map.of("Content-Type", "application/json"),
                cleanJson.getBytes(StandardCharsets.UTF_8),
                200
        );

        InspectionReport report = inspector.inspect(transaction);

        assertThat(report.hasLeaks()).isFalse();
        assertThat(report.findings()).isEmpty();
        assertThat(report.inferredSchema())
                .contains("\"type\":\"object\"")
                .contains("\"properties\":")
                .contains("\"username\":{\"type\":\"string\"}")
                .contains("\"userId\":{\"type\":\"string\"}")
                .contains("\"role\":{\"type\":\"string\"}");
    }
}
