# Agent Alignment Guide: Argus

This file governs how all AI agents (running for Himanshu, Vansh, or Koustav) must operate within this repository.

---

## 1. Core Mission & Philosophy
* **What is Argus**: A 100% local-first, zero-overhead runtime security and living contract sentinel for microservices and AI agent tool calls.
* **The Rule**: **Zero AI Slop**. No hallucinated layers, no pass-through boilerplate, no fake abstractions. Build deep modules with small interfaces and high leverage (Ousterhout / Matt Pokrzywnicki principles).

---

## 2. Ubiquitous Language ([GLOSSARY.md](./GLOSSARY.md))
Agents working in this codebase **must** use canonical domain terminology:
* Use **Runtime Sensor** (never "agent" or "SDK").
* Use **Tool Caller** for external AI agents (Claude, GPT, DeepSeek) calling endpoints.
* Use **Route Template** for parameterized paths (e.g., `/orders/{id}`) vs **Path** for literal URIs.
* Use **Endpoint** for `METHOD + Route Template`.
* Use **Shadow Endpoint** for active endpoints missing from the committed Contract.
* Use **Contract Drift** for schema divergences on known Endpoints.
* Use **Telemetry Event** for structured event records emitted to the **Collector**.

---

## 3. Team Division of Labor

```
┌────────────────────────────────────────────────────────┐
│ Himanshu (CTO / Systems)                               │
│ • Scope: `argus-agent` (Java 21 ByteBuddy Agent)       │
│ • Scope: `argus-core` (Ring Buffer, Collector Engine)  │
├────────────────────────────────────────────────────────┤
│ Vansh (Head of Product & Data)                         │
│ • Scope: `argus-python` (FastAPI/ASGI Middleware)      │
│ • Scope: PII / Secret Entropy Scanners                 │
│ • Scope: `argus-ui` (React & Interactive Node Graph)   │
├────────────────────────────────────────────────────────┤
│ Koustav (Head of Growth & DevRel)                      │
│ • Scope: Documentation, Quickstart Guides              │
│ • Scope: SSIP 2.0 Pitch Decks & Demo Day Materials     │
│ • Scope: User Interview Scripts & Cold Outreach        │
└────────────────────────────────────────────────────────┘
```

---

## 4. Architectural Invariants ([ADR 0001](./docs/adr/0001-in-process-bytecode-instrumentation.md))
1. **In-Process Sensors**: All traffic is captured inside the runtime process (JVM / ASGI), *not* via external network proxies or eBPF.
2. **Zero-Blocking**: Telemetry offloading uses bounded, lock-free ring buffers. Under extreme load, events are dropped via atomic counters; user request threads are **never blocked or delayed**.
3. **Local-First Privacy**: No customer payloads or secrets ever leave the local machine/VPC. All analysis is in-process or local loopback (`127.0.0.1:4319` / `/tmp/argus.sock`).
4. **Wire Contract**: All Runtime Sensors (Java and Python) must emit `TelemetryEvent` structures conforming exactly to [docs/spec/0001-java-runtime-sensor.md](./docs/spec/0001-java-runtime-sensor.md#4-telemetry-event-wire-schema).

---

## 5. Testing & Implementation Rules
* **TDD Loop**: Red $\rightarrow$ Green on public module seams. Write the failing test first; verify behavior, never internal implementation details.
* **Dependencies**: Shaded namespaces for all agent dependencies (`io.argus.agent.shadow.*`). No dependency conflicts with user apps.
