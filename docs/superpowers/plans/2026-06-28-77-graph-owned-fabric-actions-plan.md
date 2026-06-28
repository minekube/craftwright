# Graph-Owned Fabric Actions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Fabric's public `actions()` projection derive from runtime graph operations instead of binding descriptors.

**Architecture:** Add a small Fabric-side projection helper from `RuntimeOperationNode` to `DriverActionDescriptor`. Keep `FabricActionBinding` as private execution code and keep generated operation invocation behavior intact.

**Tech Stack:** Kotlin/JVM, JUnit 5, Gradle via mise, Markdown.

---

### Task 1: Add Red Tests For Graph-Owned Public Actions

**Files:**
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [x] **Step 1: Add the failing source invariant test**

  Add a test named:

  ```kotlin
  @Test
  fun `fabric public actions are projected from runtime graph instead of binding descriptors`() {
      val gateway = RecordingFabricClientGateway()
      gateway.connected = true
      val backend =
          FabricDriverBackend.real(
              gateway = gateway,
              runtimeMetadataProvider = blockQueryRuntimeMetadataProvider(),
          )

      val actions = backend.actions("alice")

      assertTrue(actions.any { it.id == "player.query" })
      assertTrue(actions.any { it.id == "inventory.equip" })
      assertTrue(actions.any { it.id == "world.block.break" })
      assertTrue(actions.all { it.source == DriverActionSource.RUNTIME_PROBE })
      assertEquals(DriverActionAvailability.AVAILABLE, actions.single { it.id == "player.query" }.availability)
      assertEquals(null, actions.single { it.id == "player.query" }.availabilityReason)
      assertEquals("integer", actions.single { it.id == "inventory.equip" }.arguments["slot"]?.type)
      assertEquals(true, actions.single { it.id == "inventory.equip" }.arguments["slot"]?.required)
      assertEquals("object", actions.single { it.id == "player.query" }.result.properties["data"]?.type)
  }
  ```

- [x] **Step 2: Run the red test**

  Run:

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric public actions are projected from runtime graph instead of binding descriptors*'
  ```

  Expected: test fails because connected Fabric public actions still expose
  `DriverActionSource.BINDING`.

  Evidence: command failed as expected with an assertion failure at the new
  source invariant.

### Task 2: Project Public Actions From Runtime Graph

**Files:**
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt`

- [x] **Step 1: Implement runtime graph action projection**

  Change `actions(clientId)` to:

  ```kotlin
  override fun actions(clientId: String): List<DriverActionDescriptor> =
      runtimeGraph(clientId).operations.sortedBy { operation -> operation.id }.map { operation -> operation.toDriverActionDescriptor() }
  ```

  Add private helpers in the same file:

  ```kotlin
  private fun RuntimeOperationNode.toDriverActionDescriptor(): DriverActionDescriptor =
      DriverActionDescriptor(
          id = id,
          schemaVersion = "1",
          arguments = arguments.mapValues { (_, schema) -> schema.toDriverActionArgument() },
          result = result.toDriverActionResultDescriptor(),
          source = DriverActionSource.RUNTIME_PROBE,
          availability = availability.toDriverActionAvailability(),
          availabilityReason = availability.reason,
      )

  private fun RuntimeSchema.toDriverActionArgument(): DriverActionArgument =
      DriverActionArgument(
          type = type,
          required = required,
          properties = properties.mapValues { (_, schema) -> schema.toDriverActionArgument() },
          items = items?.toDriverActionArgument(),
      )

  private fun RuntimeSchema.toDriverActionResultDescriptor(): DriverActionResultDescriptor =
      DriverActionResultDescriptor(
          properties =
              mapOf(
                  "action" to DriverActionResultProperty("string"),
                  "status" to DriverActionResultProperty("string"),
                  "message" to DriverActionResultProperty("string"),
                  "data" to toDriverActionResultProperty(),
              ),
          required = listOf("action", "status"),
      )

  private fun RuntimeSchema.toDriverActionResultProperty(): DriverActionResultProperty =
      DriverActionResultProperty(
          type = type,
          properties = properties.mapValues { (_, schema) -> schema.toDriverActionResultProperty() },
          items = items?.toDriverActionResultProperty(),
      )

  private fun RuntimeAvailability.toDriverActionAvailability(): DriverActionAvailability =
      when (state) {
          RuntimeAvailabilityState.AVAILABLE -> DriverActionAvailability.AVAILABLE
          RuntimeAvailabilityState.UNAVAILABLE -> DriverActionAvailability.UNAVAILABLE
      }
  ```

  Add imports for `DriverActionArgument`, `DriverActionAvailability`,
  `DriverActionResultDescriptor`, `DriverActionResultProperty`,
  `DriverActionSource`, `RuntimeAvailability`, `RuntimeOperationNode`, and
  `RuntimeSchema` if they are not already present.

