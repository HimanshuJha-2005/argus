# Specification: Component 1 — Java Runtime Sensor

* **Status**: Approved
* **Context**: `argus-sensor-java`
* **Target Runtimes**: Spring Boot 3.x (Spring Framework 6.x), Java 21 LTS
* **Compliance**: [GLOSSARY.md](../../GLOSSARY.md), [ADR 0001](../adr/0001-in-process-bytecode-instrumentation.md)

---

## 1. Scope & System Seam

The **Runtime Sensor** is an in-process Java Agent attached via `-javaagent:argus-agent.jar` at JVM startup. It observes HTTP transactions, extracts declared **Route Templates**, sanitizes payloads, and offloads structured **Telemetry Events** to the local **Collector** without blocking application request threads.

```
┌────────────────────────────────────────────────────────────────────────┐
│ Target JVM (Spring Boot 3)                                             │
│                                                                        │
│   Incoming HTTP Request                                                │
│         │                                                              │
│         ▼                                                              │
│   ┌──────────────────────────────────────────────────────────────┐     │
│   │ DispatcherServlet / Spring Filter Seam                       │     │
│   │ • Route Template Extraction (BEST_MATCHING_PATTERN)          │     │
│   │ • Auth Context Extraction (SecurityContextHolder)            │     │
│   └──────────────────────────────┬───────────────────────────────┘     │
│                                  │                                     │
│         ▼                        ▼                                     │
│   [Controller Method]     [Payload Capture Advice]                     │
│         │                        │                                     │
│         ▼                        ▼                                     │
│   Outgoing HTTP Response ──► [OnExit Advice]                           │
│                                  │                                     │
│                                  ▼                                     │
│             ┌──────────────────────────────────────────────┐           │
│             │ Lock-Free Ring Buffer (Bounded 16,384 Slots) │           │
│             └──────────────────────┬───────────────────────┘           │
└────────────────────────────────────┼───────────────────────────────────┘
                                     │ Loopback IPC (UDS or 127.0.0.1:4319)
                                     ▼
                   ┌───────────────────────────────────┐
                   │ Local Collector Daemon            │
                   └───────────────────────────────────┘
```

---

## 2. Bytecode Instrumentation Seam (Spring Boot 3)

### 2.1 Target Classes & Methods
The agent uses `net.bytebuddy.agent.builder.AgentBuilder` to intercept two execution points in Spring MVC:

1. **Route & Principal Resolution**:
   * **Target**: `org.springframework.web.servlet.DispatcherServlet.doDispatch(HttpServletRequest, HttpServletResponse)`
   * **Seam**: `@Advice.OnMethodExit(onThrowable = Throwable.class)`
   * **Extraction**:
     * **Route Template**: Extracted from request attribute `HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE`. If absent, defaults to literal **Path** flagged as unmapped.
     * **Endpoint**: Concat `request.getMethod()` + Route Template (e.g., `POST /api/v1/orders/{id}`).
     * **Principal Claims**: Read from `SecurityContextHolder.getContext().getAuthentication()` (extracts `name`, assigned roles/authorities, and token attributes without reflection overhead).

2. **Payload Inspection**:
   * **Target**: `org.springframework.http.converter.HttpMessageConverter.read(...)` and `write(...)` OR Servlet caching wrappers (`ContentCachingRequestWrapper`, `ContentCachingResponseWrapper`).
   * **Extraction**: Reads raw payload bytes capped at 64 KB per body. Payloads exceeding 64 KB are truncated with a byte-length indicator to prevent JVM heap spikes.

### 2.2 Classloader Isolation
* All internal dependencies (`net.bytebuddy`, protocol buffers/serializers) are shaded into the namespace `io.argus.agent.shadow.*`.
* The agent bootstrap jar lives on the system classloader; runtime advices delegate to an isolated `ArgusAgentClassLoader` to prevent host application dependency pollution.

---

## 3. High-Throughput Concurrency & Memory Model

### 3.1 Lock-Free Ring Buffer
To guarantee `< 0.3ms` request thread overhead:
* **Buffer Structure**: Bounded array-backed ring buffer (power-of-two size: 16,384 entries), initialized with pre-allocated reusable event objects to eliminate Garbage Collection allocation pressure.
* **Producer Thread (User Request Thread)**:
  * Computes timestamp, status code, duration nanos.
  * Calls `ringBuffer.tryPublish(slot)` using lock-free CAS sequence cursors.
  * **Zero-Blocking Invariant**: If the ring buffer is saturated (e.g. during a 100k RPS traffic spike), the event is dropped and an atomic `dropped_events_total` counter is incremented. User requests are never delayed or failed.
* **Consumer Thread (Background Daemon)**:
  * Single dedicated daemon thread pinned with high priority.
  * Batches events (batch size: 100 events or 10ms timeout) and streams them over the local loopback connection.

---

## 4. Telemetry Event Wire Schema

Each transaction emitted from the ring buffer adheres to the following record definition:

```protobuf
syntax = "proto3";
package io.argus.telemetry.v1;

message TelemetryEvent {
  string event_id = 1;
  int64 timestamp_epoch_ms = 2;
  int64 duration_nanos = 3;

  // Topology & Routing
  string http_method = 4;        // e.g. "POST"
  string route_template = 5;      // e.g. "/api/v1/users/{id}"
  string path = 6;                // e.g. "/api/v1/users/49102"
  int32 response_status = 7;      // e.g. 200

  // Security Context
  string principal_id = 8;        // Authenticated user/service ID
  repeated string roles = 9;      // Assigned authorities/roles

  // Payloads & Metadata
  map<string, string> request_headers = 10;
  map<string, string> response_headers = 11;
  bytes request_payload = 12;     // Capped at 64KB
  bytes response_payload = 13;    // Capped at 64KB
  bool payload_truncated = 14;
}
```

---

## 5. Fail-Safe Invariants

1. **Silent Failure on Exception**: Every ByteBuddy advice method is annotated with `@Advice.OnMethodEnter` and `@Advice.OnMethodExit` containing explicit `suppress = Throwable.class`. No internal sensor exception may escape into user application code.
2. **Circuit Breaker**: If internal sensor processing encounters more than 50 consecutive failures or GC pressure exceeds a 90% threshold, the sensor transitions to a dormant state (`BYPASS_MODE`) for 60 seconds before probing again.
3. **Local Loopback Isolation**: Emits strictly to a Unix Domain Socket (`/tmp/argus.sock` on Linux/macOS) or local loopback TCP (`127.0.0.1:4319` on Windows). No external network sockets are opened by the sensor.

---

## 6. Verification Criteria (Test Seams)

1. **Route Resolution Test**:
   * Request to `/users/9981` matching `@GetMapping("/users/{id}")` must emit a `TelemetryEvent` with `route_template = "/users/{id}"` and `path = "/users/9981"`.
2. **Under-Load Latency Test**:
   * Synthetic benchmark: 10,000 requests/sec with sensor attached vs uninstrumented baseline must demonstrate `< 0.3ms` latency difference at p99.
3. **Memory Non-Leak Test**:
   * Sustained high-throughput load over 1,000,000 requests must show zero growth in old-generation heap attributable to the agent ring buffer.
