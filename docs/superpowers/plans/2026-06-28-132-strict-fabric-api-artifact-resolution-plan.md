# Strict Fabric API Artifact Resolution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Fabric cache preparation fail explicitly when no Fabric API
artifact matches the resolved Minecraft version.

**Architecture:** Keep Fabric Loader and Fabric API metadata resolution inside
`CachePreparationService`. Preserve Maven metadata-driven Fabric API selection,
but return a required artifact instead of an optional artifact for Fabric
requests.

**Tech Stack:** Kotlin/JVM daemon tests, Ktor metadata fetcher, mise local
verification.

---

### Task 1: Governance

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/specs/2026-06-28-132-strict-fabric-api-artifact-resolution-design.md`
- Create: `docs/superpowers/plans/2026-06-28-132-strict-fabric-api-artifact-resolution-plan.md`

- [x] **Step 1: Add Phase 132 to AGENTS.md**

  Define it as Fabric cache compatibility plumbing only.

- [x] **Step 2: Add Phase 132 to checklist**

  Track it as support-enabling work that does not satisfy latest/older support
  by itself.

### Task 2: Add Red Daemon Test

**Files:**
- Modify: `daemon/src/test/kotlin/com/minekube/craftless/daemon/CachePreparationServiceTest.kt`

- [x] **Step 1: Write missing Fabric API test**

  Add a test that prepares Fabric for `1.21.7` with valid Minecraft, Fabric
  Loader, and profile metadata, but Fabric API Maven metadata containing only
  `0.129.0+1.21.6`.

- [x] **Step 2: Run test red**

  ```sh
  mise exec -- gradle :daemon:test --tests '*CachePreparationServiceTest.fabric cache preparation requires matching fabric api artifact*'
  ```

  Expected: fail before implementation because `prepare` succeeds instead of
  rejecting the missing Fabric API artifact.

### Task 3: Implement Strict Fabric API Resolution

**Files:**
- Modify: `daemon/src/main/kotlin/com/minekube/craftless/daemon/CachePreparationService.kt`

- [x] **Step 1: Remove silent `runCatching` fallback**

  Replace optional Fabric API resolution with a required helper.

- [x] **Step 2: Add clear missing-artifact error**

  Make `fabricApiModArtifact(minecraftVersion)` throw:

  ```text
  Fabric API artifact for Minecraft <version> was not found in Maven metadata
  ```

  when no metadata version ends with `+<version>`.

- [x] **Step 3: Keep successful path unchanged**

  Current matching metadata still returns
  `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/<version>/fabric-api-<version>.jar`.

### Task 4: Evidence, Verification, Commit

**Files:**
- Create: `docs/superpowers/evidence/2026-06-28-strict-fabric-api-artifact-resolution.md`

- [x] **Step 1: Run focused daemon tests**

  ```sh
  mise exec -- gradle :daemon:test --tests '*CachePreparationServiceTest.fabric cache preparation requires matching fabric api artifact*' --tests '*CachePreparationServiceTest.fabric cache preparation resolves fabric api mod artifact from maven metadata*'
  ```

- [x] **Step 2: Run local gates**

  ```sh
  git diff --check
  mise run ci
  ```

- [x] **Step 3: Commit and push**

  ```sh
  git add AGENTS.md docs/project-completion-checklist.md daemon/src/main/kotlin/com/minekube/craftless/daemon/CachePreparationService.kt daemon/src/test/kotlin/com/minekube/craftless/daemon/CachePreparationServiceTest.kt docs/superpowers/specs/2026-06-28-132-strict-fabric-api-artifact-resolution-design.md docs/superpowers/plans/2026-06-28-132-strict-fabric-api-artifact-resolution-plan.md docs/superpowers/evidence/2026-06-28-strict-fabric-api-artifact-resolution.md
  git commit -m "fix: require matching fabric api artifact"
  git push origin main
  ```

## Self-Review

- Spec coverage: strict missing-artifact failure, successful current metadata,
  focused verification, and full local gates are covered.
- Placeholder scan: no TODO/TBD placeholders.
- Scope: no runtime operation change, gameplay API, public route, compiled
  lane, Fabric dependency change, or support claim.