- [x] **Step 2: Run the red test again**

  Run:

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric public actions are projected from runtime graph instead of binding descriptors*'
  ```

  Expected: test passes.

  Evidence: command passed after projecting `actions()` from runtime graph
  operations.

### Task 3: Update Existing Source Assertions And Guards

**Files:**
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`

- [x] **Step 1: Update connected-action source expectations**

  Replace connected-state assertions that expect `DriverActionSource.BINDING`
  for public Fabric actions returned by `backend.actions("alice")` with
  `DriverActionSource.RUNTIME_PROBE`.

- [x] **Step 2: Keep transitional binding guard precise**

  Update the transitional binding guard text to say the hand-written bindings
  are private execution adapters and remain represented by runtime graph
  operations. Do not remove the allowlist in this phase.

- [x] **Step 3: Add Phase 77 docs/checklist entries**

  Add Phase 77 to `AGENTS.md` and add a Phase 77 checklist section stating
  that public Fabric action descriptors are graph-owned but the broader
  binding-exit goal remains active until descriptor schemas and new gameplay
  breadth no longer depend on hand-maintained bootstrap code.

### Task 4: Verify And Push

**Files:**
- Modify: `docs/superpowers/plans/2026-06-28-77-graph-owned-fabric-actions-plan.md`
- Create: `docs/superpowers/evidence/2026-06-28-graph-owned-fabric-actions.md`

- [x] **Step 1: Run focused regression tests**

  Run:

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric runtime discovery probes client state before advertising unavailable raycast*' --tests '*FabricDriverModuleTest.fabric runtime discovery exposes player query only from client state*' --tests '*FabricDriverModuleTest.fabric runtime discovery exposes inventory equip only from client state*'
  ```

  Expected: `BUILD SUCCESSFUL`.

  Evidence: focused regression command passed after updating the fake gateway's
  metadata-query handling.

- [x] **Step 2: Run final local verification**

  Run:

  ```sh
  git diff --check
  mise run architecture-check
  mise run ci
  ```

  Expected: all commands exit `0`.

  Evidence: `git diff --check`, `mise run architecture-check`, and
  `mise run ci` passed locally.

- [x] **Step 3: Record evidence**

  Create `docs/superpowers/evidence/2026-06-28-graph-owned-fabric-actions.md`
  with the red test result, green test result, focused regression result, local
  verification result, and a note that this phase adds no gameplay actions or
  support claims.

- [ ] **Step 4: Commit and push**

  Run:

  ```sh
  git add AGENTS.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-77-graph-owned-fabric-actions-design.md docs/superpowers/plans/2026-06-28-77-graph-owned-fabric-actions-plan.md docs/superpowers/evidence/2026-06-28-graph-owned-fabric-actions.md driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt
  git commit -m "driver-fabric: project actions from runtime graph"
  git push origin main
  ```

- [ ] **Step 5: Verify remote CI**

  Run:

  ```sh
  gh run list --repo minekube/craftless --branch main --limit 5 --json databaseId,headSha,status,conclusion,name,event,createdAt
  gh run watch <latest-run-id> --repo minekube/craftless --exit-status
  ```

  Expected: pushed commit's GitHub Actions CI exits successfully.
