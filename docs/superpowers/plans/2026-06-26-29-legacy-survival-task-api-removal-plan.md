# Legacy Survival Task API Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the diagnostic `task.survival.*` Fabric path from active product behavior while preserving a truthful generic task placeholder for future work.

**Architecture:** Replace the survival-specific executor with a generic unavailable task executor. Keep `task.run`/`task.status` only as generic graph operations that refuse execution until a real generic task graph executor exists. Remove smoke-harness survival invocations and update docs/checklist to make the public-agent path the only completion path.

**Tech Stack:** Kotlin/JVM, Gradle Kotlin DSL, kotlinx.serialization JSON, Fabric driver tests, mise.

---

### Task 1: Red Test For Removed Survival Task Execution

**Files:**
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricNavigationDiscoveryTest.kt`

- [ ] Change the task adapter test to invoke `task.run` with
  `task.survival.honest-cow-hunt` on the default metadata-only backend and
  expect `DriverActionStatus.UNSUPPORTED` with
  `task-executor-unavailable`.
- [ ] Assert that the result data does not echo the survival task id.
- [ ] Run:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricNavigationDiscoveryTest.fabric backend task adapter refuses legacy survival tasks*'`
- [ ] Confirm the test fails because the current `RecordingSurvivalExecutor`
  still accepts the survival task.

### Task 2: Replace Survival Executor With Generic Unavailable Executor

**Files:**
- Delete: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricSurvivalTaskExecutor.kt`
- Create: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricTaskExecutor.kt`
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt`
- Delete: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricSurvivalTaskExecutorTest.kt`

- [ ] Create `FabricTaskExecutor` with `run`, `status`, and `events`.
- [ ] Create `UnavailableFabricTaskExecutor` that returns
  `NavigationTaskState.FAILED` and `task-executor-unavailable` for any run.
- [ ] Replace `survivalTaskExecutor` constructor parameters with
  `taskExecutor`.
- [ ] Remove all survival observation, material, cow, weapon, and execution
  port code.
- [ ] Run the focused red test and confirm it passes.

### Task 3: Remove Survival Task Graph And Smoke Branch

**Files:**
- Delete: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/SurvivalTaskGraph.kt`
- Delete: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/SurvivalTaskGraphTest.kt`
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricClientSmokeController.kt`
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [ ] Remove `runSurvivalTask` configuration and the branch that invokes
  `task.run` with `task.survival.honest-cow-hunt`.
- [ ] Remove `survival-task-results.jsonl` from final gameplay plan artifacts.
- [ ] Keep public-agent gameplay artifacts intact.
- [ ] Run:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest*' --tests '*FabricNavigationDiscoveryTest*'`

### Task 4: Docs, Checklist, And Gates

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`

- [ ] Add Phase 29 to the active checklist.
- [ ] Mark legacy survival executor/graph/smoke path removed.
- [ ] Run `git diff --check`.
- [ ] Run `mise exec -- gradle :driver-fabric:test`.
- [ ] Run `mise run lint`.
- [ ] Run `mise run ci`.
- [ ] Commit and push directly to `main`.
