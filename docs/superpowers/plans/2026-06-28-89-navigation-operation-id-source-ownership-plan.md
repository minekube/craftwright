# Navigation Operation Id Source Ownership Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove duplicated navigation/task operation-id literals from backend dispatch and Fabric smoke readiness code.

**Architecture:** Add a red source guard for backend/smoke duplication, define internal navigation/task operation-id constants in the navigation discovery layer, and use those constants in graph construction, backend dispatch, and smoke required-action checks. This keeps the current transitional ids unchanged while reducing static catalog drift.

**Tech Stack:** Kotlin/JVM, Gradle via mise, Kotlin test, Markdown.

---

### Task 1: Add Red Navigation Operation Id Guard

**Files:**
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [x] **Step 1: Add source guard**

  Add a test named
  `fabric backend and smoke do not own navigation operation id literals`.

  It must read `FabricDriverBackend.kt` and assert it does not contain:

  - `"navigation.plan"`
  - `"navigation.follow"`
  - `"navigation.stop"`
  - `"task.run"`
  - `"task.status"`

  It must read `FabricClientSmokeController.kt` and assert it does not contain:

  - `"navigation.plan"`
  - `"navigation.follow"`

- [x] **Step 2: Run red guard**

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric backend and smoke do not own navigation operation id literals*'
  ```

  Expected: fails before implementation because backend and smoke code still
  repeat those operation-id literals.

### Task 2: Centralize Navigation Operation Ids

**Files:**
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricNavigationDiscovery.kt`
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt`
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricClientSmokeController.kt`

- [x] **Step 1: Add constants**

  Add an internal object in `FabricNavigationDiscovery.kt` for:

  - `PLAN = "navigation.plan"`
  - `FOLLOW = "navigation.follow"`
  - `STOP = "navigation.stop"`
  - `TASK_RUN = "task.run"`
  - `TASK_STATUS = "task.status"`
  - `TASK_PROGRESS = "task.progress"`

- [x] **Step 2: Use constants in discovery**

  Replace navigation/task operation and event id literals in
  `FabricNavigationDiscovery.kt` with the constants.

- [x] **Step 3: Use constants in backend and smoke**

  Replace backend dispatch branches and Fabric smoke required-action entries
  with the constants.

### Task 3: Update Governance And Evidence

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/evidence/2026-06-28-navigation-operation-id-source-ownership.md`

- [x] **Step 1: Add Phase 89 to AGENTS**
- [x] **Step 2: Add checklist section**
- [x] **Step 3: Record red and green evidence**

### Task 4: Final Verification And Push

**Files:**
- All modified files from previous tasks

- [x] **Step 1: Run focused green tests**

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric backend and smoke do not own navigation operation id literals*'
  ```

- [x] **Step 2: Run source scan and forced local gates**

  ```sh
  rg -n '"(navigation\.plan|navigation\.follow|navigation\.stop|task\.run|task\.status)"' driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricClientSmokeController.kt
  git diff --check
  mise exec -- gradle lint test --rerun-tasks
  mise exec -- bun test playwright
  ```

- [x] **Step 3: Commit and push**

  ```sh
  git add AGENTS.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-89-navigation-operation-id-source-ownership-design.md docs/superpowers/plans/2026-06-28-89-navigation-operation-id-source-ownership-plan.md docs/superpowers/evidence/2026-06-28-navigation-operation-id-source-ownership.md driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricNavigationDiscovery.kt driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricClientSmokeController.kt driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt
  git commit -m "driver-fabric: centralize navigation operation ids"
  git push origin main
  ```

## Self-Review

- Spec coverage: guard, constants, backend/smoke use, governance, and
  verification are covered.
- Placeholder scan: no TBD/TODO placeholders.
- Scope: no new gameplay action, route family, CLI catalog, Fabric binding,
  version lane, or support claim.
