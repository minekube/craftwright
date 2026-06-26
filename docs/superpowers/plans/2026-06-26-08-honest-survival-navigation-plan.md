# Honest Survival Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the navigation and task-execution foundation required for Craftless to complete the final gameplay gate without cheated items or manual movement.

**Architecture:** Implement a Craftless-owned navigation/task layer fed by runtime graph discovery. The first slice adds protocol models, discovery/projection guardrails, an optional internal Baritone backend seam, SSE task progress events, and a no-cheat final gameplay harness. Later slices can deepen the survival executor until it can gather/craft/fight end to end.

**Tech Stack:** Kotlin/JVM, Gradle Kotlin DSL, Fabric Loom, Ktor Server/Client, kotlinx.serialization, mise, Bun for helper tests.

---

### Task 1: Protocol Models For Navigation And Tasks

**Files:**
- Create: `protocol/src/main/kotlin/com/minekube/craftless/protocol/NavigationModels.kt`
- Test: `protocol/src/test/kotlin/com/minekube/craftless/protocol/NavigationModelsTest.kt`

- [x] **Step 1: Write the failing serialization and validation tests**

Create tests asserting that navigation goals, plans, route segments, task
requests, task status, and progress events serialize with Craftless-owned names
and reject raw backend names:

```kotlin
@Test
fun `navigation models reject raw backend names`() {
    assertFailsWith<IllegalArgumentException> {
        NavigationEngineDescriptor(id = "baritone.primary", displayName = "Baritone")
    }
    assertFailsWith<IllegalArgumentException> {
        NavigationTaskRequest(task = "baritone.goto", args = emptyMap())
    }
}
```

- [x] **Step 2: Run the focused test and verify RED**

Run:

```sh
mise exec -- gradle :protocol:test --tests 'com.minekube.craftless.protocol.NavigationModelsTest'
```

Expected: FAIL because the model classes do not exist.

- [x] **Step 3: Implement the protocol models**

Add serializable models:

```kotlin
@Serializable
data class NavigationGoal(
    val kind: String,
    val position: Map<String, Double> = emptyMap(),
    val radius: Double? = null,
)

@Serializable
data class NavigationEngineDescriptor(
    val id: String,
    val displayName: String,
    val availability: String = "available",
    val reasons: List<String> = emptyList(),
) {
    init {
        require(id.startsWith("navigation.")) { "navigation engine id must be Craftless-owned" }
        require(!id.contains("baritone", ignoreCase = true)) { "navigation engine id must not expose backend names" }
    }
}
```

Add the remaining models explicitly:

```kotlin
@Serializable
data class NavigationRouteSegment(
    val index: Int,
    val from: NavigationGoal,
    val to: NavigationGoal,
    val cost: Double,
    val operations: List<String> = emptyList(),
)

@Serializable
data class NavigationPlan(
    val id: String,
    val goal: NavigationGoal,
    val engine: String,
    val segments: List<NavigationRouteSegment>,
) {
    init {
        require(id.startsWith("navigation.plan.")) { "navigation plan id must be Craftless-owned" }
        require(engine.startsWith("navigation.")) { "navigation engine must be Craftless-owned" }
    }
}

@Serializable
data class NavigationTaskRequest(
    val task: String,
    val args: Map<String, JsonElement> = emptyMap(),
) {
    init {
        require(task.startsWith("task.")) { "navigation task id must be Craftless-owned" }
        require(!task.contains("baritone", ignoreCase = true)) { "navigation task id must not expose backend names" }
    }
}

@Serializable
data class NavigationTaskStatus(
    val id: String,
    val state: String,
    val message: String? = null,
)

@Serializable
data class NavigationProgressEvent(
    val taskId: String,
    val type: String,
    val message: String,
    val payload: Map<String, JsonElement> = emptyMap(),
)
```

- [x] **Step 4: Verify GREEN**

Run:

```sh
mise exec -- gradle :protocol:test --tests 'com.minekube.craftless.protocol.NavigationModelsTest'
```

Expected: PASS.

### Task 2: Runtime Graph Projection For Navigation

