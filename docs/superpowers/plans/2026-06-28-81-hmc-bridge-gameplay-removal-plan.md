# HMC Bridge Gameplay Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove static gameplay action exposure from the temporary HMC bridge so only Fabric runtime graph discovery can own gameplay APIs.

**Architecture:** Add failing runtime and bridge tests that require lifecycle-only HMC behavior. Delete HMC driver action descriptors/invocation branches and lower bridge gameplay helpers, then update docs/governance/evidence.

**Tech Stack:** Kotlin/JVM, Gradle via mise, Kotlin test, Markdown.

---

### Task 1: Add Red Runtime Test

**Files:**
- Modify: `driver-runtime/src/test/kotlin/com/minekube/craftless/driver/runtime/BackendDriverSessionTest.kt`

- [x] **Step 1: Replace the HMC action-adapter test**

  Replace `hmc bridge backend adapts the temporary bridge to runtime backend actions`
  with:

  ```kotlin
  @Test
  fun `hmc bridge backend is lifecycle only and exposes no gameplay actions`() {
      val backend = HmcBridgeDriverBackend(HmcBridgeBackend.dryRun())

      assertEquals(
          DriverBackendAction.CONNECT,
          backend.connect("alice", ConnectionTarget(host = "127.0.0.1", port = 25565)).action,
      )
      assertEquals(emptyList(), backend.actions("alice"))
      assertEquals("craftless-driver-bridge", backend.runtimeMetadata("alice").driver)
      assertEquals("bridge-evidence", backend.runtimeMetadata("alice").permissionsFingerprint)

      val chat =
          backend.invoke(
              "alice",
              DriverActionInvocation(
                  action = "player.chat",
                  arguments = mapOf("message" to JsonPrimitive("hello as action")),
              ),
          )
      val move =
          backend.invoke(
              "alice",
              DriverActionInvocation(
                  action = "player.move",
                  arguments = mapOf("forward" to JsonPrimitive(true), "ticks" to JsonPrimitive(20)),
              ),
          )

      assertEquals(DriverActionStatus.UNSUPPORTED, chat.status)
      assertEquals("unsupported action player.chat", chat.message)
      assertEquals(DriverActionStatus.UNSUPPORTED, move.status)
      assertEquals("unsupported action player.move", move.message)
      assertEquals(DriverBackendAction.STOP, backend.stop("alice").action)
  }
  ```

- [x] **Step 2: Add source guard**

  Add:

  ```kotlin
  @Test
  fun `hmc bridge backend has no static gameplay action catalog`() {
      val root = repositoryRoot()
      val backendSource =
          Files.readString(
              root.resolve(
                  "driver-runtime/src/main/kotlin/com/minekube/craftless/driver/runtime/HmcBridgeDriverBackend.kt",
              ),
          )

      assertFalse(backendSource.contains("bridgePlayer"))
      assertFalse(backendSource.contains("player.chat"))
      assertFalse(backendSource.contains("player.move"))
      assertFalse(backendSource.contains("MoveIntent"))
  }
  ```

- [x] **Step 3: Run red runtime tests**

  Run:

  ```sh
  mise exec -- gradle :driver-runtime:test --tests '*BackendDriverSessionTest.hmc bridge backend is lifecycle only and exposes no gameplay actions*' --tests '*BackendDriverSessionTest.hmc bridge backend has no static gameplay action catalog*'
  ```

  Expected: FAIL because the backend still exposes/invokes static gameplay
  actions.

### Task 2: Add Red Bridge Test

**Files:**
- Modify: `bridge-hmc/src/test/kotlin/com/minekube/craftless/bridge/hmc/HmcBridgeBackendTest.kt`

- [x] **Step 1: Replace gameplay tests with lifecycle-only tests**

  Replace the chat/move/jump/look tests with:

  ```kotlin
  @Test
  fun `bridge supports lifecycle actions without exposing hmc command names`() {
      val backend = HmcBridgeBackend.dryRun()

      val connect = backend.connect("alice", "127.0.0.1:25567")
      val stop = backend.stop("alice")

      assertEquals(ClientAction.CONNECT, connect.action)
      assertEquals(ClientAction.STOP, stop.action)
      assertFalse(connect.publicDescription.contains("hmc", ignoreCase = true))
      assertFalse(connect.publicDescription.contains("specifics", ignoreCase = true))
      assertTrue(connect.internalCommand.redacted().contains("<internal bridge command>"))
      assertTrue(stop.internalCommand.redacted().contains("<internal bridge command>"))
  }

  @Test
  fun `bridge backend source has no gameplay helpers`() {
      val source =
          java.nio.file.Files.readString(
              java.nio.file.Path
                  .of("")
                  .toAbsolutePath()
                  .let { start ->
                      generateSequence(start) { path -> path.parent }
                          .first { path -> java.nio.file.Files.exists(path.resolve("settings.gradle.kts")) }
                  }.resolve("bridge-hmc/src/main/kotlin/com/minekube/craftless/bridge/hmc/HmcBridgeBackend.kt"),
          )

      assertFalse(source.contains("fun chat"))
      assertFalse(source.contains("fun move"))
      assertFalse(source.contains("fun jump"))
      assertFalse(source.contains("fun look"))
      assertFalse(source.contains("MoveIntent"))
      assertFalse(source.contains("ClientAction.CHAT"))
      assertFalse(source.contains("ClientAction.MOVE"))
  }
  ```

- [x] **Step 2: Run red bridge tests**

  Run:

  ```sh
  mise exec -- gradle :bridge-hmc:test --tests '*HmcBridgeBackendTest.bridge backend source has no gameplay helpers*'
  ```

  Expected: FAIL because the bridge still exposes gameplay helpers.

