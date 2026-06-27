# Stable Fabric Entrypoint Boundary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Point Fabric mod metadata at a stable Craftless entrypoint and keep current compiled-lane startup behind an internal versioned bootstrap boundary.

**Architecture:** Add `com.minekube.craftless.driver.fabric.CraftlessFabricClientEntrypoint` as the Fabric-facing class. It delegates to `FabricCurrentLaneBootstrap` in the current compiled provider package. The mixin package remains version-scoped because those classes are bytecode-sensitive.

**Tech Stack:** Kotlin/JVM, Fabric `ClientModInitializer`, Fabric Loom resources, Gradle through mise.

---

### Task 1: Add Failing Entrypoint Boundary Tests

**Files:**
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [x] **Step 1: Update processed metadata expectation**

  Change the Fabric metadata test to expect
  `com.minekube.craftless.driver.fabric.CraftlessFabricClientEntrypoint`.

- [x] **Step 2: Guard source metadata**

  Extend the source `fabric.mod.json` test so it rejects the old
  `driver.fabric.v1_21_6.CraftlessFabricClientEntrypoint` entrypoint path.

- [x] **Step 3: Verify RED**

  Run:

  ```sh
  mise exec -- gradle --no-daemon :driver-fabric:test --tests '*FabricDriverModuleTest.fabric metadata declares client entrypoint and mixin config*' --tests '*FabricDriverModuleTest.fabric mod source metadata is expanded from compiled lane placeholders*'
  ```

  Expected: both focused tests fail because metadata still points at the
  versioned entrypoint.

### Task 2: Add Stable Entrypoint And Internal Bootstrap

**Files:**
- Create: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/CraftlessFabricClientEntrypoint.kt`
- Move/modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/CraftlessFabricClientEntrypoint.kt`
- Modify: `driver-fabric/src/main/resources/fabric.mod.json`

- [x] **Step 1: Add stable entrypoint**

  Add a stable Fabric `ClientModInitializer` in the non-versioned Fabric
  package.

- [x] **Step 2: Move current-lane startup**

  Rename the versioned entrypoint implementation to
  `FabricCurrentLaneBootstrap` and expose only an internal `initialize()`
  method.

- [x] **Step 3: Update mod metadata**

  Point `fabric.mod.json` at the stable entrypoint. Leave mixin package
  metadata version-scoped.

- [x] **Step 4: Verify GREEN**

  Run:

  ```sh
  mise exec -- gradle --no-daemon :driver-fabric:test --tests '*FabricDriverModuleTest.fabric metadata declares client entrypoint and mixin config*' --tests '*FabricDriverModuleTest.fabric mod source metadata is expanded from compiled lane placeholders*'
  ```

### Task 3: Register Phase And Verify

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`

- [x] **Step 1: Register Phase 48 in `AGENTS.md`**

  Add `48. stable Fabric entrypoint boundary.` to the active phase list and
  document that the Fabric-facing entrypoint is stable while current lane
  bootstrap remains internal.

- [x] **Step 2: Add checklist evidence**

  Add a Phase 48 checklist section with spec path, plan path, behavior, and
  verification commands.

- [x] **Step 3: Run quality gates**

  Run:

  ```sh
  git diff --check
  mise exec -- gradle --no-daemon :driver-fabric:test
  mise run lint
  mise run architecture-check
  mise run ci
  ```

### Task 4: Commit, Push, And Monitor

**Files:**
- Commit the phase files and implementation.

- [x] **Step 1: Commit and push**

  Run:

  ```sh
  git add AGENTS.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-27-48-stable-fabric-entrypoint-boundary-design.md docs/superpowers/plans/2026-06-27-48-stable-fabric-entrypoint-boundary-plan.md driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/CraftlessFabricClientEntrypoint.kt driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricCurrentLaneBootstrap.kt driver-fabric/src/main/resources/fabric.mod.json driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt
  git commit -m "driver-fabric: add stable fabric entrypoint boundary"
  git push origin main
  ```

- [x] **Step 2: Verify remote CI**

  Run:

  ```sh
  gh run list --repo minekube/craftless --branch main --limit 3
  gh run watch <latest-run-id> --repo minekube/craftless --exit-status
  ```
