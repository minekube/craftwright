# Client Logging Config Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cache Mojang client logging config metadata and include the resolved logging JVM argument in prepared real-client launch arguments.

**Architecture:** Keep logging config support in daemon cache preparation and protocol cache artifact metadata. The Minecraft version manifest remains the source of truth; Craftless derives a safe cache handle and resolves `${path}` into the prepared launch arguments. No gameplay OpenAPI, Fabric action, or CLI gameplay catalog changes are allowed.

**Tech Stack:** Kotlin/JVM, kotlinx.serialization JSON parsing, daemon/protocol cache models, Gradle through mise.

---

### Task 1: Add Logging Config Regression Test

**Files:**
- Modify: `daemon/src/test/kotlin/com/minekube/craftless/daemon/CachePreparationServiceTest.kt`

- [x] **Step 1: Write the failing test**

  Extend `cache preparation resolves and stores minecraft version metadata` so
  the version manifest fixture contains:

  ```json
  "logging": {
    "client": {
      "argument": "-Dlog4j.configurationFile=${path}",
      "file": {
        "id": "client-1.21.2.xml",
        "url": "$loggingConfigUrl"
      },
      "type": "log4j2-xml"
    }
  }
  ```

  Add binary fixture bytes:

  ```kotlin
  loggingConfigUrl to "<Configuration/>".encodeToByteArray()
  ```

  Add assertions:

  ```kotlin
  CachePreparedArtifactKind.MINECRAFT_LOG_CONFIG
  val loggingConfig = result.artifacts.single { it.kind == CachePreparedArtifactKind.MINECRAFT_LOG_CONFIG }
  assertEquals(loggingConfigUrl, loggingConfig.source)
  assertEquals("cache/minecraft/versions/1.21.6/logging/client-1.21.2.xml", loggingConfig.handle)
  assertEquals("<Configuration/>", Files.readString(workspace.resolve(loggingConfig.handle)))
  val launchArgumentsJson = Files.readString(workspace.resolve(launchArguments.handle))
  assertTrue(launchArgumentsJson.contains("-Dlog4j.configurationFile=cache/minecraft/versions/1.21.6/logging/client-1.21.2.xml"))
  ```

- [x] **Step 2: Run the focused test and verify RED**

  Run:

  ```sh
  mise exec -- gradle :daemon:test --tests 'com.minekube.craftless.daemon.CachePreparationServiceTest.cache preparation resolves and stores minecraft version metadata'
  ```

  Expected: compilation or assertion failure because
  `MINECRAFT_LOG_CONFIG` and logging config preparation do not exist yet.

- [x] **Step 3: Write the failing logging id validation test**

  Add:

  ```kotlin
  @Test
  fun `cache preparation rejects invalid minecraft logging config ids before writing cache handles`() =
      runBlocking {
          val failure =
              assertFailsWith<IllegalArgumentException> {
                  service.prepare(CachePrepareRequest("1.21.6", Loader.VANILLA))
              }

          assertEquals("Minecraft logging config id must be a file-safe segment", failure.message)
      }
  ```

  The full fixture should include `logging.client.file.id = "../client.xml"`.

- [x] **Step 4: Run the validation test and verify RED**

  Run:

  ```sh
  mise exec -- gradle :daemon:test --tests 'com.minekube.craftless.daemon.CachePreparationServiceTest.cache preparation rejects invalid minecraft logging config ids before writing cache handles'
  ```

  Expected: the test fails because invalid logging file ids are not rejected
  before deriving cache handles.

### Task 2: Add Protocol Artifact Kind

**Files:**
- Modify: `protocol/src/main/kotlin/com/minekube/craftless/protocol/CacheModels.kt`
- Modify: `protocol/src/test/kotlin/com/minekube/craftless/protocol/CacheModelsTest.kt` if enum ordering assertions require it.

- [x] **Step 1: Add the enum value**

  Add `MINECRAFT_LOG_CONFIG` to `CachePreparedArtifactKind` near other
  Minecraft metadata/cache artifacts:

  ```kotlin
  MINECRAFT_LOG_CONFIG,
  ```

- [x] **Step 2: Verify protocol tests**

  Run:

  ```sh
  mise exec -- gradle :protocol:test
  ```

### Task 3: Prepare Logging Config Artifact

**Files:**
- Modify: `daemon/src/main/kotlin/com/minekube/craftless/daemon/CachePreparationService.kt`

- [x] **Step 1: Parse logging metadata**

  Add an internal `MinecraftLoggingConfigArtifact` that validates a file-safe
  `id`, stores `source`, exposes artifact kind `MINECRAFT_LOG_CONFIG`, and
  handles files at:

  ```kotlin
  cache/minecraft/versions/$minecraftVersion/logging/$id
  ```

- [x] **Step 2: Include the artifact in preparation**

  Read logging metadata from `logging.client.file` in the version manifest.
  Add the logging artifact to the prepared artifact list when present and
  fetch/write its bytes like other binary artifacts.

- [x] **Step 3: Resolve the logging JVM argument**

  When building `launchArgumentsJson`, append the manifest logging client
  argument to the JVM arguments and resolve `${path}` with the prepared logging
  artifact handle.

- [x] **Step 4: Run the focused daemon test and verify GREEN**

  Run:

  ```sh
  mise exec -- gradle :daemon:test --tests 'com.minekube.craftless.daemon.CachePreparationServiceTest.cache preparation resolves and stores minecraft version metadata' --tests 'com.minekube.craftless.daemon.CachePreparationServiceTest.cache preparation rejects invalid minecraft logging config ids before writing cache handles'
  ```

  Expected: both tests pass; the prepared launch file contains the resolved
  logging JVM argument and invalid logging ids are rejected before cache handle
  construction.

### Task 4: Update Guardrails And Evidence

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`

- [x] **Step 1: Register Phase 43 in `AGENTS.md`**

  Add `43. client logging config.` to the active phase list and note that the
  phase changes supervisor cache/launch metadata only.

- [x] **Step 2: Add checklist evidence**

  Add a Phase 43 checklist section with spec path, plan path, behavior, and
  verification commands.

- [ ] **Step 3: Verify docs formatting**

  Run:

  ```sh
  git diff --check
  ```

### Task 5: Verify, Commit, And Push

**Files:**
- Verify the whole repository.

- [ ] **Step 1: Run focused and module tests**

  Run:

  ```sh
  mise exec -- gradle :protocol:test :daemon:test
  ```

- [ ] **Step 2: Run repository quality gates**

  Run:

  ```sh
  mise run lint
  mise run architecture-check
  mise run ci
  ```

- [ ] **Step 3: Commit and push**

  Run:

  ```sh
  git add AGENTS.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-27-43-client-logging-config-design.md docs/superpowers/plans/2026-06-27-43-client-logging-config-plan.md protocol/src/main/kotlin/com/minekube/craftless/protocol/CacheModels.kt daemon/src/main/kotlin/com/minekube/craftless/daemon/CachePreparationService.kt daemon/src/test/kotlin/com/minekube/craftless/daemon/CachePreparationServiceTest.kt
  git commit -m "daemon: prepare minecraft client logging config"
  git push origin main
  ```

- [ ] **Step 4: Verify remote CI**

  Run:

  ```sh
  gh run list --branch main --limit 3
  gh run watch <latest-run-id> --exit-status
  ```

  Expected: the latest `main` CI run passes.
