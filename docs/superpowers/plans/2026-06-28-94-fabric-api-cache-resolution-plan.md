# Fabric API Cache Resolution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve and cache Fabric API mod artifacts from Fabric Maven metadata during cache preparation.

**Architecture:** Add a `FABRIC_MOD` cache artifact kind and a `mods` list to `CacheLaunchPlan`. Extend `CachePreparationService.resolveFabricMetadata` to fetch Fabric API Maven metadata, select the latest version whose suffix matches the requested Minecraft version, cache that jar as a Fabric mod artifact, and include its handle in the launch plan.

**Tech Stack:** Kotlin/JVM, kotlinx.serialization, Ktor-backed cache fetcher, Gradle via mise.

---

### Task 1: Add Red Protocol And Daemon Tests

**Files:**
- Modify: `protocol/src/main/kotlin/com/minekube/craftless/protocol/CacheModels.kt`
- Modify: `daemon/src/test/kotlin/com/minekube/craftless/daemon/CachePreparationServiceTest.kt`

- [x] **Step 1: Add failing cache test**

  Add a test named `fabric cache preparation resolves fabric api mod artifact from maven metadata`.

  The fixture must include:

  - Fabric Loader versions/profile responses for `1.21.6`;
  - Fabric API Maven metadata with versions `0.127.0+1.21.5`,
    `0.128.2+1.21.6`, and `0.129.0+1.21.6`;
  - binary response for
    `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.129.0+1.21.6/fabric-api-0.129.0+1.21.6.jar`.

  Assert:

  ```kotlin
  val fabricApi = result.artifacts.single { it.kind == CachePreparedArtifactKind.FABRIC_MOD }
  assertEquals("https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.129.0+1.21.6/fabric-api-0.129.0+1.21.6.jar", fabricApi.source)
  assertTrue(result.launch.mods.contains(fabricApi.handle))
  assertEquals("fabric-api-jar", Files.readString(workspace.resolve(fabricApi.handle)))
  ```

- [x] **Step 2: Run red test**

  ```sh
  mise exec -- gradle :daemon:test --tests '*CachePreparationServiceTest.fabric cache preparation resolves fabric api mod artifact from maven metadata*'
  ```

  Expected: fails to compile or run because `FABRIC_MOD` and `CacheLaunchPlan.mods`
  do not exist yet.

### Task 2: Add Fabric Mod Launch Metadata

**Files:**
- Modify: `protocol/src/main/kotlin/com/minekube/craftless/protocol/CacheModels.kt`

- [x] **Step 1: Add artifact kind**

  Add `FABRIC_MOD` to `CachePreparedArtifactKind`.

- [x] **Step 2: Add launch mods**

  Add `val mods: List<String> = emptyList()` to `CacheLaunchPlan`, and make
  `fromArtifacts` collect handles with `CachePreparedArtifactKind.FABRIC_MOD`.

- [x] **Step 3: Run protocol tests**

  ```sh
  mise exec -- gradle :protocol:test
  ```

### Task 3: Resolve Fabric API Metadata

**Files:**
- Modify: `daemon/src/main/kotlin/com/minekube/craftless/daemon/CachePreparationService.kt`

- [x] **Step 1: Add Fabric API metadata URL**

  Add:

  ```kotlin
  private const val FABRIC_API_MAVEN_METADATA_URL =
      "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml"
  ```

- [x] **Step 2: Extend `FabricCacheMetadata`**

  Add `val fabricApi: FabricModArtifact?` to `FabricCacheMetadata`.

- [x] **Step 3: Resolve Fabric API in `resolveFabricMetadata`**

  Fetch the Maven metadata URL and select the last `<version>` whose text ends
  with `+${request.minecraftVersion}`.

- [x] **Step 4: Add `FabricModArtifact`**

  Add a data class that creates a `FABRIC_MOD` artifact under
  `cache/mods/fabric/<sha>.jar`.

- [x] **Step 5: Include and download Fabric mod artifacts**

  Add the Fabric API artifact to `artifacts`, write it via
  `writeFetchedBytesArtifact`, and let `CacheLaunchPlan.fromArtifacts` expose
  it in `launch.mods`.

- [x] **Step 6: Run focused green**

  ```sh
  mise exec -- gradle :daemon:test --tests '*CachePreparationServiceTest.fabric cache preparation resolves fabric api mod artifact from maven metadata*'
  ```

### Task 4: Register Phase 94 And Verify

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/evidence/2026-06-28-fabric-api-cache-resolution.md`

- [x] **Step 1: Register Phase 94**

  Add `94. Fabric API cache resolution.` to AGENTS and document that Fabric API
  metadata resolution is a foundation step, not a support claim.

- [x] **Step 2: Update checklist and evidence**

  Add Phase 94 to `docs/project-completion-checklist.md` and create the
  evidence file with red, green, local gates, and remote-CI policy.

- [x] **Step 3: Run local gates**

  ```sh
  git diff --check
  mise exec -- gradle :protocol:test :daemon:test
  ```

### Task 5: Commit And Push

**Files:**
- All modified files from Tasks 1-4

- [ ] **Step 1: Commit and push**

  ```sh
  git add AGENTS.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-94-fabric-api-cache-resolution-design.md docs/superpowers/plans/2026-06-28-94-fabric-api-cache-resolution-plan.md docs/superpowers/evidence/2026-06-28-fabric-api-cache-resolution.md protocol/src/main/kotlin/com/minekube/craftless/protocol/CacheModels.kt daemon/src/main/kotlin/com/minekube/craftless/daemon/CachePreparationService.kt daemon/src/test/kotlin/com/minekube/craftless/daemon/CachePreparationServiceTest.kt
  git commit -m "daemon: resolve fabric api cache artifacts"
  git push origin main
  ```

## Self-Review

- Spec coverage: Fabric API Maven metadata resolution, cache artifact, launch
  mod handle, docs/evidence, and tests are covered.
- Placeholder scan: no TODO/TBD placeholders.
- Scope: no new support claim, static version catalog, public version-specific
  API, gameplay action, route family, or CLI catalog.
