# Graph-Owned Fabric Invoke Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route Fabric's generic `invoke(...)` compatibility path through runtime graph operations and operation adapters.

**Architecture:** Add a red test proving injected `FabricActionDiscovery` cannot make compatibility invoke succeed when the runtime graph marks an operation unavailable. Change `FabricDriverBackend.invoke(...)` to use `runtimeGraph(clientId).operations` plus `operationAdapters(clientId)` for dispatch, keeping `FabricActionBinding` only as private adapter implementation.

**Tech Stack:** Kotlin/JVM, Gradle via mise, Kotlin test, Markdown.

---

### Task 1: Add Red Tests For Graph-Owned Compatibility Invoke

**Files:**
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [x] **Step 1: Add graph-owned dispatch tests**

  Add:

  ```kotlin
  @Test
  fun `fabric backend dispatch does not depend on fabric action discovery`() {
      val source =
          Files.readString(
              repositoryRoot().resolve(
                  "driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt",
              ),
          )

      assertFalse(source.contains("FabricActionDiscovery"))
      assertFalse(source.contains("discoveredActions"))
  }

  @Test
  fun `fabric compatibility invoke dispatches unavailable operations from runtime graph`() {
      val gateway = RecordingFabricClientGateway()
      gateway.connected = false
      val backend =
          FabricDriverBackend.real(
              gateway = gateway,
              runtimeMetadataProvider = blockQueryRuntimeMetadataProvider(),
          )

      val result = backend.invoke("alice", DriverActionInvocation("player.raycast"))

      assertEquals(DriverActionStatus.UNSUPPORTED, result.status)
      assertEquals("client-not-connected", result.message)
      assertEquals(0, gateway.graphCapabilityProbeQueries)
      assertEquals(0, gateway.capabilityProbeQueries)
      assertEquals(emptyList(), gateway.actions)
  }

  @Test
  fun `fabric compatibility invoke adapters come from private binding map`() {
      val gateway = RecordingFabricClientGateway()
      gateway.connected = true
      val backend =
          FabricDriverBackend.real(
              gateway = gateway,
              runtimeMetadataProvider = blockQueryRuntimeMetadataProvider(),
          )

      val result =
          backend.invoke(
              "alice",
              DriverActionInvocation(
                  action = "player.chat",
                  arguments = mapOf("message" to JsonPrimitive("hello graph invoke")),
              ),
          )

      assertEquals(DriverActionStatus.ACCEPTED, result.status)
      assertEquals("hello graph invoke", result.message)
      assertEquals(2, gateway.graphCapabilityProbeQueries)
      assertEquals(0, gateway.capabilityProbeQueries)
      assertEquals(listOf("client-action"), gateway.actions)
  }
  ```

- [x] **Step 2: Run the red test**

  Run:

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric backend dispatch does not depend on fabric action discovery*' --tests '*FabricDriverModuleTest.fabric compatibility invoke dispatches unavailable operations from runtime graph*' --tests '*FabricDriverModuleTest.fabric compatibility invoke adapters come from private binding map*'
  ```

  Expected: FAIL before implementation because `FabricDriverBackend` still
  contains `FabricActionDiscovery`/`discoveredActions` and compatibility dispatch can
  be controlled by action discovery.

  Evidence: command failed as expected before the implementation. A connected
  override red test also failed while adapters still came from action
  discovery.

### Task 2: Route Compatibility Invoke Through Runtime Graph

**Files:**
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt`

