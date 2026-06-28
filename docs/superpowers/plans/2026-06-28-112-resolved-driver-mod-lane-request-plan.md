# Resolved Driver Mod Lane Request Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make prepared client runtime driver-mod selection use the concrete Minecraft version resolved by cache preparation.

**Architecture:** Keep alias resolution in `CachePreparationService`. Change only the bridge from prepared cache results into `ClientRuntimeDriverModRequest` so the provider receives `CachePrepareResult.minecraftVersion` instead of `CreateClientRequest.version`.

**Tech Stack:** Kotlin/JVM, daemon tests, Gradle through mise.

---

### Task 1: Add Red Provider Request Test

**Files:**
- Modify: `daemon/src/test/kotlin/com/minekube/craftless/daemon/LocalSessionApiServerTest.kt`

- [x] **Step 1: Extend prepared metadata fixture with latest aliases**

  Add `latest.release = "1.21.6"` and `latest.snapshot = "26.3-snapshot-1"`
  to `preparedRuntimeMetadataFetcher()` so local prepared-runtime tests can
  request `latest-release` without live network access.

- [x] **Step 2: Add alias provider request test**

  Add a test named
  `prepared runtime asks driver mod provider for resolved runtime lane`.

  It should create a Fabric client with:

  ```json
  {
    "id": "alice",
    "version": "latest-release",
    "loader": "FABRIC",
    "profile": { "kind": "OFFLINE", "name": "Alice" }
  }
  ```

  The recording `ClientRuntimeDriverModProvider` should receive:

  ```kotlin
  ClientRuntimeDriverModRequest(
      loader = Loader.FABRIC,
      minecraftVersion = "1.21.6",
      loaderVersion = "0.17.2",
  )
  ```

- [x] **Step 3: Run red test**

  ```sh
  mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.prepared runtime asks driver mod provider for resolved runtime lane*'
  ```

  Expected: fails before implementation because the provider request still
  uses `latest-release`.

### Task 2: Use Prepared Runtime Identity

**Files:**
- Modify: `daemon/src/main/kotlin/com/minekube/craftless/daemon/WorkspaceClientRuntimeDriverFactory.kt`

- [x] **Step 1: Change provider request version source**

  In `CachePrepareResult.withConfiguredDriverMod`, change:

  ```kotlin
  minecraftVersion = request.version
  ```

  to:

  ```kotlin
  minecraftVersion = minecraftVersion
  ```

- [x] **Step 2: Run focused green tests**

  ```sh
  mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.prepared runtime asks driver mod provider for *runtime lane*'
  ```

### Task 3: Update Governance And Evidence

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/evidence/2026-06-28-resolved-driver-mod-lane-request.md`

- [x] **Step 1: Add Phase 112 to AGENTS**
- [x] **Step 2: Add Phase 112 checklist section**
- [x] **Step 3: Record red/green and local gate evidence**

### Task 4: Verify, Commit, Push

- [x] **Step 1: Run local gates**

  ```sh
  git diff --check
  mise run ci
  ```

- [x] **Step 2: Commit and push**

  ```sh
  git add AGENTS.md daemon/src/main/kotlin/com/minekube/craftless/daemon/WorkspaceClientRuntimeDriverFactory.kt daemon/src/test/kotlin/com/minekube/craftless/daemon/LocalSessionApiServerTest.kt docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-112-resolved-driver-mod-lane-request-design.md docs/superpowers/plans/2026-06-28-112-resolved-driver-mod-lane-request-plan.md docs/superpowers/evidence/2026-06-28-resolved-driver-mod-lane-request.md
  git commit -m "fix: select driver mods by resolved runtime lane"
  git push origin main
  ```

## Self-Review

- Spec coverage: alias request, resolved concrete provider version, resolved
  loader version, exact behavior preservation, governance, and verification
  are covered.
- Placeholder scan: no TODO/TBD placeholders.
- Scope: no new compiled lane, gameplay action, public route, CLI gameplay
  catalog, scenario shortcut, or support claim.
