# Smoke Bootstrap Action Id Source Ownership Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove duplicated bootstrap gameplay action-id literals from the Fabric smoke controller.

**Architecture:** Add a red source guard for smoke-owned bootstrap action ids, then replace smoke action calls and required-action checks with `FabricBootstrapOperationIds` constants. This keeps the smoke harness tied to the transitional bootstrap definition source while preserving behavior.

**Tech Stack:** Kotlin/JVM, Gradle via mise, Kotlin test, Markdown.

---

### Task 1: Add Red Smoke Action Id Guard

**Files:**
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [x] **Step 1: Add source guard**

  Add a test named
  `fabric smoke controller does not own bootstrap action id literals`.

  It must read `FabricClientSmokeController.kt` and assert it does not contain
  the quoted literals currently owned by `FabricBootstrapOperationIds`:

  - `"player.chat"`
  - `"player.move"`
  - `"screen.query"`
  - `"world.time.query"`
  - `"player.query"`
  - `"entity.query"`
  - `"inventory.query"`
  - `"inventory.equip"`
  - `"player.look"`
  - `"player.raycast"`
  - `"world.block.break"`
  - `"world.block.interact"`

- [x] **Step 2: Run red guard**

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric smoke controller does not own bootstrap action id literals*'
  ```

  Expected: fails before implementation because the smoke controller still
  repeats those action-id literals.

### Task 2: Use Bootstrap Operation Id Constants In Smoke

**Files:**
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricClientSmokeController.kt`

- [x] **Step 1: Replace smoke action call ids**

  Replace `runAvailableAction` and `runInventoryQuery` bootstrap action
  literals with `FabricBootstrapOperationIds` constants.

- [x] **Step 2: Replace public-agent required action ids**

  Replace the bootstrap action literals in `PUBLIC_AGENT_REQUIRED_ACTIONS` with
  `FabricBootstrapOperationIds` constants while keeping navigation constants
  from Phase 89.

### Task 3: Update Governance And Evidence

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/evidence/2026-06-28-smoke-bootstrap-action-id-source-ownership.md`

- [x] **Step 1: Add Phase 90 to AGENTS**
- [x] **Step 2: Add checklist section**
- [x] **Step 3: Record red and green evidence**

### Task 4: Final Verification And Push

**Files:**
- All modified files from previous tasks

- [x] **Step 1: Run focused green test**

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric smoke controller does not own bootstrap action id literals*'
  ```

- [x] **Step 2: Run source scan and forced local gates**

  ```sh
  rg -n '"(player\.chat|player\.move|screen\.query|world\.time\.query|player\.query|entity\.query|inventory\.query|inventory\.equip|player\.look|player\.raycast|world\.block\.break|world\.block\.interact)"' driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricClientSmokeController.kt
  git diff --check
  mise exec -- gradle lint test --rerun-tasks
  mise exec -- bun test playwright
  ```

- [ ] **Step 3: Commit and push**

  ```sh
  git add AGENTS.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-90-smoke-bootstrap-action-id-source-ownership-design.md docs/superpowers/plans/2026-06-28-90-smoke-bootstrap-action-id-source-ownership-plan.md docs/superpowers/evidence/2026-06-28-smoke-bootstrap-action-id-source-ownership.md driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricClientSmokeController.kt driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt
  git commit -m "driver-fabric: centralize smoke action ids"
  git push origin main
  ```

## Self-Review

- Spec coverage: guard, smoke action calls, required actions, governance, and
  verification are covered.
- Placeholder scan: no TBD/TODO placeholders.
- Scope: no new gameplay action, route family, CLI catalog, Fabric binding,
  version lane, or support claim.
