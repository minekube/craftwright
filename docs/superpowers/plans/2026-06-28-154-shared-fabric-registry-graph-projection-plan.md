# Shared Fabric Registry Graph Projection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Move non-gameplay Fabric registry graph projection into shared discovery infrastructure and expose official-lane registry discovery status through generated OpenAPI evidence.

**Architecture:** `driver-fabric-discovery` owns protocol-level registry graph fragments. Fabric lanes still own actual Minecraft registry inspection, fingerprints, and any version-specific adapters.

**Tech Stack:** Kotlin/JVM, Craftless protocol runtime graph DTOs, Gradle 9.6, mise-managed Java.

---

### Task 1: Add red tests and architecture guards

**Files:**
- Modify: `driver-fabric-discovery/src/test/kotlin/com/minekube/craftless/driver/fabric/discovery/FabricRuntimeGraphTest.kt`
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [x] **Step 1: Add shared registry projection tests**

Add tests that call:

```kotlin
val metadata =
    DriverRuntimeMetadata.runtimeAdapter().copy(
        registryFingerprint = "registries:abc123",
    )

val fragment =
    fabricRegistryGraphFragment(
        metadata = metadata,
        available = true,
    )
```

Assert:

```kotlin
assertEquals(listOf("registry"), fragment.resources.map { it.id })
assertEquals(RuntimeAvailability.available(), fragment.resources.single().availability)
assertEquals(
    listOf(
        "registry.block",
        "registry.effect",
        "registry.entity",
        "registry.event",
        "registry.item",
        "registry.screen",
    ),
    fragment.handles.map { it.id }.sorted(),
)
assertTrue(fragment.resources.single().sourceEvidence.any { it.kind == "registry" && it.fingerprint == "registries:abc123" })
```

Add an unavailable test with:

```kotlin
val metadata =
    DriverRuntimeMetadata.runtimeAdapter().copy(
        registryFingerprint = "registries:not-discovered",
    )

val fragment =
    fabricRegistryGraphFragment(
        metadata = metadata,
        available = false,
    )
```

Assert every resource and handle has:

```kotlin
RuntimeAvailability.unavailable("registry-not-discovered")
```

- [x] **Step 2: Add architecture guard expectations**

In the official-lane architecture test, read
`driver-fabric-discovery/src/main/kotlin/com/minekube/craftless/driver/fabric/discovery/FabricRegistryGraph.kt`
and assert:

```kotlin
assertTrue(fabricRegistryGraph.contains("fabricRegistryGraphFragment"))
assertTrue(fabricRegistryGraph.contains("registry.entity"))
assertTrue(officialBackend.contains("fabricRegistryGraphFragment"))
assertFalse(officialBackend.contains("import com.minekube.craftless.protocol.RuntimeCapabilityGraph"))
assertFalse(fabricCapabilityProbe.contains("RuntimeResourceNode(\n                        id = \"registry\""))
```

- [x] **Step 3: Run red**

Run:

```sh
mise exec -- gradle :driver-fabric-discovery:test :driver-fabric:test --tests '*FabricDriverModuleTest.official lane has opt in launch attach probe task without packaging support claim'
```

Expected: FAIL because `fabricRegistryGraphFragment` and
`FabricRegistryGraph.kt` do not exist yet.

### Task 2: Implement shared registry projection

**Files:**
- Create: `driver-fabric-discovery/src/main/kotlin/com/minekube/craftless/driver/fabric/discovery/FabricRegistryGraph.kt`
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricCapabilityProbe.kt`
- Modify: `driver-fabric-official/src/main/kotlin/com/minekube/craftless/driver/fabric/official/OfficialFabricDriverBackend.kt`

- [x] **Step 1: Add shared registry graph helper**

Create `FabricRegistryGraph.kt`:

```kotlin
package com.minekube.craftless.driver.fabric.discovery

import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.protocol.RuntimeAvailability
import com.minekube.craftless.protocol.RuntimeHandleNode
import com.minekube.craftless.protocol.RuntimeResourceNode
import com.minekube.craftless.protocol.RuntimeSchema
import com.minekube.craftless.protocol.RuntimeSourceEvidence

