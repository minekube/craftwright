# Bootstrap Adapter Key Separation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> superpowers:subagent-driven-development or superpowers:executing-plans to
> implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for
> tracking.

**Goal:** Close CL-02d by separating private Fabric adapter keys from
bootstrap operation public descriptor shape.

**Architecture:** Bootstrap operation definitions describe transitional graph
shape. Private adapter keys live in a separate operation-id to adapter-key map
used by both runtime graph projection and backend adapter registration.

**Tech Stack:** Kotlin/JVM, Gradle through `mise`, Fabric driver module.

---

### Task 1: Add The Red Guard

**Files:**

- Modify:
  `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [x] Add
  `bootstrap operation definitions do not own private adapter key pairs`.
- [x] Reject `val adapter: String` and
  `adapter = FabricBootstrapOperationAdapters` in bootstrap operation
  definitions.
- [x] Run the focused test and capture the red failure.

### Task 2: Move Adapter Keys To A Separate Map

**Files:**

- Modify:
  `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricBootstrapOperationDefinitions.kt`

- [x] Remove `adapter` from `FabricBootstrapOperationDefinition`.
- [x] Remove per-definition adapter-key assignments.
- [x] Add a private operation-id to adapter-key map.
- [x] Make `toRuntimeOperation` look up the adapter key from that map.

### Task 3: Use The Shared Private Map In Backend Registration

**Files:**

- Modify:
  `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt`

- [x] Replace adapter-key extraction from bootstrap definitions with the new
  `fabricBootstrapOperationAdapterKeysById()` helper.

### Task 4: Verify And Record Evidence

**Files:**

- Create:
  `docs/superpowers/evidence/2026-06-28-bootstrap-adapter-key-separation.md`
- Modify:
  `docs/project-completion-checklist.md`
- Modify:
  `docs/superpowers/phase-index.md`

- [x] Run focused red/green tests.
- [x] Run the full Fabric module test suite.
- [x] Run architecture and whitespace checks.
- [ ] Commit and push to `main`.
