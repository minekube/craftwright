# Fabric Execution Adapter Naming Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> superpowers:subagent-driven-development or superpowers:executing-plans to
> implement this plan task-by-task. Follow TDD: add the naming guard first,
> watch it fail, then rename.

**Goal:** Remove stale action-binding terminology from private Fabric
execution adapter code.

**Architecture:** Runtime graph discovery and generated OpenAPI own public
actions. Fabric private classes execute graph operations through adapter keys.

**Tech Stack:** Kotlin, Gradle through mise.

---

### Task 1: Add The Red Guard

**Files:**

- Modify:
  `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [x] Add a source guard over `FabricActionBindings.kt` and
  `FabricDriverBackend.kt`.
- [x] Reject stale names: `FabricActionBinding`,
  `defaultFabricActionBindings`, `actionBindings`, and
  `actionBindingsById`.
- [x] Run the focused test before implementation and capture the red failure.

### Task 2: Rename Private Execution Types

**Files:**

- Rename:
  `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricActionBindings.kt`
  to
  `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricExecutionAdapters.kt`
- Modify:
  `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricExecutionAdapters.kt`
- Modify:
  `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt`
- Modify:
  `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricCapabilityProbeTest.kt`
- Modify:
  `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [x] Rename interfaces, factory functions, implementation objects, and
  backend variables to execution-adapter terminology.
- [x] Keep operation ids, adapter keys, invocation behavior, and graph
  projection unchanged.

### Task 3: Verify

- [x] Run focused naming and adapter tests.
- [x] Run the full Fabric module test suite.
- [x] Run architecture checks.

### Task 4: Record Evidence

**Files:**

- Create:
  `docs/superpowers/evidence/2026-06-28-fabric-execution-adapter-naming.md`
- Modify:
  `docs/project-completion-checklist.md`
- Modify:
  `docs/superpowers/phase-index.md`

- [x] Record red/green focused evidence.
- [x] Record final local verification.
- [ ] Commit and push to `main`.
