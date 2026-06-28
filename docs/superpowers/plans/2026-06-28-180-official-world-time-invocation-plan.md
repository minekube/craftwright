# Official World Time Invocation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the official 26.x lane execute the generated `world.time.query`
operation through an internal official provider.

**Architecture:** The official backend validates invocations against its
generated runtime graph and dispatches by private adapter key. A lane-local
provider reads Minecraft 26.x official clock APIs on the client thread.

**Tech Stack:** Kotlin/JVM, Fabric Loom official mappings, Gradle through
`mise`, Ktor-free in-process driver backend tests.

---

### Task 1: Official World Time Invocation

**Files:**
- Modify: `driver-fabric-official/src/test/kotlin/com/minekube/craftless/driver/fabric/official/OfficialFabricSharedRuntimeMetadataTest.kt`
- Create: `driver-fabric-official/src/main/kotlin/com/minekube/craftless/driver/fabric/official/OfficialFabricClientQuery.kt`
- Create: `driver-fabric-official/src/main/kotlin/com/minekube/craftless/driver/fabric/official/OfficialFabricWorldTimeProvider.kt`
- Modify: `driver-fabric-official/src/main/kotlin/com/minekube/craftless/driver/fabric/official/OfficialFabricClientStateProvider.kt`
- Modify: `driver-fabric-official/src/main/kotlin/com/minekube/craftless/driver/fabric/official/OfficialFabricDriverBackend.kt`

- [x] **Step 1: Write the failing test**

Add a focused official backend test that constructs an available
client-state graph and invokes `DriverActionInvocation("world.time.query")`.
Expect `DriverActionStatus.ACCEPTED` plus `time` and `time-of-day` data.

- [x] **Step 2: Verify red**

Run:

```sh
mise exec -- gradle :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest.official backend invokes generated world time query through lane provider*'
```

Expected before implementation: compile failure because the official
world-time provider API and backend parameter do not exist.

- [x] **Step 3: Add client-thread query helper**

Create `OfficialFabricClientQuery.kt` with a shared
`queryOfficialMinecraftClient(...)` helper so official client-state and
world-time providers use the same thread boundary.

- [x] **Step 4: Add official world-time provider**

Create `OfficialFabricWorldTimeProvider.kt` with:

```kotlin
internal data class OfficialFabricWorldTime(
    val time: Long,
    val timeOfDay: Long,
)
```

and a Minecraft-backed provider that reads `defaultClockTime` and
`overworldClockTime`.

- [x] **Step 5: Dispatch generated operation by private adapter key**

Update `OfficialFabricDriverBackend.invoke(...)` to resolve the operation from
`runtimeGraph(clientId)`, honor unavailable graph state, and dispatch
`fabric.world-time-query` to the official provider.

- [x] **Step 6: Verify green and broad gates**

Run:

```sh
mise exec -- gradle :driver-fabric-official:test
mise run fabric-lane-check-latest-official
mise run architecture-check
```

Expected: all pass, with latest official status `status=compiled`.
