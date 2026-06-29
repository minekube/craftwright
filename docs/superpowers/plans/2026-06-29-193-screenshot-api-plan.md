# Screenshot API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a PR-ready screenshot API slice backed by generated per-client OpenAPI/action metadata and generic artifact serving.

**Architecture:** Keep screenshot capture in the runtime graph as `media.screenshot.capture`, so `POST /clients/{id}/media/screenshot:capture` remains a generated alias and `POST /clients/{id}:run` remains the generic fallback. Store returned media bytes under each client's Craftless-owned runtime artifacts directory and serve them through a generic guarded artifact route.

**Tech Stack:** Kotlin/JVM, Ktor Server/Client, kotlinx.serialization, JUnit 5/kotlin.test, Gradle through mise.

---

### Task 1: Protocol Projection

**Files:**
- Modify: `protocol/src/test/kotlin/com/minekube/craftless/protocol/OpenApiGenerationTest.kt`

- [x] **Step 1: Write the protocol projection test**

Add a test that creates a `RuntimeCapabilityGraph` with resource
`media.screenshot` and operation `media.screenshot.capture`, then asserts:
the generated path is `/clients/{id}/media/screenshot:capture`, the resource
is projected, and the result schema contains `artifact-id`, `media-type`,
`byte-size`, `sha256`, `width`, `height`, `created-at`, and `download-url`.

- [x] **Step 2: Verify behavior**

Run:

```sh
mise exec -- gradle :protocol:test --tests 'com.minekube.craftless.protocol.OpenApiGenerationTest' --rerun-tasks
```

Expected: pass if the existing runtime graph projection already supports the
shape.

### Task 2: Fake Driver Screenshot Operation

**Files:**
- Modify: `testkit/src/main/kotlin/com/minekube/craftless/testkit/FakeDriverSession.kt`
- Modify: `testkit/src/test/kotlin/com/minekube/craftless/testkit/FakeDriverSessionTest.kt`

- [x] **Step 1: Write failing fake-driver test**

Assert `FakeDriverSession("alice").runtimeGraph()` exposes
`media.screenshot` and `media.screenshot.capture`; invoking the action returns
`ACCEPTED` with the required result fields and no static route dependency.

- [x] **Step 2: Verify red**

Run:

```sh
mise exec -- gradle :testkit:test --tests 'com.minekube.craftless.testkit.FakeDriverSessionTest' --rerun-tasks
```

Expected: fail because the fake driver does not yet expose the screenshot
resource or action.

- [x] **Step 3: Implement fake operation**

Add the media resource, operation result schema, and deterministic invocation
result to `FakeDriverSession`.

- [x] **Step 4: Verify green**

Run the focused test, then `mise exec -- gradle :testkit:test`.

### Task 3: Generic Artifact Serving

**Files:**
- Create: `daemon/src/main/kotlin/com/minekube/craftless/daemon/ClientArtifactStore.kt`
- Modify: `daemon/src/main/kotlin/com/minekube/craftless/daemon/LocalSessionApiServer.kt`
- Modify: `daemon/src/main/kotlin/com/minekube/craftless/daemon/ClientSessionService.kt`
- Modify: `daemon/src/test/kotlin/com/minekube/craftless/daemon/LocalSessionApiServerTest.kt`

- [x] **Step 1: Write failing daemon route test**

Create a workspace-backed client, write
`instances/<instance>/runtime/artifacts/screenshot-1.png`, then assert
`GET /clients/alice/artifacts/screenshot-1.png` returns bytes and
`GET /clients/alice/artifacts/../secret.txt` is rejected.

- [x] **Step 2: Verify red**

Run:

```sh
mise exec -- gradle :daemon:test --tests 'com.minekube.craftless.daemon.LocalSessionApiServerTest' --rerun-tasks
```

Expected: fail because the route does not exist yet.

- [x] **Step 3: Implement artifact store and Ktor route**

Resolve artifact ids only as relative normalized handles below the client's
existing `instance.files.artifacts` directory. Return `404` for missing
clients or missing artifacts, `400` for invalid artifact ids, and serve bytes
with the detected content type when available.

- [x] **Step 4: Verify green**

Run the focused daemon test, then `mise exec -- gradle :daemon:test`.

### Task 4: Evidence And PR

**Files:**
- Modify: `docs/superpowers/phase-index.md`
- Create/modify: `docs/superpowers/evidence/2026-06-29-screenshot-api.md`

- [x] **Step 1: Record focused verification**

Record protocol, daemon, testkit, `git diff --check`, and any broader
verification command results.

- [x] **Step 2: Run completion verification**

Run focused tests and `git diff --check`. Run `mise run ci` if practical in
the worktree before PR.

- [ ] **Step 3: Commit, push, and open PR**

Commit the screenshot slice, push `codex/screenshot-api`, and create a PR
against `main`.
