# Official Fabric Live Client State Probe Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the 26.x official Fabric lane project client-state resources from the running official Minecraft client instead of a hard-coded disconnected snapshot.

**Architecture:** Add a tiny official-lane provider that converts official/Mojang-mapped Minecraft client state into the shared `FabricClientStateGraphSnapshot`. Keep graph projection in `driver-fabric-discovery`, keep the official backend operation-free, and leave packaging/support claims unchanged.

**Tech Stack:** Kotlin/JVM, Fabric Loom official mappings, Ktor attach transport, Gradle through mise.

---

### Task 1: Tests And Guards

**Files:**
- Modify: `driver-fabric-official/src/test/kotlin/com/minekube/craftless/driver/fabric/official/OfficialFabricSharedRuntimeMetadataTest.kt`
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [ ] **Step 1: Add an official backend test with an injected live snapshot**

Add a test that constructs `OfficialFabricDriverBackend` with
`clientStateProvider = OfficialFabricClientStateProvider { FabricClientStateGraphSnapshot(...) }`,
then asserts client/player/inventory/world/entity handles are available,
`recipe` is unavailable only when recipes are false, and operations remain empty.

- [ ] **Step 2: Add architecture guards**

Extend the existing official-lane guard to assert:

```kotlin
assertTrue(officialSources.contains("OfficialFabricClientStateProvider"))
assertTrue(officialSources.contains("net.minecraft.client.Minecraft"))
assertFalse(officialBackend.contains("FabricClientStateGraphSnapshot.disconnected()"))
assertFalse(officialBackend.contains("metadata-only backend"))
assertFalse(officialBackend.contains("import com.minekube.craftless.protocol.RuntimeCapabilityGraph"))
```

- [ ] **Step 3: Run red tests**

Run:

```sh
mise exec -- gradle :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*' :driver-fabric:test --tests '*FabricDriverModuleTest.official lane has opt in launch attach probe task without packaging support claim'
```

Expected: fail because `OfficialFabricClientStateProvider` and live snapshot wiring do not exist yet.

### Task 2: Official Client-State Provider

**Files:**
- Create: `driver-fabric-official/src/main/kotlin/com/minekube/craftless/driver/fabric/official/OfficialFabricClientStateProvider.kt`
- Modify: `driver-fabric-official/src/main/kotlin/com/minekube/craftless/driver/fabric/official/OfficialFabricDriverBackend.kt`

- [ ] **Step 1: Implement provider interface and production provider**

Create `OfficialFabricClientStateProvider.kt` with:

```kotlin
internal fun interface OfficialFabricClientStateProvider {
    fun snapshot(): FabricClientStateGraphSnapshot
}
```

The production provider should use `Minecraft.getInstance()`, query on the
client thread, and map only booleans into `FabricClientStateGraphSnapshot`.

- [ ] **Step 2: Wire backend**

Add a `clientStateProvider` constructor parameter to
`OfficialFabricDriverBackend`, use `fabricClientStateGraphFragment(clientStateProvider.snapshot())`,
and update messages/evidence from `metadata-only` to `client-state-probe`.

- [ ] **Step 3: Run green tests**

Run the same focused test command from Task 1 and expect `BUILD SUCCESSFUL`.

### Task 3: Evidence, Docs, And Probe

**Files:**
- Modify: `AGENTS.md`
- Modify: `driver-fabric-official/AGENTS.md`
- Modify: `README.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/evidence/2026-06-28-official-fabric-live-client-state-probe.md`

- [ ] **Step 1: Update docs/checklist**

Document Phase 157 and make clear it is live client-state evidence only, not
latest/current support completion.

- [ ] **Step 2: Run verification**

Run:

```sh
mise run ci
CRAFTLESS_OFFICIAL_FABRIC_ATTACH_PROBE=1 CRAFTLESS_OFFICIAL_ATTACH_PROBE_TIMEOUT_MS=120000 mise exec -- gradle :driver-fabric-official:officialFabricAttachProbe
git diff --check
```

- [ ] **Step 3: Record probe evidence**

Record status, OpenAPI counts, and client-state resource availability in the
Phase 157 evidence file.

- [ ] **Step 4: Commit and push**

Commit:

```sh
git commit -m "feat: probe official fabric client state"
git push origin main
```
