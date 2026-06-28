# Shared Fabric Runtime Resource Projection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Make both Fabric lanes project runtime metadata into the runtime graph through shared `driver-fabric-discovery` code.

**Architecture:** Keep metadata and graph resource projection generic in `driver-fabric-discovery`. Lane modules continue to provide lane-specific evidence and keep Minecraft game-class probes, registry scans, server-feature scans, and gameplay operation adapters outside the shared module.

**Tech Stack:** Kotlin/JVM, Craftless protocol runtime graph DTOs, Fabric Loader metadata helpers, Gradle 9.6, mise-managed Java.

---

### Task 1: Add red tests and guards

**Files:**
- Modify: `driver-fabric-discovery/src/test/kotlin/com/minekube/craftless/driver/fabric/discovery/FabricRuntimeMetadataProviderTest.kt`
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [x] **Step 1: Write failing shared projection test**

Add a test:

```kotlin
@Test
fun `runtime resource projection includes metadata and caller lane evidence`() {
    val node =
        fabricRuntimeResourceNode(
            metadata =
                DriverRuntimeMetadata(
                    loaderVersion = "0.19.3",
                    driver = "craftless-driver-fabric-official",
                    driverVersion = "0.1.0-SNAPSHOT",
                    mappings = "craftless-official-bindings-26-2",
                    installedModsFingerprint = "mods:test",
                    registryFingerprint = "registries:not-discovered",
                    serverFeatureFingerprint = "server-features:not-connected",
                    permissionsFingerprint = "permissions:local-client",
                ),
            sourceEvidence =
                listOf(
                    RuntimeSourceEvidence("runtime-lane", "latest-current-official"),
                    RuntimeSourceEvidence("runtime-status", "metadata-only"),
                    RuntimeSourceEvidence("runtime-java", "java:25"),
                ),
        )

    val evidence = node.sourceEvidence.associate { it.kind to it.fingerprint }

    assertEquals("runtime", node.id)
    assertEquals(RuntimeAvailability.available(), node.availability)
    assertEquals("mods:test", evidence["installed-mods"])
    assertEquals("registries:not-discovered", evidence["registry"])
    assertEquals("server-features:not-connected", evidence["server-features"])
    assertEquals("permissions:local-client", evidence["permissions"])
    assertEquals("latest-current-official", evidence["runtime-lane"])
    assertEquals("metadata-only", evidence["runtime-status"])
    assertEquals("java:25", evidence["runtime-java"])
}
```

- [x] **Step 2: Write failing official architecture guard**

In the official-lane architecture test, assert:

```kotlin
assertTrue(officialBackend.contains("fabricRuntimeResourceNode"))
assertFalse(officialBackend.contains("RuntimeResourceNode("))
```

- [x] **Step 3: Run red**

Run:

```sh
mise exec -- gradle :driver-fabric-discovery:test :driver-fabric:test --tests '*FabricDriverModuleTest.official lane has opt in launch attach probe task without packaging support claim'
```

Expected: FAIL because `fabricRuntimeResourceNode` is missing and the official
backend still directly constructs `RuntimeResourceNode`.

### Task 2: Implement shared projection and wire lanes

**Files:**
- Modify: `driver-fabric-discovery/src/main/kotlin/com/minekube/craftless/driver/fabric/discovery/FabricRuntimeMetadataProvider.kt`
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricCapabilityProbe.kt`
- Modify: `driver-fabric-official/src/main/kotlin/com/minekube/craftless/driver/fabric/official/OfficialFabricDriverBackend.kt`

- [x] **Step 1: Add shared helper**

Add:

```kotlin
fun fabricRuntimeResourceNode(
    metadata: DriverRuntimeMetadata,
    sourceEvidence: List<RuntimeSourceEvidence> = emptyList(),
): RuntimeResourceNode =
    RuntimeResourceNode(
        id = "runtime",
        availability = RuntimeAvailability.available(),
        sourceEvidence =
            listOf(
                RuntimeSourceEvidence("installed-mods", metadata.installedModsFingerprint),
                RuntimeSourceEvidence("registry", metadata.registryFingerprint),
                RuntimeSourceEvidence("server-features", metadata.serverFeatureFingerprint),
                RuntimeSourceEvidence("permissions", metadata.permissionsFingerprint),
            ) + sourceEvidence,
    )
```

- [x] **Step 2: Wire Yarn/remap probe**

Replace the `RuntimeResourceNode` construction in
`FabricRuntimeMetadataCapabilityProbe` with:

```kotlin
fabricRuntimeResourceNode(
    metadata = context.runtimeMetadata,
    sourceEvidence = context.compatibilityLane.sourceEvidence(),
)
```

- [x] **Step 3: Wire official backend**

Replace the direct official runtime resource construction with:

```kotlin
val metadata = runtimeMetadata(clientId)
RuntimeCapabilityGraph(
    clientId = clientId,
    resources =
        listOf(
            fabricRuntimeResourceNode(
                metadata = metadata,
                sourceEvidence =
                    listOf(
                        RuntimeSourceEvidence("runtime-lane", "latest-current-official"),
                        RuntimeSourceEvidence("runtime-status", "metadata-only"),
                        RuntimeSourceEvidence("runtime-java", "java:25"),
                    ),
            ),
        ),
)
```

### Task 3: Verify, document, commit, and push

**Files:**
- Modify: `AGENTS.md`
- Modify: `driver-fabric-discovery/AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/evidence/2026-06-28-shared-fabric-runtime-resource-projection.md`

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

- [x] **Step 4: Document evidence and push**

Record verification in the checklist/evidence file, then run:

```sh
git status --short --branch
git add .
git commit -m "refactor: share fabric runtime resource projection"
git push origin main
```
