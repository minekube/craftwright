# Static Unsupported Version Lane Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove hand-maintained latest/older unsupported Fabric lanes from product runtime code while preserving historical probe evidence and keeping real multi-version support open.

**Architecture:** Change matrix tests first so `26.2` and `1.20.6` must resolve through the generic unsupported path. Remove those static lanes from `FabricCompatibilityMatrix`, remove the `26.2` Gradle smoke JSON branch, and update current docs so active product status no longer presents static unsupported lane ids as runtime design.

**Tech Stack:** Kotlin/JVM, Gradle Kotlin DSL, Fabric driver runtime tests, Markdown.

---

### Task 1: Add Red Runtime Guard

**Files:**
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/runtime/FabricCompatibilityMatrixTest.kt`

- [x] **Step 1: Replace static-lane expectations**

  Replace the tests that expect `latest-release-26-2` and
  `older-release-1-20-6` with one test:

  ```kotlin
  @Test
  fun `matrix does not catalog static unsupported latest or older lanes`() {
      val matrix = defaultFabricCompatibilityMatrix()

      val latest = matrix.resolve(currentLaneIdentity().copy(gameVersion = "26.2"))
      val older = matrix.resolve(currentLaneIdentity().copy(gameVersion = "1.20.6"))

      assertEquals("fabric-unsupported-26-2", latest.id)
      assertEquals(FabricCompatibilityStatus.UNSUPPORTED, latest.status)
      assertEquals("unsupported-version", latest.unsupportedReason)
      assertEquals("fabric-unsupported", latest.providerId)
      assertEquals("fabric-unsupported-1-20-6", older.id)
      assertEquals(FabricCompatibilityStatus.UNSUPPORTED, older.status)
      assertEquals("unsupported-version", older.unsupportedReason)
      assertEquals("fabric-unsupported", older.providerId)
  }
  ```

- [x] **Step 2: Run red guard**

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricCompatibilityMatrixTest.matrix does not catalog static unsupported latest or older lanes*'
  ```

  Expected: fails because the matrix still contains the static latest/older
  unsupported lanes.

### Task 2: Remove Static Unsupported Lanes

**Files:**
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/runtime/FabricCompatibilityMatrix.kt`
- Modify: `driver-fabric/build.gradle.kts`
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [x] **Step 1: Delete latest/older matrix entries**

  Remove the `latest-release-26-2` and `older-release-1-20-6` entries from
  `defaultFabricCompatibilityMatrix()`.

- [x] **Step 2: Remove smoke JSON version branch**

  In `fabricSmokeRuntimeLaneJson`, keep the compiled-lane branch and replace
  the `26.2` branch with the existing generic unsupported branch.

- [x] **Step 3: Add build-script source guard**

  Add a `FabricDriverModuleTest` assertion that
  `driver-fabric/build.gradle.kts` does not contain:

  ```text
  "latest-release-26-2"
  "older-release-1-20-6"
  "\"26.2\" ->"
  ```

- [x] **Step 4: Run focused green**

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricCompatibilityMatrixTest*' --tests '*FabricDriverModuleTest*'
  ```

### Task 3: Update Current Docs

**Files:**
- Modify: `AGENTS.md`
- Modify: `README.md`
- Modify: `docs/roadmap.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/evidence/2026-06-28-static-unsupported-version-lane-removal.md`

- [x] **Step 1: Register Phase 93**

  Add `93. static unsupported version lane removal.` to AGENTS and explain
  that static unsupported lanes are historical diagnostics only.

- [x] **Step 2: Update current status docs**

  Update README, roadmap, and checklist current-status wording so they say
  latest/older probes are historical diagnostic evidence, while active product
  code uses provider-backed lanes plus generic unsupported fallback.

- [x] **Step 3: Record evidence**

  Record red, green, final local gates, and push policy in:

  ```text
  docs/superpowers/evidence/2026-06-28-static-unsupported-version-lane-removal.md
  ```

### Task 4: Verify, Commit, And Push

**Files:**
- All modified files from Tasks 1-3

- [x] **Step 1: Run local gates**

  ```sh
  git diff --check
  rg -n "latest-release-26-2|older-release-1-20-6|\\\"26\\.2\\\" ->" driver-fabric/src/main driver-fabric/build.gradle.kts README.md docs/roadmap.md
  mise exec -- gradle :driver-fabric:test
  ```

  Expected: `rg` returns no active product/current-doc matches.

- [ ] **Step 2: Commit and push**

  ```sh
  git add AGENTS.md README.md docs/roadmap.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-93-static-unsupported-version-lane-removal-design.md docs/superpowers/plans/2026-06-28-93-static-unsupported-version-lane-removal-plan.md docs/superpowers/evidence/2026-06-28-static-unsupported-version-lane-removal.md driver-fabric/build.gradle.kts driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/runtime/FabricCompatibilityMatrix.kt driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/runtime/FabricCompatibilityMatrixTest.kt driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt
  git commit -m "driver-fabric: remove static unsupported version lanes"
  git push origin main
  ```

## Self-Review

- Spec coverage: matrix removal, smoke JSON branch removal, docs/current status,
  evidence, and gates are covered.
- Placeholder scan: no TODO/TBD placeholders.
- Scope: no new support claim, public version-specific API, gameplay action,
  route family, or CLI catalog.