fun fabricRegistryGraphFragment(
    metadata: DriverRuntimeMetadata,
    available: Boolean,
): FabricRuntimeGraphFragment {
    val availability =
        if (available && metadata.registryFingerprint != "registries:not-discovered") {
            RuntimeAvailability.available()
        } else {
            RuntimeAvailability.unavailable("registry-not-discovered")
        }
    val evidence = listOf(RuntimeSourceEvidence("registry", metadata.registryFingerprint))
    return FabricRuntimeGraphFragment(
        resources =
            listOf(
                RuntimeResourceNode(
                    id = "registry",
                    availability = availability,
                    sourceEvidence = evidence,
                ),
            ),
        handles =
            fabricRegistryHandleIds.map { handleId ->
                RuntimeHandleNode(
                    id = handleId,
                    resource = "registry",
                    schema = RuntimeSchema.objectSchema(),
                    availability = availability,
                    sourceEvidence = evidence,
                )
            },
    )
}

private val fabricRegistryHandleIds =
    listOf(
        "registry.block",
        "registry.item",
        "registry.entity",
        "registry.screen",
        "registry.effect",
        "registry.event",
    )
```

- [x] **Step 2: Wire Yarn/remap registry probe**

In `FabricRegistrySummaryCapabilityProbe.discover`, replace the direct
`RuntimeResourceNode` and `RuntimeHandleNode` construction with:

```kotlin
return fabricRegistryGraphFragment(
    metadata = context.runtimeMetadata,
    available = true,
)
```

Remove now-unused imports from `FabricCapabilityProbe.kt`.

- [x] **Step 3: Wire official metadata graph composition**

In `OfficialFabricDriverBackend.runtimeGraph`, replace the direct
`fabricRuntimeMetadataGraph(...)` return with an inferred expression body so
the official lane still does not import `RuntimeCapabilityGraph`:

```kotlin
override fun runtimeGraph(clientId: String) =
    runtimeMetadata(clientId).let { metadata ->
        fabricRuntimeGraph(
            clientId = clientId,
            fragments =
                listOf(
                    fabricRuntimeMetadataGraphFragment(
                        metadata = metadata,
                        sourceEvidence =
                            listOf(
                                RuntimeSourceEvidence("runtime-lane", "latest-current-official"),
                                RuntimeSourceEvidence("runtime-status", "metadata-only"),
                                RuntimeSourceEvidence("runtime-java", "java:25"),
                            ),
                    ),
                    fabricRegistryGraphFragment(
                        metadata = metadata,
                        available = false,
                    ),
                ),
        )
    }
```

If `fabricRuntimeMetadataGraphFragment` does not exist yet, add it next to
`fabricRuntimeMetadataGraph` and make `fabricRuntimeMetadataGraph` delegate to
it. This keeps the official lane composing graph fragments instead of merging
already-built graphs.

### Task 3: Verify, document, commit, and push

**Files:**
- Modify: `AGENTS.md`
- Modify: `driver-fabric-discovery/AGENTS.md`
- Modify: `driver-fabric-official/AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/evidence/2026-06-28-shared-fabric-registry-graph-projection.md`

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
jq -r '"status=" + .status + " client=" + .clientId' \
  driver-fabric-official/build/craftless-official-attach-probe/probe-result.json
jq -r '"actions=" + ((."x-craftless-actions"|length)|tostring) + " resources=" + ((."x-craftless-resources"|length)|tostring)' \
  driver-fabric-official/build/craftless-official-attach-probe/client-openapi.json
```

Expected: `status=ATTACHED`, `actions=0`, and `resources=2`.

- [x] **Step 4: Clean generated probe runtime dirs**

Run:

```sh
rm -rf driver-fabric-official/logs driver-fabric-official/run
git status --short --branch
```

Expected: no generated `logs/` or `run/` dirs remain.

- [x] **Step 5: Commit and push**

Run:

```sh
git add .
git commit -m "refactor: share fabric registry graph projection"
git push origin main
git status --short --branch
git rev-parse HEAD origin/main
```
