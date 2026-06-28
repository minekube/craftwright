# Graph-Native Fabric Schemas Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the runtime graph schema dependency on transitional Fabric action bindings while preserving the current generated API shape.

**Architecture:** Add tests that fail while `FabricCapabilityProbeContext` receives `FabricActionBinding` maps. Replace descriptor-to-schema conversion in `FabricCapabilityProbe.kt` with graph-local `RuntimeSchema` helpers for the current bootstrap operations. Keep bindings only inside private execution adapter discovery.

**Tech Stack:** Kotlin/JVM, Gradle via mise, JUnit/Kotlin test, Markdown.

---

### Task 1: Add Red Tests For Binding-Free Graph Schemas

**Files:**
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricCapabilityProbeTest.kt`

- [x] **Step 1: Add the context boundary test**

  Add:

  ```kotlin
  @Test
  fun `fabric capability probe context does not receive action bindings for graph schemas`() {
      assertTrue(
          FabricCapabilityProbeContext::class.java.declaredFields.none { field ->
              field.name == "bindings" || field.type.name.contains("FabricActionBinding")
          },
      )
      assertTrue(
          FabricCapabilityProbeContext::class.java.constructors.none { constructor ->
              constructor.parameterTypes.any { type -> type.name.contains("FabricActionBinding") }
          },
      )
  }
  ```

- [x] **Step 2: Add the schema regression test**

  Add:

  ```kotlin
  @Test
  fun `fabric graph schemas stay available without binding descriptor fallback`() {
      val graph =
          defaultFabricCapabilityDiscovery(probes = listOf(FabricClientStateCapabilityProbe))
              .discover(
                  FabricCapabilityProbeContext(
                      clientId = "alice",
                      modeId = "metadata-only",
                      gateway = null,
                  ),
              )

      val operations = graph.operations.associateBy { it.id }

      assertEquals("string", operations.getValue("player.chat").arguments["message"]?.type)
      assertEquals(true, operations.getValue("player.chat").arguments["message"]?.required)
      assertEquals("integer", operations.getValue("player.move").arguments["ticks"]?.type)
      assertEquals("integer", operations.getValue("inventory.equip").arguments["slot"]?.type)
      assertEquals(true, operations.getValue("inventory.equip").arguments["slot"]?.required)
      assertEquals("number", operations.getValue("player.raycast").arguments["max-distance"]?.type)
      assertEquals("object", operations.getValue("world.block.break").arguments["target"]?.type)
      assertEquals("object", operations.getValue("world.block.interact").arguments["target"]?.type)
      assertEquals("object", operations.getValue("player.raycast").result.type)
      assertEquals("object", operations.getValue("player.raycast").result.properties["data"]?.type)
  }
  ```

- [x] **Step 3: Run the red test**

  Run:

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricCapabilityProbeTest.fabric capability probe context does not receive action bindings for graph schemas*'
  ```

  Expected: FAIL because `FabricCapabilityProbeContext` still has a `bindings`
  field.

  Evidence: command failed as expected with an assertion failure at
  `FabricCapabilityProbeTest.kt:102`.

### Task 2: Move Current Bootstrap Schemas Into The Graph Layer

**Files:**
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricCapabilityProbe.kt`
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt`

- [x] **Step 1: Remove binding input from probe context**

  Remove `val bindings: Map<String, FabricActionBinding> = emptyMap()` from
  `FabricCapabilityProbeContext`.

- [x] **Step 2: Stop passing bindings into graph discovery**

  In `FabricDriverBackend.runtimeGraph(clientId)`, remove:

  ```kotlin
  bindings = actionBindingsById,
  ```

- [x] **Step 3: Replace descriptor lookup with graph schema lookup**

  Replace the `operation(...)` helper so it uses:

  ```kotlin
  val schema = fabricRuntimeOperationSchema(id)
  ```

  and assigns `schema.arguments` and `schema.result` directly to
  `RuntimeOperationNode`.

- [x] **Step 4: Add graph-native schema helpers**

  Add a private `FabricRuntimeOperationSchema` data class and
  `fabricRuntimeOperationSchema(id)` helpers for:
  `player.query`, `player.chat`, `player.look`, `player.move`,
  `player.raycast`, `inventory.query`, `inventory.equip`, `world.time.query`,
  `world.block.break`, `world.block.interact`, `screen.query`, and
  `screen.close`.

- [x] **Step 5: Remove descriptor conversion helpers from the probe**

  Delete `actionDescriptor`, `fabricBootstrapDescriptor`, and the
  `DriverActionArgument`/`DriverActionResultDescriptor`/`DriverActionResultProperty`
  conversion helpers from `FabricCapabilityProbe.kt`.

### Task 3: Verify Graph Schema Behavior

**Files:**
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricCapabilityProbeTest.kt`

- [x] **Step 1: Run the red test again**

  Run:

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricCapabilityProbeTest.fabric capability probe context does not receive action bindings for graph schemas*'
  ```

  Expected: PASS.

  Evidence: command passed after removing `bindings` from the probe context.

- [x] **Step 2: Run the focused schema regression**

  Run:

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricCapabilityProbeTest.fabric graph schemas stay available without binding descriptor fallback*'
  ```

  Expected: PASS.

  Evidence: command passed after adding graph-local `RuntimeSchema` helpers.

- [x] **Step 3: Run full Fabric tests**

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
- Modify: `docs/superpowers/plans/2026-06-28-78-graph-native-fabric-schemas-plan.md`
- Create: `docs/superpowers/evidence/2026-06-28-graph-native-fabric-schemas.md`

- [x] **Step 1: Add Phase 78 to governance docs**

  Add Phase 78 to the active sequence and state that graph operation schemas
  must not be sourced from transitional bindings.

- [x] **Step 2: Add checklist section**

  Add a Phase 78 checklist section recording spec, plan, schema ownership,
  private binding status, non-goals, and verification commands.

- [x] **Step 3: Record evidence**

  Create an evidence note with the red test, green tests, local gates, commit,
  push, and remote CI result.

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
  `mise run ci` passed locally after the ktlint chain-wrap fix.

- [ ] **Step 2: Commit and push**

  Run:

  ```sh
  git add AGENTS.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-78-graph-native-fabric-schemas-design.md docs/superpowers/plans/2026-06-28-78-graph-native-fabric-schemas-plan.md docs/superpowers/evidence/2026-06-28-graph-native-fabric-schemas.md driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricCapabilityProbe.kt driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricCapabilityProbeTest.kt
  git commit -m "driver-fabric: own graph operation schemas"
  git push origin main
  ```

- [ ] **Step 3: Verify remote CI**

  Run:

  ```sh
  gh run list --branch main --limit 5
  gh run watch <run-id> --exit-status
  ```

  Expected: pushed `main` CI passes.
