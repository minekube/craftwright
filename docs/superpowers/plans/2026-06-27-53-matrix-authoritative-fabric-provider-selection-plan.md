# Matrix-Authoritative Fabric Provider Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Fabric runtime provider selection use the compatibility matrix as the source of truth before provider-reported support.

**Architecture:** Update `selectFabricRuntimeProvider(...)` to resolve the runtime identity against `defaultFabricCompatibilityMatrix()` by default. A provider may be selected only when the resolved lane is supported and the provider id matches that lane; provider support remains the final runtime check.

**Tech Stack:** Kotlin/JVM, kotlin.test, Gradle through mise.

---

### Task 1: Add Failing Provider Selection Tests

**Files:**
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/runtime/FabricRuntimeProviderTest.kt`
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricCurrentLaneRuntimeProviderTest.kt`

- [x] **Step 1: Add unsupported matrix lane rejection test**

  Add this test to `FabricRuntimeProviderTest`:

  ```kotlin
  @Test
  fun `provider selection rejects identities from unsupported compatibility lanes`() {
      val unsupportedIdentity = currentLaneIdentity().copy(gameVersion = "26.2")
      val provider =
          TestFabricRuntimeProvider(
              providerId = "fabric-current-lane",
              supportedGameVersion = "26.2",
              access = TestFabricRuntimeAccess(unsupportedIdentity),
          )

      val error =
          assertFailsWith<IllegalArgumentException> {
              selectFabricRuntimeProvider(unsupportedIdentity, listOf(provider))
          }

      assertTrue(error.message?.contains("runtime-lane-missing") == true)
  }
  ```

- [x] **Step 2: Add provider id mismatch test**

  Add this test to `FabricRuntimeProviderTest`:

  ```kotlin
  @Test
  fun `provider selection requires the supported matrix lane provider id`() {
      val identity = currentLaneIdentity()
      val provider =
          TestFabricRuntimeProvider(
              providerId = "fabric-compatible-but-wrong-id",
              supportedGameVersion = "1.21.6",
              access = TestFabricRuntimeAccess(identity),
          )

      val error =
          assertFailsWith<IllegalArgumentException> {
              selectFabricRuntimeProvider(identity, listOf(provider))
          }

      assertTrue(error.message?.contains("fabric-current-lane:provider-missing") == true)
  }
  ```

- [x] **Step 3: Keep current compiled-lane test matrix-backed**

  In `FabricCurrentLaneRuntimeProviderTest`, keep the existing
  `current compiled lane provider supports the matching runtime identity` test
  and assert that selection still returns the current provider through the
  matrix-backed selector.

- [x] **Step 4: Verify RED**

  Run:

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricRuntimeProviderTest*' --tests '*FabricCurrentLaneRuntimeProviderTest*'
  ```

  Expected: the new unsupported-lane test fails because the current selector
  trusts provider-reported support before consulting the compatibility matrix.

### Task 2: Make Selection Matrix-Authoritative

**Files:**
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/runtime/FabricRuntimeProvider.kt`

- [x] **Step 1: Add matrix parameter and lane resolution**

  Change the function signature to:

  ```kotlin
  internal fun selectFabricRuntimeProvider(
      identity: FabricRuntimeIdentity,
      providers: List<FabricRuntimeProvider>,
      matrix: FabricCompatibilityMatrix = defaultFabricCompatibilityMatrix(),
  ): FabricRuntimeProviderSelection
  ```

  Resolve `val lane = matrix.resolve(identity)` before evaluating providers.

- [x] **Step 2: Reject unsupported lanes**

  Before selecting a provider, reject non-supported lanes:

  ```kotlin
  if (lane.status != FabricCompatibilityStatus.SUPPORTED) {
      throw IllegalArgumentException(
          "no Fabric runtime provider supports Minecraft ${identity.gameVersion}: " +
              "${lane.providerId}:${lane.unsupportedReason ?: lane.status.name.lowercase()}",
      )
  }
  ```

- [x] **Step 3: Select only the lane provider id**

  Find the provider by lane provider id. If it is missing, throw:

  ```kotlin
  val provider =
      providers.firstOrNull { candidate -> candidate.id == lane.providerId }
          ?: throw IllegalArgumentException(
              "no Fabric runtime provider supports Minecraft ${identity.gameVersion}: " +
                  "${lane.providerId}:provider-missing",
          )
  ```

- [x] **Step 4: Keep provider support as final check**

  Evaluate only the selected provider's support. If unsupported, throw:

  ```kotlin
  val support = provider.support(identity)
  if (support.state != FabricRuntimeSupportState.SUPPORTED) {
      throw IllegalArgumentException(
          "no Fabric runtime provider supports Minecraft ${identity.gameVersion}: " +
              "${provider.id}:${support.reason}",
      )
  }
  ```

- [x] **Step 5: Verify focused tests GREEN**

  Run:

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricRuntimeProviderTest*' --tests '*FabricCurrentLaneRuntimeProviderTest*'
  ```

### Task 3: Register Phase 53

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`

- [x] **Step 1: Register Phase 53 in `AGENTS.md`**

  Add `53. matrix-authoritative Fabric provider selection.` to the active
  phase list. Document that provider support cannot override a compatibility
  matrix lane marked unsupported or missing.

- [x] **Step 2: Add checklist evidence**

  Add a Phase 53 checklist section with the spec path, plan path, matrix-first
  provider selection, unsupported latest-lane rejection, provider-id matching,
  and focused verification command.

### Task 4: Verify, Commit, Push, And Monitor

**Files:**
- Commit all Phase 53 files and changes.

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
  git add AGENTS.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-27-53-matrix-authoritative-fabric-provider-selection-design.md docs/superpowers/plans/2026-06-27-53-matrix-authoritative-fabric-provider-selection-plan.md driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/runtime/FabricRuntimeProvider.kt driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/runtime/FabricRuntimeProviderTest.kt driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricCurrentLaneRuntimeProviderTest.kt
  git commit -m "driver-fabric: make provider selection matrix authoritative"
  git push origin main
  ```

- [x] **Step 3: Verify remote CI**

  Run:

  ```sh
  latest_run_id="$(gh run list --repo minekube/craftless --branch main --json databaseId --jq '.[0].databaseId')"
  gh run watch "$latest_run_id" --repo minekube/craftless --exit-status
  ```
