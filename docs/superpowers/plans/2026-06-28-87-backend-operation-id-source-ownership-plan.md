# Backend Operation Id Source Ownership Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove duplicated bootstrap operation-id literals from backend adapter guard checks.

**Architecture:** Add a red source guard that rejects bootstrap operation-id literals in `FabricDriverBackend.kt`, then replace those guard checks with `FabricBootstrapOperationIds` constants. This keeps operation id ownership in the bootstrap graph definition layer while preserving current behavior.

**Tech Stack:** Kotlin/JVM, Gradle via mise, Kotlin test, Markdown.

---

### Task 1: Add Red Operation Id Ownership Guard

**Files:**
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [x] **Step 1: Add backend source guard**

  Add a test named
  `fabric backend does not own bootstrap operation id guard literals`.

  It must read
  `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt`
  and assert it does not contain:

  - `"entity.query"`
  - `"entity.attack"`
  - `"world.block.query"`
  - `"recipe.query"`
  - `"recipe.craft"`

- [x] **Step 2: Run red guard**

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric backend does not own bootstrap operation id guard literals*'
  ```

  Expected: fails before implementation because `FabricDriverBackend.kt`
  currently contains those operation-id literals.

### Task 2: Use Bootstrap Operation Id Constants

**Files:**
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt`

- [x] **Step 1: Replace entity guards**

  Replace `entity.query` and `entity.attack` backend guard literals with
  `FabricBootstrapOperationIds.ENTITY_QUERY` and
  `FabricBootstrapOperationIds.ENTITY_ATTACK`.

- [x] **Step 2: Replace block and recipe guards**

  Replace `world.block.query`, `recipe.query`, and `recipe.craft` backend guard
  literals with their `FabricBootstrapOperationIds` constants.

### Task 3: Update Governance And Evidence

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/evidence/2026-06-28-backend-operation-id-source-ownership.md`

- [x] **Step 1: Add Phase 87 to AGENTS**
- [x] **Step 2: Add checklist section**
- [x] **Step 3: Record red and green evidence**

### Task 4: Final Verification And Push

**Files:**
- All modified files from previous tasks

- [x] **Step 1: Run focused green tests**

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric backend does not own bootstrap operation id guard literals*' --tests '*FabricDriverModuleTest.fabric backend exposes bootstrap bindings as graph operation adapters*'
  ```

- [x] **Step 2: Run source scan and forced local gates**

  ```sh
  rg -n '"(entity\.query|entity\.attack|world\.block\.query|recipe\.query|recipe\.craft)"' driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt
  git diff --check
  mise exec -- gradle lint test --rerun-tasks
  mise exec -- bun test playwright
  ```

- [x] **Step 3: Commit and push**

  ```sh
  git add AGENTS.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-87-backend-operation-id-source-ownership-design.md docs/superpowers/plans/2026-06-28-87-backend-operation-id-source-ownership-plan.md docs/superpowers/evidence/2026-06-28-backend-operation-id-source-ownership.md driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt
  git commit -m "driver-fabric: centralize backend operation ids"
  git push origin main
  ```

## Self-Review

- Spec coverage: guard, constants use, governance, and verification are
  covered.
- Placeholder scan: no TBD/TODO placeholders.
- Scope: no new gameplay action, route family, CLI catalog, Fabric binding,
  version lane, or support claim.
