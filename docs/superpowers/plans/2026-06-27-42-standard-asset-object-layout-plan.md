# Standard Asset Object Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Store prepared Minecraft asset objects in Mojang's standard `assets/objects/<prefix>/<hash>` layout so real versioned clients can consume supervisor-prepared assets.

**Architecture:** Keep the fix in daemon cache preparation. The asset index remains the source of truth; each asset object handle is derived generically from its Mojang hash and source URL. No gameplay API, Fabric action descriptor, or CLI gameplay catalog changes are allowed.

**Tech Stack:** Kotlin/JVM, kotlinx.serialization JSON parsing, daemon cache tests, Gradle through mise.

---

### Task 1: Add Asset Layout Regression Test

**Files:**
- Modify: `daemon/src/test/kotlin/com/minekube/craftless/daemon/CachePreparationServiceTest.kt`

- [ ] **Step 1: Write the failing test**

  Add a focused assertion to `cache preparation resolves and stores minecraft version metadata`:

  ```kotlin
  assertEquals(
      "cache/assets/objects/ab/abcdef0123456789abcdef0123456789abcdef01",
      assetObject.handle,
  )
  ```

- [ ] **Step 2: Run the focused test and verify RED**

  Run:

  ```sh
  mise exec -- gradle :daemon:test --tests 'com.minekube.craftless.daemon.CachePreparationServiceTest.cache preparation resolves and stores minecraft version metadata'
  ```

  Expected: the test fails because the current handle uses a Craftless hash and
  `.asset` suffix instead of the Mojang object layout.

- [ ] **Step 3: Write the failing hash-validation test**

  Add:

  ```kotlin
  @Test
  fun `cache preparation rejects invalid minecraft asset hashes before writing cache handles`() =
      runBlocking {
          val failure =
              assertFailsWith<IllegalArgumentException> {
                  service.prepare(CachePrepareRequest("1.21.6", Loader.VANILLA))
              }

          assertEquals("Minecraft asset hash must be a SHA-1 hex string", failure.message)
      }
  ```

  The full fixture should include an asset index object with
  `"hash": "../not-a-sha1"`.

- [ ] **Step 4: Run the validation test and verify RED**

  Run:

  ```sh
  mise exec -- gradle :daemon:test --tests 'com.minekube.craftless.daemon.CachePreparationServiceTest.cache preparation rejects invalid minecraft asset hashes before writing cache handles'
  ```

  Expected: the test fails because invalid hashes are not rejected yet.

### Task 2: Implement Standard Asset Object Handles

**Files:**
- Modify: `daemon/src/main/kotlin/com/minekube/craftless/daemon/CachePreparationService.kt`

- [ ] **Step 1: Change asset object handle derivation**

  In `MinecraftAssetObject`, replace the handle with:

  ```kotlin
  handle = "cache/assets/objects/${hash.take(2)}/$hash"
  ```

- [ ] **Step 2: Validate asset hashes**

  Add an initializer to `MinecraftAssetObject`:

  ```kotlin
  init {
      require(hash.matches(Regex("[a-fA-F0-9]{40}"))) { "Minecraft asset hash must be a SHA-1 hex string" }
  }
  ```

- [ ] **Step 3: Run the focused test and verify GREEN**

  Run:

  ```sh
  mise exec -- gradle :daemon:test --tests 'com.minekube.craftless.daemon.CachePreparationServiceTest.cache preparation resolves and stores minecraft version metadata' --tests 'com.minekube.craftless.daemon.CachePreparationServiceTest.cache preparation rejects invalid minecraft asset hashes before writing cache handles'
  ```

  Expected: both tests pass; the service writes valid asset objects at the
  standard Mojang path and rejects invalid hashes before writing cache handles.

### Task 3: Update Guardrails And Evidence

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`

- [ ] **Step 1: Register Phase 42 in `AGENTS.md`**

  Add `42. standard asset object layout.` to the active phase list and note
  that the phase changes supervisor cache preparation only.

- [ ] **Step 2: Add checklist evidence**

  Add a Phase 42 checklist section with the spec path, plan path, asset layout
  behavior, and verification command.

- [ ] **Step 3: Verify docs formatting**

  Run:

  ```sh
  git diff --check
  ```

### Task 4: Verify, Commit, And Push

**Files:**
- Verify the whole repository.

- [ ] **Step 1: Run focused daemon tests**

  Run:

  ```sh
  mise exec -- gradle :daemon:test
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
  git add AGENTS.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-27-42-standard-asset-object-layout-design.md docs/superpowers/plans/2026-06-27-42-standard-asset-object-layout-plan.md daemon/src/main/kotlin/com/minekube/craftless/daemon/CachePreparationService.kt daemon/src/test/kotlin/com/minekube/craftless/daemon/CachePreparationServiceTest.kt
  git commit -m "daemon: use standard minecraft asset layout"
  git push origin main
  ```

- [ ] **Step 4: Verify remote CI**

  Run:

  ```sh
  gh run list --branch main --limit 3
  gh run watch <latest-run-id> --exit-status
  ```

  Expected: the latest `main` CI run passes.