- [x] **Step 1: Replace discovered-action lookup and adapter construction**

  Change `invoke(clientId, invocation)` so it:

  ```kotlin
  val operation = runtimeGraph(clientId).operations.firstOrNull { operation -> operation.id == invocation.action }
  if (operation == null) {
      return DriverActionResult(
          action = invocation.action,
          status = DriverActionStatus.UNSUPPORTED,
          message = "unsupported Fabric action ${invocation.action}",
      )
  }
  if (operation.availability.state == RuntimeAvailabilityState.UNAVAILABLE) {
      return DriverActionResult(
          action = invocation.action,
          status = DriverActionStatus.UNSUPPORTED,
          message = operation.availability.reason ?: "unavailable Fabric action ${invocation.action}",
      )
  }
  val adapters = operationAdapters(clientId)
  if (operation.adapter !in adapters.adapterKeys()) {
      return DriverActionResult(
          action = invocation.action,
          status = DriverActionStatus.UNSUPPORTED,
          message = "missing Fabric operation adapter ${operation.adapter}",
      )
  }
  return adapters.invoke(
      DriverOperationInvocation(
          clientId = clientId,
          operation = operation,
          arguments = invocation.arguments,
      ),
  )
  ```

  Change `operationAdapters(clientId)` so transitional adapters are built from
  the private `actionBindingsById` map instead of `discoveredActions(clientId)`,
  then remove `actionDiscovery` and `discoveredActions` from
  `FabricDriverBackend`.

- [x] **Step 2: Run the red test again**

  Run:

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric backend dispatch does not depend on fabric action discovery*' --tests '*FabricDriverModuleTest.fabric compatibility invoke dispatches unavailable operations from runtime graph*' --tests '*FabricDriverModuleTest.fabric compatibility invoke adapters come from private binding map*'
  ```

  Expected: PASS.

  Evidence: the focused graph-dispatch tests passed after routing invoke
  through runtime graph operations and private adapters.

### Task 3: Verify Existing Invoke Behavior

**Files:**
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [x] **Step 1: Run unavailable raycast regression**

  Run:

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric runtime discovery probes client state before advertising unavailable raycast*'
  ```

  Expected: PASS.

  Evidence: command passed.

- [x] **Step 2: Run full Fabric tests**

  Run:

  ```sh
  mise exec -- gradle :driver-fabric:test
  ```

  Expected: `BUILD SUCCESSFUL`.

  Evidence: command passed with `BUILD SUCCESSFUL`.

### Task 4: Update Governance And Evidence

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Modify: `docs/superpowers/plans/2026-06-28-79-graph-owned-fabric-invoke-plan.md`
- Create: `docs/superpowers/evidence/2026-06-28-graph-owned-fabric-invoke.md`

- [x] **Step 1: Add Phase 79 governance text**

  Add Phase 79 to the active sequence and state that generic invoke compatibility dispatch is
  graph-owned.

- [x] **Step 2: Add checklist section**

  Add a Phase 79 checklist section recording spec, plan, dispatch ownership,
  private binding status, non-goals, and verification commands.

- [x] **Step 3: Record evidence**

  Create an evidence note with red, green, local, push, and remote CI evidence.

  Evidence: `docs/superpowers/evidence/2026-06-28-graph-owned-fabric-invoke.md`
  records red/green test evidence and local gate evidence.

### Task 5: Final Verification And Push

**Files:**
- All modified files from previous tasks

- [x] **Step 1: Run final local gates**

  Run:

  ```sh
  git diff --check
  mise run architecture-check
  mise run ci
  ```

  Expected: all exit `0`.

  Evidence: `git diff --check`, `mise run architecture-check`, and
  `mise run ci` exited `0` locally.

- [x] **Step 2: Commit and push**

  Run:

  ```sh
  git add AGENTS.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-79-graph-owned-fabric-invoke-design.md docs/superpowers/plans/2026-06-28-79-graph-owned-fabric-invoke-plan.md docs/superpowers/evidence/2026-06-28-graph-owned-fabric-invoke.md driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricActionBindings.kt driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt
  git commit -m "driver-fabric: invoke through runtime graph"
  git push origin main
  ```

  Evidence: commit `a42d5680efc959ad19040f88ee173382f5efbf4d`
  (`driver-fabric: invoke through runtime graph`) was pushed to
  `origin/main`.

- [x] **Step 3: Verify remote CI**

  Run:

  ```sh
  gh run list --branch main --limit 5
  gh run watch <run-id> --exit-status
  ```

  Expected: pushed `main` CI passes.

  Evidence: GitHub Actions run `28310090098` passed for commit
  `a42d5680efc959ad19040f88ee173382f5efbf4d`.
