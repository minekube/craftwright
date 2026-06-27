# Fabric Bootstrap Selection Boundary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route stable Fabric entrypoint startup through an internal non-versioned bootstrap selector instead of importing the current version package directly.

**Architecture:** Add a small internal `FabricDriverBootstrap` contract and `FabricBootstrapSelector` in `com.minekube.craftless.driver.fabric`. The selector knows the registered compiled-lane bootstrap implementations; the stable entrypoint calls the selector, and the current `v1_21_6` bootstrap implements the stable contract.

**Tech Stack:** Kotlin/JVM, Fabric `ClientModInitializer`, Gradle tests through mise.

---

### Task 1: Add Failing Bootstrap Boundary Tests

**Files:**
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`
- Create: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/FabricBootstrapSelectorTest.kt`

- [x] **Step 1: Guard stable entrypoint source**

  Add a test that reads
  `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/CraftlessFabricClientEntrypoint.kt`
  and asserts it does not contain `driver.fabric.v1_21_6` or
  `FabricCurrentLaneBootstrap`, and does contain `FabricBootstrapSelector`.

- [x] **Step 2: Add selector contract test**

  Add `FabricBootstrapSelectorTest` in package
  `com.minekube.craftless.driver.fabric`. It should assert that
  `FabricBootstrapSelector.selectCurrentCompiledLane()` returns metadata
  matching `FabricCompiledLaneMetadata.PROVIDER_ID` and
  `FabricCompiledLaneMetadata.MINECRAFT_VERSION`. The test must not call
  `initialize()`.

- [x] **Step 3: Verify RED**

  Run:

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.stable fabric entrypoint delegates through bootstrap selector only*' --tests '*FabricBootstrapSelectorTest*'
  ```

  Expected: fails because the selector does not exist and the entrypoint still
  imports the version-scoped bootstrap directly.

### Task 2: Add Stable Selector And Current Bootstrap Adapter

**Files:**
- Create: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/FabricBootstrapSelector.kt`
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/CraftlessFabricClientEntrypoint.kt`
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricCurrentLaneBootstrap.kt`

- [x] **Step 1: Add bootstrap contract and selector**

  Create an internal `FabricDriverBootstrap` interface with
  `providerId`, `minecraftVersion`, and `initialize()`. Add
  `FabricBootstrapSelector.selectCurrentCompiledLane()` and
  `FabricBootstrapSelector.initializeCurrentCompiledLane()`.

- [x] **Step 2: Implement contract in current lane**

  Make `FabricCurrentLaneBootstrap` implement `FabricDriverBootstrap` and
  expose provider/version values from `FabricCompiledLaneMetadata`.

- [x] **Step 3: Update stable entrypoint**

  Replace the direct current-lane bootstrap import with
  `FabricBootstrapSelector.initializeCurrentCompiledLane()`.

- [x] **Step 4: Verify focused tests GREEN**

  Run:

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.stable fabric entrypoint delegates through bootstrap selector only*' --tests '*FabricBootstrapSelectorTest*'
  ```

### Task 3: Register Phase And Verify

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`

- [x] **Step 1: Register Phase 51**

  Add `51. Fabric bootstrap selection boundary.` to `AGENTS.md` and document
  that the stable Fabric entrypoint must call the selector instead of a
  version-scoped bootstrap directly.

- [x] **Step 2: Add checklist evidence**

  Add a Phase 51 checklist section with spec path, plan path, behavior, and
  verification commands.

- [x] **Step 3: Run quality gates**

  Run:

  ```sh
  git diff --check
  mise exec -- gradle :driver-fabric:test
  mise run lint
  mise run architecture-check
  mise run ci
  ```

### Task 4: Commit, Push, And Monitor

**Files:**
- Commit all Phase 51 files and changes.

- [x] **Step 1: Commit and push**

  Run:

  ```sh
  git add AGENTS.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-27-51-fabric-bootstrap-selection-boundary-design.md docs/superpowers/plans/2026-06-27-51-fabric-bootstrap-selection-boundary-plan.md driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/FabricBootstrapSelector.kt driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/CraftlessFabricClientEntrypoint.kt driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricCurrentLaneBootstrap.kt driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/FabricBootstrapSelectorTest.kt driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt
  git commit -m "driver-fabric: add bootstrap selection boundary"
  git push origin main
  ```

- [x] **Step 2: Verify remote CI**

  Run:

  ```sh
  gh run list --repo minekube/craftless --branch main --limit 3
  gh run watch <latest-run-id> --repo minekube/craftless --exit-status
  ```
