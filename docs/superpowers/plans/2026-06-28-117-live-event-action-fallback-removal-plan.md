# Live Event Action Fallback Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove static `chat`/`movement` to gameplay action-id fallback mapping from live event normalization.

**Architecture:** Treat `operationId` as the action/event authority. Raw event `type` values only become live event types when they are already valid Craftless action ids or stable system events.

**Tech Stack:** Kotlin/JVM daemon and protocol tests, Gradle through mise.

---

### Task 1: Add Red Architecture Guard

**Files:**
- Modify: `protocol/src/test/kotlin/com/minekube/craftless/protocol/NamespacePolicyTest.kt`

- [x] **Step 1: Add source guard**

  Add a test named
  `daemon live event normalization does not synthesize gameplay action ids`.

  Scan `daemon/src/main/kotlin/com/minekube/craftless/daemon/LocalSessionApiServer.kt`
  and reject `operationId ?: "player.chat"`, `operationId ?: "player.move"`,
  `type == "chat" ->`, and `type == "movement" ->`.

- [x] **Step 2: Run red test**

  ```sh
  mise exec -- gradle :protocol:test --tests '*NamespacePolicyTest.daemon live event normalization does not synthesize gameplay action ids*'
  ```

  Expected: fails before implementation because static fallbacks are still
  present.

### Task 2: Remove Fallback Mapping

**Files:**
- Modify: `daemon/src/main/kotlin/com/minekube/craftless/daemon/LocalSessionApiServer.kt`

- [x] **Step 1: Change event type ordering**

  Use raw `type` only if it is already a Craftless action id, then use
  `operationId`, then `system.error`, otherwise `system.event`.

- [x] **Step 2: Run focused green tests**

  ```sh
  mise exec -- gradle :protocol:test --tests '*NamespacePolicyTest.daemon live event normalization does not synthesize gameplay action ids*'
  mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.server streams filtered live client events as sse*' --tests '*LocalSessionApiServerTest.server invokes actions through json rpc with correlation ids*' --tests '*LocalSessionApiServerTest.server persists json rpc subscriptions as sse filters*'
  ```

### Task 3: Update Governance And Evidence

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/evidence/2026-06-28-live-event-action-fallback-removal.md`

- [x] **Step 1: Add Phase 117 to AGENTS**
- [x] **Step 2: Add Phase 117 checklist section**
- [x] **Step 3: Record red/green and local gate evidence**

### Task 4: Verify, Commit, Push

- [x] **Step 1: Run local gates**

  ```sh
  git diff --check
  mise run ci
  ```

- [x] **Step 2: Commit and push**

  ```sh
  git add AGENTS.md daemon/src/main/kotlin/com/minekube/craftless/daemon/LocalSessionApiServer.kt protocol/src/test/kotlin/com/minekube/craftless/protocol/NamespacePolicyTest.kt docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-117-live-event-action-fallback-removal-design.md docs/superpowers/plans/2026-06-28-117-live-event-action-fallback-removal-plan.md docs/superpowers/evidence/2026-06-28-live-event-action-fallback-removal.md
  git commit -m "fix: remove static live event action fallbacks"
  git push origin main
  ```

## Self-Review

- Spec coverage: source guard, event normalization authority, SSE/JSON-RPC
  regression, governance, and verification are covered.
- Placeholder scan: no TODO/TBD placeholders.
- Scope: no new gameplay action, generated route family, CLI gameplay catalog,
  Fabric binding, scenario shortcut, or support claim.
