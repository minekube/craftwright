# Shared Fabric Runtime Graph Composition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Move generic Fabric runtime graph fragments and graph composition into shared `driver-fabric-discovery` infrastructure used by both Fabric lanes.

**Architecture:** Keep lane-specific probes and Minecraft game-class discovery in `driver-fabric`/`driver-fabric-official`; move only the protocol-level fragment model and graph merge helpers into `driver-fabric-discovery`.

**Tech Stack:** Kotlin/JVM, Craftless protocol runtime graph DTOs, Gradle 9.6, mise-managed Java.

---

### Task 1: Add red tests and guards

**Files:**
- Create: `driver-fabric-discovery/src/test/kotlin/com/minekube/craftless/driver/fabric/discovery/FabricRuntimeGraphTest.kt`
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [x] **Step 1: Write shared graph composition tests**

Create tests that call:

```kotlin
fabricRuntimeGraph(
    clientId = "alice",
    fragments =
        listOf(
            FabricRuntimeGraphFragment(
                resources = listOf(RuntimeResourceNode("player", RuntimeAvailability.available())),
            ),
            FabricRuntimeGraphFragment(
                operations =
                    listOf(
                        RuntimeOperationNode(
                            id = "player.query",
                            resource = "player",
                            adapter = "fabric.player-query",
                            availability = RuntimeAvailability.available(),
                        ),
                    ),
            ),
        ),
)
```

Assert the graph contains client id `alice`, resource `player`, operation
`player.query`, and a `graph:` fingerprint.

Add a duplicate resource test that composes two fragments with
`RuntimeResourceNode("player", RuntimeAvailability.available())` and asserts
`IllegalArgumentException`.

- [x] **Step 2: Write architecture guard**

In the official-lane architecture test, assert:

```kotlin
assertFalse(fabricBackend.contains("internal data class FabricCapabilityGraphFragment"))
assertTrue(fabricBackend.contains("typealias FabricCapabilityGraphFragment = FabricRuntimeGraphFragment"))
assertTrue(officialBackend.contains("fabricRuntimeMetadataGraph"))
assertFalse(officialBackend.contains("import com.minekube.craftless.protocol.RuntimeCapabilityGraph"))
```

- [x] **Step 3: Run red**

Run:

```sh
mise exec -- gradle :driver-fabric-discovery:test :driver-fabric:test --tests '*FabricDriverModuleTest.official lane has opt in launch attach probe task without packaging support claim'
```

Expected: FAIL because shared graph composition helpers do not exist and the
lane code still owns direct graph composition.

### Task 2: Implement shared graph composition

**Files:**
- Create: `driver-fabric-discovery/src/main/kotlin/com/minekube/craftless/driver/fabric/discovery/FabricRuntimeGraphFragment.kt`
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricCapabilityProbe.kt`
- Modify: `driver-fabric-official/src/main/kotlin/com/minekube/craftless/driver/fabric/official/OfficialFabricDriverBackend.kt`

- [x] **Step 1: Add shared graph file**

Create:

```kotlin
package com.minekube.craftless.driver.fabric.discovery

import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.protocol.RuntimeCapabilityGraph
import com.minekube.craftless.protocol.RuntimeEventNode
import com.minekube.craftless.protocol.RuntimeHandleNode
import com.minekube.craftless.protocol.RuntimeOperationNode
import com.minekube.craftless.protocol.RuntimeResourceNode
import com.minekube.craftless.protocol.RuntimeSourceEvidence

data class FabricRuntimeGraphFragment(
    val resources: List<RuntimeResourceNode> = emptyList(),
    val operations: List<RuntimeOperationNode> = emptyList(),
    val handles: List<RuntimeHandleNode> = emptyList(),
    val events: List<RuntimeEventNode> = emptyList(),
)

fun fabricRuntimeGraph(
    clientId: String,
    fragments: List<FabricRuntimeGraphFragment>,
): RuntimeCapabilityGraph =
    RuntimeCapabilityGraph(
        clientId = clientId,
        resources = fragments.flatMap { it.resources },
        operations = fragments.flatMap { it.operations },
        handles = fragments.flatMap { it.handles },
        events = fragments.flatMap { it.events },
    )

fun fabricRuntimeMetadataGraph(
    clientId: String,
    metadata: DriverRuntimeMetadata,
    sourceEvidence: List<RuntimeSourceEvidence> = emptyList(),
): RuntimeCapabilityGraph =
    fabricRuntimeGraph(
        clientId = clientId,
        fragments =
            listOf(
                FabricRuntimeGraphFragment(
                    resources =
                        listOf(
                            fabricRuntimeResourceNode(
                                metadata = metadata,
                                sourceEvidence = sourceEvidence,
                            ),
                        ),
                ),
            ),
    )
```

- [x] **Step 2: Wire Yarn/remap graph composition**

In `FabricCapabilityProbe.kt`, import `FabricRuntimeGraphFragment` and
`fabricRuntimeGraph`, replace the local `FabricCapabilityGraphFragment` data
class with:

```kotlin
internal typealias FabricCapabilityGraphFragment = FabricRuntimeGraphFragment
```

and replace direct `RuntimeCapabilityGraph(...)` construction in
`defaultFabricCapabilityDiscovery` with:

```kotlin
fabricRuntimeGraph(
    clientId = context.clientId,
    fragments = probes.map { probe -> probe.discover(context) },
)
```

- [x] **Step 3: Wire official metadata-only graph**

In `OfficialFabricDriverBackend.runtimeGraph`, return:

```kotlin
fabricRuntimeMetadataGraph(
    clientId = clientId,
    metadata = runtimeMetadata(clientId),
    sourceEvidence =
        listOf(
            RuntimeSourceEvidence("runtime-lane", "latest-current-official"),
            RuntimeSourceEvidence("runtime-status", "metadata-only"),
            RuntimeSourceEvidence("runtime-java", "java:25"),
        ),
)
```

### Task 3: Verify, document, commit, and push

**Files:**
- Modify: `AGENTS.md`
- Modify: `driver-fabric-discovery/AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/evidence/2026-06-28-shared-fabric-runtime-graph-composition.md`

- [x] **Step 1: Run focused tests**

Run:

```sh
mise exec -- gradle :driver-fabric-discovery:test :driver-fabric:test --tests '*FabricDriverModuleTest.official lane has opt in launch attach probe task without packaging support claim' :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*'
```

Expected: PASS.

- [x] **Step 2: Run lint and whitespace**

Run:

```sh
mise exec -- gradle lint
git diff --check
```

Expected: PASS.

- [x] **Step 3: Run real official attach probe**

Run:

```sh
rm -rf driver-fabric-official/build/craftless-official-attach-probe
CRAFTLESS_OFFICIAL_FABRIC_ATTACH_PROBE=1 \
CRAFTLESS_OFFICIAL_ATTACH_PROBE_TIMEOUT_MS=120000 \
mise exec -- gradle :driver-fabric-official:officialFabricAttachProbe
jq -r '."x-craftless"."x-craftless-installed-mods-fingerprint"' \
  driver-fabric-official/build/craftless-official-attach-probe/client-openapi.json
```

Expected: `ATTACHED`; installed-mod fingerprint starts with `mods:`.

- [x] **Step 4: Commit and push**

Run:

```sh
git status --short --branch
git add .
git commit -m "refactor: share fabric runtime graph composition"
git push origin main
```
