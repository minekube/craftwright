# Runtime Graph Default Action Projection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `DriverSession.actions()` default to graph-derived action descriptors from `RuntimeCapabilityGraph.operations`.

**Architecture:** Add shared protocol-to-driver projection helpers in `driver-api`, then make the driver session contract derive actions from `runtimeGraph()` by default. Remove redundant local conversion code where the graph is already authoritative, while preserving `HttpDriverSession.actions()` for attached remote drivers.

**Tech Stack:** Kotlin/JVM, driver-api, protocol runtime graph DTOs, Gradle through mise.

---

### Task 1: Add Default Runtime Graph Action Projection

**Files:**
- Modify: `driver-api/src/test/kotlin/com/minekube/craftless/driver/api/DriverSessionContractTest.kt`
- Modify: `driver-api/src/main/kotlin/com/minekube/craftless/driver/api/DriverSession.kt`

- [x] **Step 1: Write the failing contract test**

Add this test to `DriverSessionContractTest`:

```kotlin
@Test
fun `driver session derives actions from runtime graph operations by default`() {
    val session = GraphOnlyDriverSession()

    val actions = session.actions()

    assertEquals(listOf("inventory.query"), actions.map { action -> action.id })
    val action = actions.single()
    assertEquals(DriverActionSource.RUNTIME_PROBE, action.source)
    assertEquals(DriverActionAvailability.UNAVAILABLE, action.availability)
    assertEquals("client-not-connected", action.availabilityReason)
    assertEquals("object", action.arguments.getValue("filter").type)
    assertEquals(true, action.arguments.getValue("filter").required)
    assertEquals("string", action.arguments.getValue("filter").properties.getValue("item").type)
    assertEquals("object", action.result.properties.getValue("data").type)
    assertEquals("array", action.result.properties.getValue("data").properties.getValue("items").type)
}
```

Add this private fixture to the same test file:

```kotlin
private class GraphOnlyDriverSession : DriverSession {
    override val clientId: String = "graph-only"

    override fun snapshot(): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.RUNNING)

    override fun connect(target: ConnectionTarget): DriverClientSnapshot = snapshot()

    override fun runtimeMetadata(): DriverRuntimeMetadata = DriverRuntimeMetadata(driver = "craftless-test")

    override fun runtimeGraph(): RuntimeCapabilityGraph =
        RuntimeCapabilityGraph(
            clientId = clientId,
            resources = listOf(RuntimeResourceNode("inventory", RuntimeAvailability.unavailable("client-not-connected"))),
            operations =
                listOf(
                    RuntimeOperationNode(
                        id = "inventory.query",
                        resource = "inventory",
                        adapter = "test.inventory-query",
                        arguments =
                            mapOf(
                                "filter" to
                                    RuntimeSchema(
                                        type = "object",
                                        required = true,
                                        properties = mapOf("item" to RuntimeSchema("string")),
                                    ),
                            ),
                        result =
                            RuntimeSchema(
                                type = "object",
                                properties =
                                    mapOf(
                                        "items" to RuntimeSchema(type = "array", items = RuntimeSchema("object")),
                                    ),
                            ),
                        availability = RuntimeAvailability.unavailable("client-not-connected"),
                    ),
                ),
        )

    override fun invoke(invocation: DriverActionInvocation): DriverActionResult =
        DriverActionResult(invocation.action, DriverActionStatus.UNSUPPORTED)

    override fun stop(): DriverClientSnapshot = DriverClientSnapshot(clientId, ClientState.STOPPED)

    override fun events(): List<DriverEvent> = emptyList()
}
```

Expected: this does not compile before implementation because
`GraphOnlyDriverSession` does not implement abstract `actions()`.

- [x] **Step 2: Run the red test**

Run:

```sh
mise exec -- gradle :driver-api:test --tests '*DriverSessionContractTest*'
```

Expected: FAIL before implementation because `actions()` has no default.