**Files:**
- Modify: `protocol/src/main/kotlin/com/minekube/craftless/protocol/OpenApiDocument.kt`
- Test: `protocol/src/test/kotlin/com/minekube/craftless/protocol/OpenApiGenerationTest.kt`

- [x] **Step 1: Write failing projection tests**

Add a test that creates graph nodes for `navigation.plan`, `navigation.follow`,
`task.run`, and `task.status`, then asserts per-client OpenAPI contains
Craftless actions and no backend strings:

```kotlin
assertTrue(openApi.actions.any { it.id == "navigation.plan" })
assertFalse(json.encodeToString(openApi).contains("baritone", ignoreCase = true))
```

- [x] **Step 2: Run focused protocol tests and verify RED**

Run:

```sh
mise exec -- gradle :protocol:test --tests 'com.minekube.craftless.protocol.OpenApiGenerationTest'
```

Expected: FAIL until navigation resource/action schemas are projected.

- [x] **Step 3: Implement projection support**

Teach projection to accept navigation/task graph nodes with schemas from
`NavigationModels.kt`. Do not add static route catalog entries.

- [x] **Step 4: Verify GREEN**

Run:

```sh
mise exec -- gradle :protocol:test --tests 'com.minekube.craftless.protocol.OpenApiGenerationTest'
```

Expected: PASS.

### Task 3: Fabric Navigation Discovery Seam

**Files:**
- Create: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricNavigationDiscovery.kt`
- Test: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricNavigationDiscoveryTest.kt`

- [ ] **Step 1: Write failing discovery tests**

Test two cases:

```kotlin
@Test
fun `baritone presence is private evidence and public graph stays Craftless owned`() {
    val graph = FabricNavigationDiscovery(classExists = { it == "baritone.api.BaritoneAPI" }).discover()
    assertTrue(graph.operations.any { it.id == "navigation.plan" })
    assertFalse(graph.toString().contains("baritone.api"))
}

@Test
fun `missing pathfinder records unavailable navigation with reason`() {
    val graph = FabricNavigationDiscovery(classExists = { false }).discover()
    assertTrue(graph.operations.single { it.id == "navigation.plan" }.availability.reasons.isNotEmpty())
}
```

- [ ] **Step 2: Run focused test and verify RED**

Run:

```sh
mise exec -- gradle :driver-fabric:test --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricNavigationDiscoveryTest'
```

Expected: FAIL because discovery seam does not exist.

- [ ] **Step 3: Implement discovery seam**

Detect optional Baritone classes privately, emit Craftless graph nodes for
navigation/task affordances, and record private source evidence outside public
OpenAPI.

- [ ] **Step 4: Verify GREEN**

Run:

```sh
mise exec -- gradle :driver-fabric:test --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricNavigationDiscoveryTest'
```

Expected: PASS.

### Task 4: Generic Operation Adapter Wiring

**Files:**
- Modify: `driver-api/src/main/kotlin/com/minekube/craftless/driver/api/DriverOperationAdapter.kt`
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt`
- Test: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricNavigationDiscoveryTest.kt`

- [ ] **Step 1: Write failing adapter wiring tests**

Assert navigation operations are wired through existing
`DriverOperationAdapter` instances and that no action-specific driver methods
exist:

```kotlin
val methodNames = DriverSession::class.java.methods.map { it.name }.toSet()
assertFalse("goto" in methodNames)
assertFalse("mine" in methodNames)
assertFalse("killCow" in methodNames)
assertTrue(backend.operationAdapters("alice").contains("navigation.plan"))
```

- [ ] **Step 2: Run focused test and verify RED**

Run:

```sh
mise exec -- gradle :driver-fabric:test --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricNavigationDiscoveryTest'
```

Expected: FAIL until navigation operation adapters are registered.

- [ ] **Step 3: Implement adapter wiring**

Use the existing generic adapter shape:

