# Pathfinder-Backed Execution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire real internal pathfinder-backed execution to Craftless navigation/task graph operations without exposing backend names publicly.

**Architecture:** Add a small internal Fabric pathfinder backend interface, a fake backend for tests, a reflection backend for optional runtime classes, and a task progress registry. Keep generated OpenAPI sourced from `RuntimeCapabilityGraph`; operation adapters invoke the backend through generic `DriverOperationAdapters`.

**Tech Stack:** Kotlin/JVM, Fabric Loom, Gradle Kotlin DSL, kotlinx.serialization, Ktor event plumbing already in daemon, mise.

---

### Task 1: Pathfinder Backend Contract And Registry

**Files:**
- Create: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricPathfinderBackend.kt`
- Create: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricPathfinderBackendTest.kt`

- [x] **Step 1: Write failing backend contract tests**

Test that a fake backend can create a plan, follow it, stop it, and record
Craftless-owned progress:

```kotlin
val backend = RecordingFabricPathfinderBackend(available = true)
val plan = backend.plan(NavigationGoal(kind = "block", position = mapOf("x" to 1.0, "y" to 64.0, "z" to 1.0)))
assertEquals("navigation.plan.", plan.id.substringBeforeLast(".") + ".")
assertEquals(NavigationTaskState.RUNNING, backend.follow(plan.id).state)
assertEquals(NavigationTaskState.CANCELLED, backend.stop().state)
assertFalse(backend.events().joinToString().contains("baritone", ignoreCase = true))
```

- [x] **Step 2: Run focused test and verify RED**

Run:

```sh
mise exec -- gradle :driver-fabric:test --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricPathfinderBackendTest'
```

Expected: FAIL because the backend contract does not exist.

- [x] **Step 3: Implement minimal contract and fake backend**

Add `FabricPathfinderBackend`, `FabricPathfinderPlan`, and
`RecordingFabricPathfinderBackend` test fake. Use protocol
`NavigationGoal`, `NavigationTaskStatus`, `NavigationTaskState`, and
`NavigationProgressEvent`.

- [x] **Step 4: Verify GREEN**

Run the focused test again. Expected: PASS.

### Task 2: Operation Adapters Invoke Pathfinder Backend

**Files:**
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt`
- Test: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricNavigationDiscoveryTest.kt`

- [x] **Step 1: Write failing adapter invocation tests**

Create a backend with a recording pathfinder and invoke `navigation.plan`,
`navigation.follow`, and `navigation.stop` through `operationAdapters()`.
Assert the results are `ACCEPTED`, action ids are the graph operation ids, and
no `DriverSession` method named `goto`, `mine`, or `killCow` exists.

- [x] **Step 2: Run focused test and verify RED**

Run:

```sh
mise exec -- gradle :driver-fabric:test --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricNavigationDiscoveryTest'
```

Expected: FAIL until adapters call the pathfinder backend instead of the
unsupported placeholder.

- [x] **Step 3: Wire adapters**

Inject `FabricPathfinderBackend` into `FabricDriverBackend`. Keep the default
backend unavailable unless runtime probing proves executable support. Make
`navigation.plan`, `navigation.follow`, and `navigation.stop` return
`DriverActionResult` values with task/plan data.

- [x] **Step 4: Verify GREEN**

Run the focused test again. Expected: PASS.

### Task 3: Reflection Backend Probe

**Files:**
- Create: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/ReflectiveFabricPathfinderBackend.kt`
- Test: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/ReflectiveFabricPathfinderBackendTest.kt`

- [x] **Step 1: Write failing reflection probe tests**

Use fake reflection gateways to prove class/method probes mark the backend
available only when provider, primary client, custom goal process, goal class,
and stop/cancel methods are all present. Assert public status/events do not
contain backend class names.

- [x] **Step 2: Run focused test and verify RED**

Run:

```sh
mise exec -- gradle :driver-fabric:test --tests 'com.minekube.craftless.driver.fabric.v1_21_6.ReflectiveFabricPathfinderBackendTest'
```

Expected: FAIL until the reflective backend exists.

- [x] **Step 3: Implement reflective backend**

Implement class lookup, method lookup, goal object construction, plan/follow,
and stop calls behind the `FabricPathfinderBackend` interface. Execute all live
client calls through `FabricClientGateway.executeOnClient` or
`queryOnClient`.

- [x] **Step 4: Verify GREEN**

Run the focused test again. Expected: PASS.

### Task 4: Optional Runtime Mod Preparation

**Files:**
- Modify: `driver-fabric/build.gradle.kts`
- Test: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [x] **Step 1: Write failing Gradle task metadata test**

Assert the final gameplay task documents opt-in pathfinder preparation and no
server-side item provisioning. The test should inspect `FabricFinalGameplayPlan`
metadata, not shell out to Gradle.

- [x] **Step 2: Run focused test and verify RED**

Run:

```sh
mise exec -- gradle :driver-fabric:test --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricDriverModuleTest.fabric final gameplay plan gates completion on graph streams artifacts and robin chat'
```

Expected: FAIL until plan metadata includes opt-in pathfinder preparation.

- [x] **Step 3: Add opt-in preparation**

Add a Gradle task that downloads the pinned Fabric pathfinder runtime jar to
`driver-fabric/build/pathfinder/`, verifies SHA-256, and wires it into
`runClient` only when `CRAFTLESS_ENABLE_PATHFINDER_BACKEND=1` or
`CRAFTLESS_FINAL_GAMEPLAY=1`. Do not expose the backend name in OpenAPI.

- [x] **Step 4: Verify GREEN**

Run the focused test and `mise exec -- gradle :driver-fabric:tasks --group verification`.
Expected: PASS and the preparation task is listed.

### Task 5: Verification And Push

**Files:**
- Modify: `docs/project-completion-checklist.md`

- [ ] **Step 1: Update checklist**

Mark pathfinder-backed execution as implemented. Keep final live survival
gameplay unchecked until the Phase 10 live run succeeds and Robin confirms in
Minecraft chat.

- [ ] **Step 2: Run verification**

Run:

```sh
mise exec -- gradle :driver-fabric:test
mise run lint
mise run architecture-check
mise run ci
git diff --check
```

Expected: all pass.

- [ ] **Step 3: Commit and push**

Run:

```sh
git add driver-fabric docs
git commit -m "feat: add pathfinder backed navigation execution"
git push origin main
```

Expected: `main` contains the verified internal pathfinder execution substrate.
