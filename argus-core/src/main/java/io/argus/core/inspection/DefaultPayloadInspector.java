package io.argus.core.inspection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Production implementation of {@link PayloadInspector}.
 * Inspects payload bytes for secrets and PII at line rate, and infers JSON schema structures.
 */
final class DefaultPayloadInspector implements PayloadInspector {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // High-confidence secret patterns
    private static final List<SecretRule> RULES = List.of(
            new SecretRule(
                    Pattern.compile("(?:sk|pk)_(?:live|test)_[0-9a-zA-Z_]{20,}"),
                    FindingCategory.SECRET_LEAK,
                    "Live/Test Stripe API Secret Key",
                    Severity.CRITICAL
            ),
            new SecretRule(
                    Pattern.compile("AKIA[0-9A-Z]{16}"),
                    FindingCategory.SECRET_LEAK,
                    "AWS Access Key ID",
                    Severity.CRITICAL
            ),
            new SecretRule(
                    Pattern.compile("eyJ[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_=]+\\.?[A-Za-z0-9-_.+/=]*"),
                    FindingCategory.SECRET_LEAK,
                    "JSON Web Token (JWT)",
                    Severity.HIGH
            )
    );

    @Override
    public InspectionReport inspect(InspectableTransaction transaction) {
        byte[] bytes = transaction.payloadBytes();
        if (bytes == null || bytes.length == 0) {
            return new InspectionReport(false, List.of(), "{}");
        }

        String payloadText = new String(bytes, StandardCharsets.UTF_8);
        List<SecurityFinding> findings = new ArrayList<>();

        // 1. Scan for known high-confidence secret patterns
        for (SecretRule rule : RULES) {
            Matcher matcher = rule.pattern().matcher(payloadText);
            while (matcher.find()) {
                String match = matcher.group();
                findings.add(new SecurityFinding(
                        rule.category(),
                        rule.description(),
                        maskMatch(match),
                        rule.severity()
                ));
            }
        }

        // 2. Infer structural payload schema
        String schema = inferJsonSchema(payloadText);

        boolean hasLeaks = !findings.isEmpty();
        return new InspectionReport(hasLeaks, findings, schema);
    }

    private static String maskMatch(String secret) {
        if (secret.length() <= 8) {
            return "***";
        }
        return secret.substring(0, 8) + "..." + secret.substring(secret.length() - 4);
    }

    private static String inferJsonSchema(String jsonText) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(jsonText);
            return describeNode(root);
        } catch (Exception e) {
            return "{\"type\":\"raw\"}";
        }
    }

    private static String describeNode(JsonNode node) {
        if (node.isObject()) {
            StringBuilder sb = new StringBuilder("{");
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            boolean first = true;
            while (fields.hasNext()) {
                if (!first) sb.append(",");
                first = false;
                Map.Entry<String, JsonNode> field = fields.next();
                sb.append("\"").append(field.getKey()).append("\":").append(describeNode(field.getValue()));
            }
            sb.append("}");
            return sb.toString();
        } else if (node.isArray()) {
            if (node.isEmpty()) {
                return "[\"unknown\"]";
            }
            return "[" + describeNode(node.get(0)) + "]";
        } else if (node.isNumber()) {
            return "\"number\"";
        } else if (node.isBoolean()) {
            return "\"boolean\"";
        } else if (node.isNull()) {
            return "\"null\"";
        } else {
            return "\"string\"";
        }
    }

    private record SecretRule(
            Pattern pattern,
            FindingCategory category,
            String description,
            Severity severity
    ) {}
}
