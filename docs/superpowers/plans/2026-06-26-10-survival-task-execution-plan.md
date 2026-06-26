# Survival Task Execution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `task.run` execute the honest survival cow-hunt through internal Fabric task execution and graph-backed invocation.

**Current status:** Blocked as a durable completion path. Live verification
proved the internal scenario harness is useful for finding missing primitives,
but it is the wrong product boundary if it becomes the only proof. The final
completion path must move the survival scenario into an external agent/adaptive
CLI runner that composes generated OpenAPI actions, handles, SSE/JSON-RPC
events, and repository agent skills. Keep this plan as diagnostic history; do
not keep growing `task.survival.*` as the public solution.

**Architecture:** Add an internal survival task executor behind the existing `task.executor` adapter. The executor observes live client state on the client thread, invokes existing graph/navigation primitives internally, records task status/progress, and fails honestly when the live world cannot satisfy the no-cheat gate.

**Tech Stack:** Kotlin/JVM, Fabric Loom, Fabric API callbacks, Minecraft 1.21.6 Yarn mappings, Ktor client/server already in daemon, kotlinx.serialization, Gradle through mise.

---

### Task 1: Task Executor Contract And Status Registry

**Files:**
- Create: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricSurvivalTaskExecutor.kt`
- Test: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricSurvivalTaskExecutorTest.kt`

- [x] **Step 1: Write the failing contract tests**

Add tests:

```kotlin
@Test
fun `executor rejects unknown tasks without static shortcut actions`() {
    val executor = RecordingSurvivalExecutor()

    val result = executor.run(NavigationTaskRequest(task = "task.survival.unknown"))

    assertEquals(NavigationTaskState.FAILED, result.state)
    assertEquals("unsupported-task", result.message)
    assertFalse(result.toString().contains("kill.cow", ignoreCase = true))
}

@Test
fun `honest cow hunt starts with observable task status and progress`() {
    val executor = RecordingSurvivalExecutor()

    val result = executor.run(NavigationTaskRequest(task = "task.survival.honest-cow-hunt"))

    assertEquals(NavigationTaskState.RUNNING, result.state)
    assertEquals(result.id, executor.status(result.id).id)
    assertTrue(executor.events().any { it.type == "task.observe" })
}
```

- [x] **Step 2: Verify RED**

Run:

```sh
mise exec -- gradle :driver-fabric:test --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricSurvivalTaskExecutorTest'
```

Expected: FAIL because `FabricSurvivalTaskExecutor` does not exist.

- [x] **Step 3: Implement the minimal contract**

Create `FabricSurvivalTaskExecutor`, `RecordingSurvivalExecutor`, and
`SurvivalTaskRegistry`. Supported task ids are exactly:

```kotlin
private const val HONEST_COW_HUNT = "task.survival.honest-cow-hunt"
```

Unknown task ids return `NavigationTaskStatus(state = FAILED, message =
"unsupported-task")`. The cow-hunt starts a task id such as
`task:survival:honest-cow-hunt:0001`, stores it, and emits `task.observe`.

- [x] **Step 4: Verify GREEN**

Run the focused test again. Expected: PASS.

### Task 2: Wire `task.run` And `task.status`

**Files:**
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt`
- Test: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricNavigationDiscoveryTest.kt`

- [x] **Step 1: Write failing adapter tests**

Create a metadata backend with a recording survival executor. Invoke
`task.run` and `task.status` through `operationAdapters()`:

```kotlin
val run = adapters.invoke(DriverOperationInvocation(clientId = "alice", operation = operations.getValue("task.run"), arguments = mapOf("request" to requestJson)))
assertEquals("task.run", run.action)
assertEquals(DriverActionStatus.ACCEPTED, run.status)
assertEquals("task.survival.honest-cow-hunt", run.data["task"]?.jsonPrimitive?.content)
```

- [x] **Step 2: Verify RED**

Run:

```sh
mise exec -- gradle :driver-fabric:test --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricNavigationDiscoveryTest'
```

Expected: FAIL because `task.executor` still uses the unsupported adapter.

- [x] **Step 3: Implement adapter wiring**

Inject `FabricSurvivalTaskExecutor` into `FabricDriverBackend`. Map
`"task.executor"` to a task adapter that decodes `NavigationTaskRequest` from
`request`, calls `executor.run(...)`, and decodes `task` for status lookup.

- [x] **Step 4: Verify GREEN**

Run the focused test again. Expected: PASS.

### Task 3: Live Observation And Honest Failure Reasons

**Files:**
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricSurvivalTaskExecutor.kt`
- Test: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricSurvivalTaskExecutorTest.kt`

- [x] **Step 1: Write failing observation tests**

Use fake observation providers:

