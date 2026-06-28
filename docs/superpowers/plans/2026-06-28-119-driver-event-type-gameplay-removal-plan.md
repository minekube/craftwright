# Driver Event Type Gameplay Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove gameplay-specific `CHAT` and `MOVEMENT` values from the stable driver event enum.

**Architecture:** Keep `DriverEventType` for lifecycle/system driver events only. Accepted action invocations should be observed through daemon `SessionEvent`/SSE operation ids, not raw driver event enum values.

**Tech Stack:** Kotlin/JVM driver-api, testkit, daemon, Fabric self-attach tests; Gradle through mise.

---

### Task 1: Add Red Driver Event Enum Guard

**Files:**
- Modify: `driver-api/src/test/kotlin/com/minekube/craftless/driver/api/DriverSessionContractTest.kt`

- [x] **Step 1: Add enum guard**

  Add a test named
  `driver event types do not expose static gameplay categories`.

  ```kotlin
  val eventTypes = DriverEventType.entries.map { type -> type.name }.toSet()

  assertEquals(
      setOf("CLIENT_CREATED", "CLIENT_CONNECTED", "CLIENT_STOPPED", "ERROR"),
      eventTypes,
  )
  ```

- [x] **Step 2: Run red test**

  ```sh
  mise exec -- gradle :driver-api:test --tests '*DriverSessionContractTest.driver event types do not expose static gameplay categories*'
  ```

  Expected: fails before implementation because `CHAT` and `MOVEMENT` are still
  present.

### Task 2: Remove Gameplay Driver Event Values

**Files:**
- Modify: `driver-api/src/main/kotlin/com/minekube/craftless/driver/api/DriverSession.kt`
- Modify: `testkit/src/main/kotlin/com/minekube/craftless/testkit/FakeDriverSession.kt`
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/FabricDriverSelfAttachTest.kt`

- [x] **Step 1: Delete enum entries**

  Remove `CHAT` and `MOVEMENT` from `DriverEventType`.

- [x] **Step 2: Stop fake accepted action driver events**

  In `FakeDriverSession`, stop adding raw `DriverEvent` values for accepted
  chat and movement actions. Keep result messages unchanged.

- [x] **Step 3: Stop loopback accepted action driver events**

  In the self-attach test recording session, stop adding a raw `CHAT` event
  after accepted `player.chat` invocation.

### Task 3: Update Tests

**Files:**
- Modify: `testkit/src/test/kotlin/com/minekube/craftless/testkit/FakeDriverSessionTest.kt`
- Modify: `daemon/src/test/kotlin/com/minekube/craftless/daemon/ClientSessionServiceTest.kt`
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/FabricDriverSelfAttachTest.kt`

- [x] **Step 1: Update fake driver tests**

  Assert accepted movement returns an accepted result/message but does not add
  another raw driver event. Keep invalid movement and unsupported action error
  assertions.

- [x] **Step 2: Update daemon service test**

  Remove the expectation that invoking `player.chat` creates a raw
  `DriverEventType.CHAT` event.

- [x] **Step 3: Update Fabric loopback test**

  Expect the remote event list to contain lifecycle events only after accepted
  action invocation.

### Task 4: Run Focused Green Tests

- [x] **Step 1: Driver API guard**

  ```sh
  mise exec -- gradle :driver-api:test --tests '*DriverSessionContractTest.driver event types do not expose static gameplay categories*'
  ```

- [x] **Step 2: Fake and service regressions**

  ```sh
  mise exec -- gradle :testkit:test --tests '*FakeDriverSessionTest.*'
  mise exec -- gradle :daemon:test --tests '*ClientSessionServiceTest.created clients expose a driver session contract*'
  mise exec -- gradle :driver-fabric:test --tests '*FabricDriverSelfAttachTest.loopback endpoint exposes driver session contract*'
  ```

### Task 5: Update Governance And Evidence

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/evidence/2026-06-28-driver-event-type-gameplay-removal.md`

- [x] **Step 1: Add Phase 119 to AGENTS**
- [x] **Step 2: Add Phase 119 checklist section**
- [x] **Step 3: Record red/green and local gate evidence**

### Task 6: Verify, Commit, Push

- [x] **Step 1: Run local gates**

  ```sh
  git diff --check
  mise run ci
  ```

- [x] **Step 2: Commit and push**

  ```sh
  git add AGENTS.md driver-api/src/main/kotlin/com/minekube/craftless/driver/api/DriverSession.kt driver-api/src/test/kotlin/com/minekube/craftless/driver/api/DriverSessionContractTest.kt testkit/src/main/kotlin/com/minekube/craftless/testkit/FakeDriverSession.kt testkit/src/test/kotlin/com/minekube/craftless/testkit/FakeDriverSessionTest.kt daemon/src/test/kotlin/com/minekube/craftless/daemon/ClientSessionServiceTest.kt driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/FabricDriverSelfAttachTest.kt docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-119-driver-event-type-gameplay-removal-design.md docs/superpowers/plans/2026-06-28-119-driver-event-type-gameplay-removal-plan.md docs/superpowers/evidence/2026-06-28-driver-event-type-gameplay-removal.md
  git commit -m "fix: remove gameplay driver event enum values"
  git push origin main
  ```

## Self-Review

- Spec coverage: enum removal, fake/loopback cleanup, lifecycle/error
  preservation, governance, and verification are covered.
- Placeholder scan: no TODO/TBD placeholders.
- Scope: no new gameplay action, route family, CLI gameplay catalog, Fabric
  binding, scenario shortcut, replacement event enum, or support claim.