```kotlin
DriverOperationAdapters(
    mapOf(
        "navigation.plan" to DriverOperationAdapter { invocation -> planNavigation(invocation) },
        "navigation.follow" to DriverOperationAdapter { invocation -> followNavigation(invocation) },
        "navigation.stop" to DriverOperationAdapter { invocation -> stopNavigation(invocation) },
    ),
)
```

Return `DriverActionResult` values and keep the public invocation path generic.

- [ ] **Step 4: Verify GREEN**

Run:

```sh
mise exec -- gradle :driver-fabric:test --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricNavigationDiscoveryTest'
```

Expected: PASS.

### Task 5: No-Cheat Final Gameplay Harness

**Files:**
- Modify: `driver-fabric/build.gradle.kts`
- Modify: `docs/final-gameplay-runbook.md`
- Test: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [ ] **Step 1: Write failing harness tests**

Assert the final gameplay plan has a no-cheat mode and that completion evidence
must not contain server item provisioning:

```kotlin
assertTrue(FabricFinalGameplayPlan.default().completionGates.any { it.contains("no server-side item provisioning") })
assertFalse(FabricFinalGameplayPlan.default().artifacts.contains("provisioned-iron-sword"))
```

- [ ] **Step 2: Run focused test and verify RED**

Run:

```sh
mise exec -- gradle :driver-fabric:test --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricDriverModuleTest.fabric final gameplay plan gates completion on graph streams artifacts and robin chat'
```

Expected: FAIL until the final plan/runbook no-cheat gate is added.

- [ ] **Step 3: Implement no-cheat harness mode**

Update `fabricFinalGameplay` defaults so final completion mode does not set
`CRAFTLESS_SMOKE_PROVISION_ITEM_ID`. Keep provisioned-item smoke available only
under a diagnostic task or explicit non-completion flag.

- [ ] **Step 4: Verify GREEN**

Run:

```sh
mise exec -- gradle :driver-fabric:test --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricDriverModuleTest.fabric final gameplay plan gates completion on graph streams artifacts and robin chat'
```

Expected: PASS.

### Task 6: Honest Survival Task Slice

**Files:**
- Create: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/SurvivalTaskGraph.kt`
- Test: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/SurvivalTaskGraphTest.kt`

- [ ] **Step 1: Write failing task graph tests**

Assert the task graph requires real observations before claiming progress:

```kotlin
val graph = SurvivalTaskGraph.honestCowHunt()
assertEquals("observe.logs", graph.steps.first().id)
assertTrue(graph.steps.none { it.id.contains("give", ignoreCase = true) })
assertTrue(graph.steps.any { it.id == "craft.weapon" })
assertTrue(graph.steps.any { it.id == "combat.attack_entity" })
```

- [ ] **Step 2: Run focused test and verify RED**

Run:

```sh
mise exec -- gradle :driver-fabric:test --tests 'com.minekube.craftless.driver.fabric.v1_21_6.SurvivalTaskGraphTest'
```

Expected: FAIL until the task graph model exists.

- [ ] **Step 3: Implement the task graph model**

Add a declarative internal task graph for the first honest scenario. It should
describe required observations and invocations, not fake execution.

- [ ] **Step 4: Verify GREEN**

Run:

```sh
mise exec -- gradle :driver-fabric:test --tests 'com.minekube.craftless.driver.fabric.v1_21_6.SurvivalTaskGraphTest'
```

Expected: PASS.

### Task 7: Full Verification And Push

**Files:**
- Modify: `docs/project-completion-checklist.md`

- [ ] **Step 1: Update checklist status**

Mark the honest survival/navigation phase as active. Keep final completion
unchecked until the no-cheat live run and Robin's Minecraft chat confirmation.

- [ ] **Step 2: Run verification**

Run:

```sh
mise exec -- gradle :protocol:test :driver-api:test :driver-fabric:test
mise run lint
mise run architecture-check
mise run ci
git diff --check
```

Expected: all pass.

- [ ] **Step 3: Commit and push**

Run:

```sh
git add protocol driver-api driver-fabric docs
git commit -m "feat: add honest survival navigation foundation"
git push origin main
```

Expected: `main` contains the verified navigation/task foundation.
