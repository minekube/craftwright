# Fabric Discovery Probes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Populate the runtime capability graph from live Fabric/Minecraft runtime probes.

**Architecture:** Keep Fabric internals inside `driver-fabric`. Probes query the Minecraft client thread and emit protocol graph nodes with private source evidence. Public descriptors are created later by projection, not by probes.

**Tech Stack:** Kotlin/JVM, Fabric Loom, Minecraft 1.21.6, Gradle, mise.

---

### Task 1: Probe Interfaces

**Files:**
- Create: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricCapabilityProbe.kt`
- Test: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricCapabilityProbeTest.kt`

- [x] **Step 1: Write failing tests**

Assert probes compose graph fragments, reject duplicate node ids, and never output public OpenAPI descriptors directly.

- [x] **Step 2: Implement probe interfaces**

Add `FabricCapabilityProbe`, `FabricCapabilityProbeContext`, and graph fragment composition.

- [x] **Step 3: Verify**

Run: `mise exec -- gradle :driver-fabric:test --tests com.minekube.craftless.driver.fabric.v1_21_6.FabricCapabilityProbeTest`

Expected: pass.

### Task 2: Registry And Client State Probes

**Files:**
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricCapabilityProbe.kt`
- Test: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricCapabilityProbeTest.kt`

- [x] **Step 1: Add failing tests for registry/client state graph nodes**

Assert item/block/entity/screen-handler registry summaries, connected state, player/camera/world/interaction availability, and screen state are graph nodes or evidence inputs.

- [x] **Step 2: Implement probes using existing gateway boundaries**

Use `queryOnClient` for Minecraft state and Fabric Loader/Registries for registry metadata.

- [x] **Step 3: Verify**

Run: `mise exec -- gradle :driver-fabric:test`

Expected: pass.

### Task 3: Static Action Drift Guard

**Files:**
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [x] **Step 1: Add test rejecting new descriptor/binding catalog growth**

Assert new public gameplay operation ids must appear in graph projection tests, not only in `FabricActionBindings.kt`.

- [x] **Step 2: Verify**

Run: `mise exec -- gradle :driver-fabric:test`

Expected: pass.
