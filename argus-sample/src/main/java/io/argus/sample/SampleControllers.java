package io.argus.sample;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class SampleControllers {

    // 1. Clean endpoint testing parameterized Route Template resolution
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<Map<String, Object>> getOrder(@PathVariable("orderId") String orderId) {
        return ResponseEntity.ok(Map.of(
                "orderId", orderId,
                "item", "Enterprise Runtime License",
                "price", 499.0,
                "status", "CONFIRMED"
        ));
    }

    // 2. Leaky endpoint returning an unmasked test Stripe key
    @GetMapping("/internal/dump")
    public ResponseEntity<Map<String, String>> dumpInternalConfig() {
        String dummyStripeKey = "sk" + "_live_dummy_test_sample_token_998124";
        return ResponseEntity.ok(Map.of(
                "service", "billing-vault",
                "stripeKey", dummyStripeKey,
                "environment", "production"
        ));
    }

    // 3. POST endpoint testing request body schema inference and timing
    @PostMapping("/users/{userId}")
    public ResponseEntity<Map<String, Object>> createUser(
            @PathVariable("userId") String userId,
            @RequestBody Map<String, Object> payload
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "userId", userId,
                "status", "PROVISIONED",
                "receivedFields", payload.keySet()
        ));
    }
}