- [x] **Step 3: Add shared projection helpers and default actions**

In `DriverSession.kt`, add imports for protocol runtime graph DTOs:

```kotlin
import com.minekube.craftless.protocol.RuntimeAvailability
import com.minekube.craftless.protocol.RuntimeAvailabilityState
import com.minekube.craftless.protocol.RuntimeOperationNode
import com.minekube.craftless.protocol.RuntimeSchema
```

Replace the abstract action method:

```kotlin
fun actions(): List<DriverActionDescriptor>
```

with:

```kotlin
fun actions(): List<DriverActionDescriptor> =
    runtimeGraph()
        .operations
        .sortedBy { operation -> operation.id }
        .map { operation -> operation.toDriverActionDescriptor() }
```

Add shared helpers in the same file:

```kotlin
fun RuntimeOperationNode.toDriverActionDescriptor(): DriverActionDescriptor =
    DriverActionDescriptor(
        id = id,
        schemaVersion = "1",
        arguments = arguments.mapValues { (_, schema) -> schema.toDriverActionArgument() },
        result = result.toDriverActionResultDescriptor(),
        source = DriverActionSource.RUNTIME_PROBE,
        availability = availability.toDriverActionAvailability(),
        availabilityReason = availability.reason,
    )

fun RuntimeSchema.toDriverActionArgument(): DriverActionArgument =
    DriverActionArgument(
        type = type,
        required = required,
        properties = properties.mapValues { (_, schema) -> schema.toDriverActionArgument() },
        items = items?.toDriverActionArgument(),
    )

fun RuntimeSchema.toDriverActionResultDescriptor(): DriverActionResultDescriptor =
    DriverActionResultDescriptor(
        properties =
            mapOf(
                "action" to DriverActionResultProperty("string"),
                "status" to DriverActionResultProperty("string"),
                "message" to DriverActionResultProperty("string"),
                "data" to toDriverActionResultProperty(),
            ),
        required = listOf("action", "status"),
    )

fun RuntimeSchema.toDriverActionResultProperty(): DriverActionResultProperty =
    DriverActionResultProperty(
        type = type,
        properties = properties.mapValues { (_, schema) -> schema.toDriverActionResultProperty() },
        items = items?.toDriverActionResultProperty(),
    )

fun RuntimeAvailability.toDriverActionAvailability(): DriverActionAvailability =
    when (state) {
        RuntimeAvailabilityState.AVAILABLE -> DriverActionAvailability.AVAILABLE
        RuntimeAvailabilityState.UNAVAILABLE -> DriverActionAvailability.UNAVAILABLE
    }
```

- [x] **Step 4: Run the green driver-api test**

Run:

```sh
mise exec -- gradle :driver-api:test --tests '*DriverSessionContractTest*'
```

Expected: PASS.

### Task 2: Remove Duplicate Local Conversion Paths

**Files:**
- Modify: `testkit/src/main/kotlin/com/minekube/craftless/testkit/FakeDriverSession.kt`
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt`
- Modify: `driver-fabric-official/src/test/kotlin/com/minekube/craftless/driver/fabric/official/probe/OfficialFabricAttachProbe.kt`
- Modify if possible: `daemon/src/main/kotlin/com/minekube/craftless/daemon/WorkspaceClientRuntimeDriverFactory.kt`

- [x] **Step 1: Remove redundant testkit action override and helpers**

In `FakeDriverSession.kt`, delete the `override fun actions()` implementation
and the private `RuntimeOperationNode.toDriverActionDescriptor`,
`RuntimeSchema.toDriverActionArgument`, and
`RuntimeAvailability.toDriverActionAvailability` helpers. Remove now-unused
imports.

- [x] **Step 2: Use the shared helper in Fabric backend**

In `FabricDriverBackend.kt`, import:

```kotlin
import com.minekube.craftless.driver.api.toDriverActionDescriptor
```

Keep `DriverBackend.actions(clientId)` as an override, but make it use the
shared helper:

```kotlin
override fun actions(clientId: String): List<DriverActionDescriptor> =
    runtimeGraph(clientId).operations.sortedBy { operation -> operation.id }.map { operation -> operation.toDriverActionDescriptor() }