```kotlin
assertEquals("no-material-source", executorWithoutLogs.run(request).message)
assertEquals("no-cow-observed", executorWithoutCows.run(request).message)
assertEquals("pathfinder-unavailable", executorWithoutPathfinder.run(request).message)
```

Also assert progress events include `task.observe`, `task.navigate`,
`task.inventory`, and no backend names.

- [x] **Step 2: Verify RED**

Run the executor test. Expected: FAIL until observation providers exist.

- [x] **Step 3: Implement observation seams**

Add internal observation DTOs for material blocks, passive entities, inventory
weapons, and player position. The real provider uses `FabricClientGateway` and
Minecraft client-thread reads; tests use fakes.

- [x] **Step 4: Verify GREEN**

Run the executor test. Expected: PASS.

### Task 4: Material, Crafting, And Combat Execution

**Files:**
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricSurvivalTaskExecutor.kt`
- Test: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricSurvivalTaskExecutorTest.kt`

- [x] **Step 1: Write failing execution tests**

With fake execution ports, assert the successful cow-hunt sequence:

```kotlin
assertEquals(listOf("observe", "plan-material", "follow-material", "break-material", "craft-weapon", "plan-cow", "follow-cow", "attack-cow"), ports.calls)
assertEquals(NavigationTaskState.SUCCEEDED, result.state)
```

- [x] **Step 2: Verify RED**

Run the executor test. Expected: FAIL until execution ports are called.

- [x] **Step 3: Implement execution ports**

Keep Minecraft-specific calls behind internal ports:

- `breakMaterial(target)`
- `craftWeapon()`
- `equipWeapon()`
- `attackEntity(handle)`

The real port must use client-thread Fabric/Minecraft APIs and return honest
failure states; it must not mutate inventory without consuming real observed
materials.

- [x] **Step 4: Verify GREEN**

Run the executor test. Expected: PASS.

### Task 5: Final Gameplay Controller Uses `task.run`

**Files:**
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricClientSmokeController.kt`
- Test: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [x] **Step 1: Write failing final-controller test**

Assert final gameplay artifacts include task evidence names and no provisioning:

```kotlin
assertTrue(FabricFinalGameplayPlan.default().artifacts.contains("survival-task-results.jsonl"))
assertFalse(FabricFinalGameplayPlan.default().completionGates.any { it.contains("provisioned", ignoreCase = true) && !it.contains("no", ignoreCase = true) })
```

- [x] **Step 2: Verify RED**

Run the focused module test. Expected: FAIL until final artifacts mention the
survival task run.

- [x] **Step 3: Invoke `task.run` in final mode**

When `CRAFTLESS_FINAL_GAMEPLAY=1`, after connected OpenAPI is captured, invoke
`task.run` with:

```json
{"task":"task.survival.honest-cow-hunt"}
```

Write `survival-task-results.jsonl` and append task progress to
`gameplay-results.jsonl`.

- [x] **Step 4: Verify GREEN**

Run `mise exec -- gradle :driver-fabric:test`. Expected: PASS.

### Task 6: Live No-Cheat Gameplay And Completion

**Files:**
- Modify: `docs/project-completion-checklist.md`

- [ ] **Step 1: Run preflight**

Run:

```sh
mise run lint
mise run architecture-check
mise run ci
```

Expected: all pass.

- [!] **Step 2: Run final gameplay**

Run:

```sh
CRAFTLESS_FINAL_GAMEPLAY=1 mise exec -- gradle :driver-fabric:fabricFinalGameplay
```

Current evidence: after adding inventory verification, repeated no-hold live
runs fail honestly instead of falsely passing. Latest failure is
`material-collect-timeout`, and post-task `inventory.query` shows no collected
logs, weapon, beef, or leather. Next work should specify and implement the
public-agent final gameplay path instead of adding more survival-specific
Kotlin scenario code.

Expected: artifacts show no `ITEM_PROVISIONED`, `task.run` succeeds, a weapon
is obtained through real gameplay, a cow is killed, and SSE/task evidence is
recorded.

Observed with bounded debug hold:

```sh
CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 mise exec -- gradle :driver-fabric:fabricFinalGameplay
```

`survival-task-results.jsonl` contained `task.run` status `ACCEPTED` with
message `cow-hunt-complete`.

- [ ] **Step 3: Invite Robin**

Run:

```sh
say "Robin, join the Craftless test server now and confirm in Minecraft chat when the goal may be completed."
```

Expected: Robin joins or observes and writes in Minecraft chat that completion
is allowed.

- [ ] **Step 4: Update checklist, commit, and push**

Only after the evidence and Robin chat confirmation exist, mark the final
completion gates checked, run `mise run ci`, commit, push to `main`, and then
call `update_goal(status = "complete")`.
