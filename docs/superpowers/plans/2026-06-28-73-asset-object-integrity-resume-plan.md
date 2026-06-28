# Asset Object Integrity Resume Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make cache preparation re-fetch corrupt Minecraft asset-object files instead of blindly reusing any existing path.

**Architecture:** Carry the asset object's expected SHA-1 from the asset index into `CachePreparedArtifact`. Keep existing idempotent file reuse, but verify SHA-1 before reusing asset objects and validate downloaded asset bytes before writing.

**Tech Stack:** Kotlin/JVM, kotlinx.serialization, Ktor Client, Gradle through mise.

---

### Task 1: Add Failing Asset Integrity Test

**Files:**
- Modify: `daemon/src/test/kotlin/com/minekube/craftless/daemon/CachePreparationServiceTest.kt`

- [x] **Step 1: Write failing test**

  Add a test named `cache preparation refetches corrupt existing asset objects`
  that:

  - creates a fake asset index with object hash `e2b4694e41e508d3cba98550e509c7fc82aaca8a`;
  - writes `corrupt-asset` to `cache/assets/objects/e2/e2b4694e41e508d3cba98550e509c7fc82aaca8a`;
  - configures the test fetcher to return bytes whose SHA-1 is
    `e2b4694e41e508d3cba98550e509c7fc82aaca8a`;
  - runs `CachePreparationService.prepare(...)`;
  - asserts the existing corrupt file was replaced with the downloaded bytes;
  - asserts the asset URL was fetched exactly once;
  - runs prepare again and asserts the asset URL fetch count stays at one.

- [x] **Step 2: Verify RED**

  Run:

  ```sh
  mise exec -- gradle :daemon:test --tests '*CachePreparationServiceTest.cache preparation refetches corrupt existing asset objects'
  ```

  Expected before implementation: FAIL because the corrupt existing file is
  reused and the asset URL is not fetched.

### Task 2: Carry Asset SHA-1 Metadata

**Files:**
- Modify: `protocol/src/main/kotlin/com/minekube/craftless/protocol/CacheModels.kt`
- Modify: `daemon/src/main/kotlin/com/minekube/craftless/daemon/CachePreparationService.kt`

- [x] **Step 1: Add optional artifact SHA-1**

  Add `val sha1: String? = null` to `CachePreparedArtifact`.

- [x] **Step 2: Populate asset object SHA-1**

  Set `sha1 = hash.lowercase()` on `MinecraftAssetObject.artifact`.

- [x] **Step 3: Keep non-asset artifacts unchanged**

  Do not populate `sha1` for client jars, libraries, runtime files, loader
  metadata, or launch metadata in this phase.

### Task 3: Verify Before Reuse And Write

**Files:**
- Modify: `daemon/src/main/kotlin/com/minekube/craftless/daemon/CachePreparationService.kt`

- [x] **Step 1: Add byte SHA-1 helper**

  Add a private `ByteArray.sha1Hex()` helper near the existing `String.sha256Hex()`.

- [x] **Step 2: Verify existing files**

  Update `writeFetchedBytesArtifact(...)` so an existing target is reused only
  when `artifact.sha1 == null` or the existing bytes match `artifact.sha1`.

- [x] **Step 3: Validate downloaded bytes**

  After `metadataFetcher.fetchBytes(source)`, require downloaded bytes to match
  `artifact.sha1` when present. Use the message:

  ```text
  downloaded artifact checksum mismatch for <handle>
  ```

- [x] **Step 4: Verify GREEN**

  Run:

  ```sh
  mise exec -- gradle :daemon:test --tests '*CachePreparationServiceTest.cache preparation refetches corrupt existing asset objects'
  ```

### Task 4: Register Phase 73 And Verify

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/specs/2026-06-28-73-asset-object-integrity-resume-design.md`
- Create: `docs/superpowers/plans/2026-06-28-73-asset-object-integrity-resume-plan.md`

- [x] **Step 1: Register governance**

  Add Phase 73 to `AGENTS.md` and the checklist. State that it improves
  latest-version cache resumability and does not add support claims or gameplay
  APIs.

- [x] **Step 2: Run verification**

  Run:

  ```sh
  mise exec -- gradle :daemon:test --tests '*CachePreparationServiceTest.cache preparation refetches corrupt existing asset objects'
  mise exec -- gradle :daemon:test --tests '*CachePreparationServiceTest*'
  git diff --check
  mise run architecture-check
  mise run ci
  ```

- [ ] **Step 3: Commit, push, and verify CI**

  Run:

  ```sh
  git add AGENTS.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-73-asset-object-integrity-resume-design.md docs/superpowers/plans/2026-06-28-73-asset-object-integrity-resume-plan.md protocol/src/main/kotlin/com/minekube/craftless/protocol/CacheModels.kt daemon/src/main/kotlin/com/minekube/craftless/daemon/CachePreparationService.kt daemon/src/test/kotlin/com/minekube/craftless/daemon/CachePreparationServiceTest.kt
  git commit -m "daemon: verify cached asset object integrity"
  git push origin main
  gh run watch <latest-run-id> --repo minekube/craftless --exit-status
  ```

### Guardrails

- [x] No public gameplay action, route family, Fabric descriptor/binding pair,
  CLI gameplay catalog, scenario shortcut, compiled Fabric lane, public
  version-specific API, or Minecraft support claim is added.
- [x] Existing non-asset binary artifact reuse remains unchanged.
- [x] The change helps cache preparation resume latest-version probes by
  repairing corrupt asset objects instead of preserving them.
