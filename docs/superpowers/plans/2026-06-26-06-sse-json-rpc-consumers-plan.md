# SSE, JSON-RPC, And Adaptive Consumers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add SSE streams, JSON-RPC-style control, and adaptive CLI/helper event consumption.

**Architecture:** Ktor Server emits SSE for one-way live events. HTTP POST JSON-RPC-style endpoints handle control. CLI and Bun helper fetch live OpenAPI and subscribe to stream metadata.

**Tech Stack:** Ktor Server/Client, Kotlin/JVM, Bun through mise, kotlinx.serialization.

---

### Task 1: Protocol Event Models

**Files:**
- Create: `protocol/src/main/kotlin/com/minekube/craftless/protocol/LiveEventModels.kt`
- Test: `protocol/src/test/kotlin/com/minekube/craftless/protocol/LiveEventModelsTest.kt`

- [x] **Step 1: Add failing tests**

Assert event ids, event types, resource ids, correlation ids, filters, and JSON-RPC request/response envelopes validate as Craftless-owned contracts.

- [x] **Step 2: Implement models**

Add serializable SSE event and JSON-RPC envelope DTOs.

- [x] **Step 3: Verify**

Run: `mise exec -- gradle :protocol:test`

Expected: pass.

### Task 2: Daemon SSE And RPC

**Files:**
- Modify: `daemon/src/main/kotlin/com/minekube/craftless/daemon/LocalSessionApiServer.kt`
- Test: `daemon/src/test/kotlin/com/minekube/craftless/daemon/LocalSessionApiServerTest.kt`

- [x] **Step 1: Add failing daemon tests**

Assert `GET /clients/{id}/events:stream` streams filtered events and `POST /clients/{id}:rpc` returns JSON-RPC acknowledgements with correlation ids.

- [x] **Step 2: Implement Ktor SSE response**

Use Ktor response streaming with `text/event-stream`; do not introduce WebSocket unless later evidence requires it.

- [x] **Step 3: Verify**

Run: `mise exec -- gradle :daemon:test`

Expected: pass.

### Task 3: CLI And Helper Consumers

**Files:**
- Modify: `cli/src/main/kotlin/com/minekube/craftless/cli/Main.kt`
- Modify: `playwright/src/index.ts`
- Test: `cli/src/test/kotlin/com/minekube/craftless/cli/CraftlessCliTest.kt`
- Test: `playwright/src/index.test.ts`

- [x] **Step 1: Add failing consumer tests**

Assert CLI can watch events from live stream metadata and Bun helper can subscribe without npm/node tooling.

- [x] **Step 2: Implement adaptive consumers**

Add `craftless clients <id> events` and helper event stream support using Ktor Client/Bun fetch.

- [x] **Step 3: Verify**

Run: `mise exec -- gradle :cli:test && mise exec -- bun test playwright`

Expected: pass.