```

Delete the private duplicate `RuntimeOperationNode.toDriverActionDescriptor`,
`RuntimeSchema.toDriverActionArgument`,
`RuntimeSchema.toDriverActionResultDescriptor`,
`RuntimeSchema.toDriverActionResultProperty`, and
`RuntimeAvailability.toDriverActionAvailability` helpers if no remaining code
uses them.

- [x] **Step 3: Remove explicit empty action overrides from graph-empty sessions where default is equivalent**

Remove explicit `override fun actions(): List<DriverActionDescriptor> = emptyList()`
from:

```text
driver-fabric-official/src/test/kotlin/com/minekube/craftless/driver/fabric/official/probe/OfficialFabricAttachProbe.kt
daemon/src/main/kotlin/com/minekube/craftless/daemon/WorkspaceClientRuntimeDriverFactory.kt
```

Only remove imports that become unused.

- [x] **Step 4: Run focused module tests**

Run:

```sh
mise exec -- gradle :driver-api:test :testkit:test :driver-fabric:test --tests '*DriverSessionContractTest*'
```

Expected: PASS or, if Gradle test filtering cannot select tests across all
modules cleanly, rerun the exact failing module without the filter and record
the actual command.

### Task 3: Docs, Final Verification, And Push

**Files:**
- Create: `docs/superpowers/evidence/2026-06-28-runtime-graph-default-action-projection.md`
- Modify: `docs/project-completion-checklist.md`
- Modify: `docs/superpowers/phase-index.md`
- Modify: `docs/superpowers/plans/2026-06-28-166-runtime-graph-default-action-projection-plan.md`

- [x] **Step 1: Record evidence and checklist status**

Record red check, focused tests, boundary notes, and removed duplication in:

```text
docs/superpowers/evidence/2026-06-28-runtime-graph-default-action-projection.md
docs/project-completion-checklist.md
docs/superpowers/phase-index.md
```

- [x] **Step 2: Run final verification**

Run:

```sh
mise exec -- gradle :driver-api:test :testkit:test
mise run fabric-lane-check-latest-official
mise run ci
git diff --check
git status --short --branch
```

Expected: all commands pass and the worktree only contains Phase 166 files.

- [x] **Step 3: Commit and push**

Run:

```sh
git add driver-api/src/main/kotlin/com/minekube/craftless/driver/api/DriverSession.kt driver-api/src/test/kotlin/com/minekube/craftless/driver/api/DriverSessionContractTest.kt testkit/src/main/kotlin/com/minekube/craftless/testkit/FakeDriverSession.kt driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt driver-fabric-official/src/test/kotlin/com/minekube/craftless/driver/fabric/official/probe/OfficialFabricAttachProbe.kt daemon/src/main/kotlin/com/minekube/craftless/daemon/WorkspaceClientRuntimeDriverFactory.kt docs/project-completion-checklist.md docs/superpowers/phase-index.md docs/superpowers/specs/2026-06-28-166-runtime-graph-default-action-projection-design.md docs/superpowers/plans/2026-06-28-166-runtime-graph-default-action-projection-plan.md docs/superpowers/evidence/2026-06-28-runtime-graph-default-action-projection.md
git commit -m "refactor: derive driver actions from runtime graph"
git push origin main
```

## Self-Review

- Spec coverage: the plan adds a failing driver-api contract test, implements
  default action projection from runtime graph operations, removes duplicate
  conversion helpers, updates evidence/checklist/phase index, and runs focused
  plus full verification.
- Placeholder scan: no task uses TBD/TODO/fill-in wording.
- Type consistency: helper names and DTO names match `driver-api` and
  `protocol` source names.
