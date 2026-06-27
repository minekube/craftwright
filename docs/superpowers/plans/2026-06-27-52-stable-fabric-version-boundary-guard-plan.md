# Stable Fabric Version Boundary Guard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforce the stable Fabric package boundary so version-scoped current-lane imports stay behind the bootstrap selector.

**Architecture:** Keep `FabricBootstrapSelector.kt` as the only stable Fabric production file that registers versioned bootstraps. Add source-scanning architecture tests for the stable package and selector metadata tests that compare registered bootstraps with the compatibility matrix's current supported lane.

**Tech Stack:** Kotlin/JVM, JUnit/kotlin.test, Gradle through mise.

---

### Task 1: Add Failing Boundary Tests

**Files:**
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/FabricBootstrapSelectorTest.kt`
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [x] **Step 1: Add selector registry metadata test**

  Add this test to `FabricBootstrapSelectorTest`:

  ```kotlin
  @Test
  fun `selector exposes registered bootstrap metadata without initialization`() {
      val bootstraps = FabricBootstrapSelector.registeredBootstraps()

      assertEquals(
          listOf(FabricCompiledLaneMetadata.PROVIDER_ID to FabricCompiledLaneMetadata.MINECRAFT_VERSION),
          bootstraps.map { bootstrap -> bootstrap.providerId to bootstrap.minecraftVersion },
      )
  }
  ```

- [x] **Step 2: Add selector compatibility matrix alignment test**

  Add this test and helper to `FabricBootstrapSelectorTest`:

  ```kotlin
  @Test
  fun `selector current bootstrap matches supported compatibility lane`() {
      val bootstrap = FabricBootstrapSelector.selectCurrentCompiledLane()
      val lane = defaultFabricCompatibilityMatrix().resolve(currentLaneIdentity())

      assertEquals(FabricCompatibilityStatus.SUPPORTED, lane.status)
      assertEquals(lane.providerId, bootstrap.providerId)
      assertEquals(lane.gameVersion, bootstrap.minecraftVersion)
  }

  private fun currentLaneIdentity(): FabricRuntimeIdentity =
      FabricRuntimeIdentity(
          gameVersion = FabricCompiledLaneMetadata.MINECRAFT_VERSION,
          loaderVersion = FabricCompiledLaneMetadata.LOADER_VERSION,
          fabricApiVersion = FabricCompiledLaneMetadata.FABRIC_API_VERSION,
          mappingsFingerprint = FabricCompiledLaneMetadata.MAPPINGS_FINGERPRINT,
          installedModsFingerprint = "mods:current-lane",
          registryFingerprint = "registries:current-lane",
          serverFeatureFingerprint = "server-features:local",
          permissionsFingerprint = "permissions:local",
      )
  ```

- [x] **Step 3: Add stable package source boundary test**

  Add this test to `FabricDriverModuleTest`:

  ```kotlin
  @Test
  fun `stable fabric production package imports versioned implementations only through bootstrap selector`() {
      val stablePackage =
          repositoryRoot().resolve(
              "driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric",
          )
      val violations =
          Files.list(stablePackage).use { paths ->
              paths
                  .filter { path -> path.fileName.toString().endsWith(".kt") }
                  .filter { path -> path.fileName.toString() != "FabricBootstrapSelector.kt" }
                  .filter { path ->
                      Files.readString(path).contains("com.minekube.craftless.driver.fabric.v")
                  }
                  .map { path -> stablePackage.relativize(path).toString() }
                  .sorted()
                  .toList()
          }

      assertEquals(emptyList(), violations)
  }
  ```

- [x] **Step 4: Verify RED**

  Run:

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricBootstrapSelectorTest*' --tests '*FabricDriverModuleTest.stable fabric production package imports versioned implementations only through bootstrap selector*'
  ```

  Expected: fails because `FabricBootstrapSelector.registeredBootstraps()` does
  not exist yet.

### Task 2: Expose Selector Registry Metadata

**Files:**
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/FabricBootstrapSelector.kt`

- [x] **Step 1: Add registered bootstrap metadata accessor**

  Add this function to `FabricBootstrapSelector`:

  ```kotlin
  fun registeredBootstraps(): List<FabricDriverBootstrap> = bootstraps
  ```

- [x] **Step 2: Verify focused tests GREEN**

  Run:

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricBootstrapSelectorTest*' --tests '*FabricDriverModuleTest.stable fabric production package imports versioned implementations only through bootstrap selector*'
  ```

### Task 3: Register Phase 52

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`

- [x] **Step 1: Register Phase 52 in `AGENTS.md`**

  Add `52. stable Fabric version boundary guard.` to the active phase list and
  document that stable top-level Fabric production files may import
  version-scoped implementations only from `FabricBootstrapSelector.kt`.

- [x] **Step 2: Add checklist evidence**

  Add a Phase 52 section to `docs/project-completion-checklist.md` with the
  spec path, plan path, stable-package import guard, selector metadata
  alignment, and focused verification command.

### Task 4: Verify, Commit, Push, And Monitor

**Files:**
- Commit all Phase 52 files and changes.

- [x] **Step 1: Run quality gates**

  Run:

  ```sh
  git diff --check
  mise exec -- gradle :driver-fabric:test
  mise run lint
  mise run architecture-check
  mise run ci
  ```

- [x] **Step 2: Commit and push**

  Run:

  ```sh
  git add AGENTS.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-27-52-stable-fabric-version-boundary-guard-design.md docs/superpowers/plans/2026-06-27-52-stable-fabric-version-boundary-guard-plan.md driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/FabricBootstrapSelector.kt driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/FabricBootstrapSelectorTest.kt driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt
  git commit -m "driver-fabric: guard stable fabric version boundary"
  git push origin main
  ```

- [x] **Step 3: Verify remote CI**

  Run:

  ```sh
  gh run list --repo minekube/craftless --branch main --limit 3
  latest_run_id="$(gh run list --repo minekube/craftless --branch main --json databaseId --jq '.[0].databaseId')"
  gh run watch "$latest_run_id" --repo minekube/craftless --exit-status
  ```