### Task 3: Remove HMC Gameplay Code

**Files:**
- Modify: `driver-runtime/src/main/kotlin/com/minekube/craftless/driver/runtime/HmcBridgeDriverBackend.kt`
- Modify: `bridge-hmc/src/main/kotlin/com/minekube/craftless/bridge/hmc/HmcBridgeBackend.kt`
- Modify: `bridge-hmc/src/main/kotlin/com/minekube/craftless/bridge/hmc/RealClientSmokePlan.kt`

- [x] **Step 1: Make HMC driver lifecycle-only**

  Remove `actions(...)`, `invoke(...)`, `bridgePlayerMoveActionDescriptor`,
  `bridgePlayerChatActionDescriptor`, and gameplay helper imports from
  `HmcBridgeDriverBackend.kt`. Keep connect, stop, and runtime metadata.

- [x] **Step 2: Remove lower bridge gameplay helpers**

  Remove `chat`, `move`, `jump`, `look`, `MoveIntent`, and `ClientAction`
  values other than `CONNECT` and `STOP` from `HmcBridgeBackend.kt`.

- [x] **Step 3: Update bridge smoke plan wording**

  Remove `INVOKE_CHAT_ACTION`, `MOVE_FORWARD`, `ASSERT_CHAT_LOG`, and
  `ASSERT_POSITION_CHANGED` from `RealClientSmokePlan.kt`. Keep it as a
  lifecycle/launch evidence plan that starts the server, launches the client,
  starts the API, connects, asserts join, and collects artifacts.

### Task 4: Update Tests For Bridge Plan

**Files:**
- Modify: `bridge-hmc/src/test/kotlin/com/minekube/craftless/bridge/hmc/RealClientSmokePlanTest.kt`

- [x] **Step 1: Replace gameplay assertions**

  Update the test to assert lifecycle-only evidence:

  ```kotlin
  assertTrue(plan.steps.any { it.kind == SmokeStepKind.CONNECT_CLIENT })
  assertTrue(plan.steps.any { it.kind == SmokeStepKind.ASSERT_SERVER_JOIN })
  assertTrue(plan.steps.none { it.kind.name.contains("CHAT") })
  assertTrue(plan.steps.none { it.kind.name.contains("MOVE") })
  assertTrue(plan.steps.none { it.kind.name.contains("POSITION") })
  ```

### Task 5: Verify Focused Green

**Files:**
- Modified files from previous tasks

- [x] **Step 1: Run bridge tests**

  Run:

  ```sh
  mise exec -- gradle :bridge-hmc:test
  ```

  Expected: `BUILD SUCCESSFUL`.

- [x] **Step 2: Run runtime tests**

  Run:

  ```sh
  mise exec -- gradle :driver-runtime:test
  ```

  Expected: `BUILD SUCCESSFUL`.

### Task 6: Update Governance And Evidence

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Modify: `docs/bridge-limitations.md`
- Create: `docs/superpowers/evidence/2026-06-28-hmc-bridge-gameplay-removal.md`

- [x] **Step 1: Add Phase 81 governance**

  Add Phase 81 to the active sequence and state that HMC bridge is
  lifecycle-only.

- [x] **Step 2: Add checklist section**

  Add a Phase 81 checklist section recording lifecycle-only bridge behavior,
  removed static gameplay mappings, non-goals, and verification commands.

- [x] **Step 3: Update bridge docs**

  Change `docs/bridge-limitations.md` so it does not say HMC bridge accepts
  `player.move` or `player.chat`.

- [x] **Step 4: Record evidence**

  Create evidence with red, green, local, push, and remote CI sections.

### Task 7: Final Verification And Push

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

- [x] **Step 2: Commit and push**

  Run:

  ```sh
  git add AGENTS.md docs/project-completion-checklist.md docs/bridge-limitations.md docs/superpowers/specs/2026-06-28-81-hmc-bridge-gameplay-removal-design.md docs/superpowers/plans/2026-06-28-81-hmc-bridge-gameplay-removal-plan.md docs/superpowers/evidence/2026-06-28-hmc-bridge-gameplay-removal.md bridge-hmc/src/main/kotlin/com/minekube/craftless/bridge/hmc/HmcBridgeBackend.kt bridge-hmc/src/main/kotlin/com/minekube/craftless/bridge/hmc/RealClientSmokePlan.kt bridge-hmc/src/test/kotlin/com/minekube/craftless/bridge/hmc/HmcBridgeBackendTest.kt bridge-hmc/src/test/kotlin/com/minekube/craftless/bridge/hmc/RealClientSmokePlanTest.kt driver-runtime/src/main/kotlin/com/minekube/craftless/driver/runtime/HmcBridgeDriverBackend.kt driver-runtime/src/test/kotlin/com/minekube/craftless/driver/runtime/BackendDriverSessionTest.kt
  git commit -m "driver-runtime: remove hmc bridge gameplay actions"
  git push origin main
  ```

- [x] **Step 3: Verify remote CI**

  Run:

  ```sh
  gh run list --branch main --limit 5
  gh run watch <run-id> --exit-status
  ```

  Expected: pushed `main` CI passes.

## Self-Review

- Spec coverage: the plan removes HMC driver descriptors/invoke branches,
  lower bridge gameplay helpers, smoke-plan gameplay wording, docs, and
  governance.
- Placeholder scan: no TBD/TODO/fill-in placeholders.
- Type consistency: HMC bridge remains lifecycle-only with `CONNECT` and
  `STOP`; generated Fabric gameplay remains untouched.
