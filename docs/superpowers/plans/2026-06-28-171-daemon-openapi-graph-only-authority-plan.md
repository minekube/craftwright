# Daemon OpenAPI Graph-Only Authority Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> superpowers:subagent-driven-development or superpowers:executing-plans to
> implement this plan task-by-task. Keep the public API authority boundary:
> runtime graph first, generated OpenAPI second, projections after that.

**Goal:** Remove the daemon fallback that publishes public OpenAPI/actions from
`DriverSession.actions()` when `runtimeGraph()` is empty.

**Architecture:** `ClientSessionService.openApiFor(clientId)` must always call
`OpenApiDocument.fromRuntimeGraph`. Any test or fake session that needs public
actions must expose runtime graph operation nodes.

**Tech Stack:** Kotlin, Ktor, kotlinx.serialization JSON, Gradle through mise.

---

### Task 1: Add The Regression Test

**Files:**

- Modify:
  `daemon/src/test/kotlin/com/minekube/craftless/daemon/ClientSessionServiceTest.kt`

- [x] Add a descriptor-only fake driver session whose `actions()` returns
  `player.chat` and whose `runtimeGraph()` is empty.
- [x] Assert generated per-client OpenAPI has no actions/resources and no
  `/clients/{id}/player:chat` alias route.
- [x] Run the focused test before implementation and capture the red failure.

### Task 2: Remove The Fallback

**Files:**

- Modify:
  `daemon/src/main/kotlin/com/minekube/craftless/daemon/ClientSessionService.kt`

- [x] Remove the `graph.hasProjectionNodes()` branch.
- [x] Remove legacy descriptor-to-OpenAPI conversion helpers from the daemon.
- [x] Remove legacy action fingerprint helpers from the daemon.
- [x] Keep `routesFor(clientId)` as a projection of generated OpenAPI.

### Task 3: Convert Stale Test Fixtures

**Files:**

- Modify:
  `daemon/src/test/kotlin/com/minekube/craftless/daemon/ClientSessionServiceTest.kt`
- Modify:
  `daemon/src/test/kotlin/com/minekube/craftless/daemon/LocalSessionApiServerTest.kt`

- [x] Convert descriptor-backed test fixtures that still expect public actions
  to expose equivalent runtime graph operations.
- [x] Update duplicate-id expectations from legacy action-list errors to
  runtime graph duplicate operation errors.
- [x] Preserve generated result validation by carrying graph result payload
  requiredness into generated OpenAPI result metadata.

### Task 4: Verify And Record Evidence

**Files:**

- Modify: `docs/project-completion-checklist.md`
- Modify: `docs/superpowers/phase-index.md`
- Create:
  `docs/superpowers/evidence/2026-06-28-daemon-openapi-graph-only-authority.md`

- [x] Run focused daemon regression tests.
- [x] Run full daemon tests.
- [x] Run architecture and local CI gates.
- [x] Commit and push to `main`.
