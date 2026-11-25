# Argus Domain Model

The ubiquitous language for Argus: runtime API observability, contract drift enforcement, and secret leak detection.

## Language

### Topology & Routing

**Endpoint**:
The combination of an HTTP method and a parameterized Route Template (e.g., `GET /api/v1/users/{id}`).
_Avoid_: Route, API, URL, URI

**Route Template**:
The parameterized path pattern defined by the backend framework router (e.g., `/orders/{orderId}/items`).
_Avoid_: Path, URI pattern, route

**Path**:
The concrete, literal request URI observed over the wire (e.g., `/orders/ord_9821/items`).
_Avoid_: URL, route

**Shadow Endpoint**:
An Endpoint actively serving runtime traffic that has no corresponding declaration in the committed Contract.
_Avoid_: Undocumented API, zombie endpoint, rogue route

---

### Contracts & Drift

**Contract**:
The authoritative OpenAPI 3.1 document defining the declared interface and schema of a service.
_Avoid_: Spec, Swagger, API definition

**Contract Drift**:
Any measurable divergence between observed runtime traffic and the committed Contract, including undocumented fields, type mismatches, or status code changes.
_Avoid_: Schema drift, breaking change

**Payload Schema**:
The structural definition (types, required fields, nesting) inferred from request or response bodies.
_Avoid_: JSON schema, model, body structure

---

### Security & Privacy

**Secret**:
High-entropy cryptographic material or credentials (API keys, private tokens, connection strings) observed in headers or payloads.
_Avoid_: Token, password, key

**PII**:
Personally Identifiable Information (emails, phone numbers, government IDs, payment card numbers) observed in plaintext payloads.
_Avoid_: Sensitive data, user info, customer data

**Leak**:
The appearance of an unmasked Secret or PII in an unauthorized context, such as a public response body, URL query parameter, or unencrypted external agent tool call.
_Avoid_: Breach, exposure, spill

---

### System Components

**Runtime Sensor**:
The in-process instrumentation agent (Java Agent or Python ASGI middleware) embedded within the target service runtime.
_Avoid_: Agent (ambiguous with AI Agent), SDK, interceptor

**Collector**:
The local or in-network daemon that ingests Telemetry Events from Runtime Sensors to infer schemas and evaluate security rules.
_Avoid_: Server, backend, daemon

**Telemetry Event**:
The standardized, structured record emitted by a Runtime Sensor containing Endpoint metadata, sanitized schemas, and timing.
_Avoid_: Log, trace, span, packet

**Tool Caller**:
An external automated client (specifically an autonomous AI Agent such as Claude or GPT) invoking an Endpoint via function or tool calling.
_Avoid_: Agent, AI, client, user
