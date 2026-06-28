# Metadata Fallback Naming Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove broad old-path naming from daemon metadata fallback internals while preserving Mojang compatibility behavior.

**Architecture:** Add a focused daemon source guard, then rename private helpers/tests/locals to describe the concrete manifest condition. Keep public protocol values and launch literals unchanged.

**Tech Stack:** Kotlin/JVM daemon module, Kotlin test, Gradle through mise.

---

### Task 1: Add Red Metadata Naming Guard

**Files:**
- Modify: `daemon/src/test/kotlin/com/minekube/craftless/daemon/JavaRuntimeRequirementResolverTest.kt`

- [x] **Step 1: Add source guard**

  Add a test named `metadata fallback internals use pre metadata naming`.
  It should read:

  - `daemon/src/main/kotlin/com/minekube/craftless/daemon/MinecraftJavaRuntimeRequirementResolver.kt`
  - `daemon/src/main/kotlin/com/minekube/craftless/daemon/CachePreparationService.kt`
  - `daemon/src/test/kotlin/com/minekube/craftless/daemon/JavaRuntimeRequirementResolverTest.kt`

  It should reject split-string stale tokens:

  - `is` + `LegacyMinecraftVersion`
  - `nativeFrom` + `Legacy`
  - `legacy manifests`
  - `legacy manifest`

- [x] **Step 2: Run red guard**

  ```sh
  mise exec -- gradle :daemon:test --tests '*JavaRuntimeRequirementResolverTest.metadata fallback internals use pre metadata naming*'
  ```

  Expected: fail before implementation because current source/test names still
  use the stale wording.

### Task 2: Rename Metadata Fallback Internals

**Files:**
- Modify: `daemon/src/main/kotlin/com/minekube/craftless/daemon/MinecraftJavaRuntimeRequirementResolver.kt`
- Modify: `daemon/src/main/kotlin/com/minekube/craftless/daemon/CachePreparationService.kt`
- Modify: `daemon/src/test/kotlin/com/minekube/craftless/daemon/JavaRuntimeRequirementResolverTest.kt`

- [x] **Step 1: Rename Java runtime helper**

  Rename `isLegacyMinecraftVersion` to
  `isPreJavaRuntimeMetadataMinecraftVersion`.

- [x] **Step 2: Rename Java runtime fallback test**

  Rename the test to
  `uses Java 8 fallback only for manifests before Java runtime metadata`.

- [x] **Step 3: Rename native classifier local**

  Rename `nativeFromLegacy` to `nativeFromClassifier`.

### Task 3: Update Governance And Evidence

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/evidence/2026-06-28-metadata-fallback-naming-removal.md`

- [x] **Step 1: Add Phase 121 to AGENTS**
- [x] **Step 2: Add Phase 121 checklist section**
- [x] **Step 3: Record red/green/local gate evidence**

### Task 4: Verify, Commit, Push

- [x] **Step 1: Run focused tests**

  ```sh
  mise exec -- gradle :daemon:test --tests '*JavaRuntimeRequirementResolverTest.*'
  ```

- [x] **Step 2: Run local gates**

  ```sh
  git diff --check
  mise run ci
  ```

- [x] **Step 3: Commit and push**

  ```sh
  git add AGENTS.md daemon/src/main/kotlin/com/minekube/craftless/daemon/MinecraftJavaRuntimeRequirementResolver.kt daemon/src/main/kotlin/com/minekube/craftless/daemon/CachePreparationService.kt daemon/src/test/kotlin/com/minekube/craftless/daemon/JavaRuntimeRequirementResolverTest.kt docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-121-metadata-fallback-naming-removal-design.md docs/superpowers/plans/2026-06-28-121-metadata-fallback-naming-removal-plan.md docs/superpowers/evidence/2026-06-28-metadata-fallback-naming-removal.md
  git commit -m "refactor: rename metadata fallback internals"
  git push origin main
  ```

## Self-Review

- Spec coverage: source guard, Java fallback naming, native classifier naming,
  governance, evidence, local gates, and non-goals are covered.
- Placeholder scan: no TODO/TBD placeholders.
- Scope: no behavior change, gameplay action, route family, CLI gameplay
  catalog, Fabric binding, scenario shortcut, compiled lane, public
  version-specific API, or support claim.
