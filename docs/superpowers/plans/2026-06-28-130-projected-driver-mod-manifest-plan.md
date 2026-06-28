# Projected Driver Mod Manifest Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate a clean packaged `driver-mods.json` manifest as a projection
of the private Fabric driver lane catalog.

**Architecture:** `driver-fabric` remains responsible for the private lane
catalog. `cli` parses that catalog in Gradle, validates required fields, maps
`distributionPath` to the public manifest `path`, and writes only daemon-facing
runtime selection fields.

**Tech Stack:** Gradle Kotlin DSL, Groovy `JsonSlurper`, Kotlin/JVM tests, Bun
distribution tests through mise.

---

### Task 1: Governance

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/specs/2026-06-28-130-projected-driver-mod-manifest-design.md`
- Create: `docs/superpowers/plans/2026-06-28-130-projected-driver-mod-manifest-plan.md`

- [x] **Step 1: Add Phase 130 to AGENTS.md**

  Define it as manifest-schema hygiene only: private catalog fields stay out of
  the public packaged manifest.

- [x] **Step 2: Add Phase 130 to checklist**

  Track it as support-enabling work that does not satisfy latest/older support
  by itself.

### Task 2: Add Red Distribution Guards

**Files:**
- Modify: `playwright/src/distribution.test.ts`

- [x] **Step 1: Require projected manifest generation**

  Extend `CLI distribution packages driver mod manifest` to assert
  `cli/build.gradle.kts` contains a projection helper such as
  `renderDriverModManifest` and no longer contains raw catalog copying.

- [x] **Step 2: Require package task private-field checks**

  Extend `.mise.toml` package verification to reject `artifactKey` and
  `distributionPath` in the packaged `driver-mods.json`.

- [x] **Step 3: Run red test**

  ```sh
  mise exec -- bun test playwright/src/distribution.test.ts
  ```

  Expected: fail before implementation because `writeDriverModManifest` still
  writes `catalog.readText()` directly.

### Task 3: Implement Manifest Projection

**Files:**
- Modify: `cli/build.gradle.kts`
- Modify: `.mise.toml`

- [x] **Step 1: Add JSON rendering helpers**

  Add small Gradle helpers for JSON string escaping and manifest rendering.

- [x] **Step 2: Project catalog entries**

  Make `writeDriverModManifest` parse `fabric-driver-lanes.json`, require each
  entry's `loader`, `minecraftVersion`, `loaderVersion`, and
  `distributionPath`, and render public entries with:

  ```json
  {
    "loader": "FABRIC",
    "minecraftVersion": "1.21.6",
    "loaderVersion": "0.19.3",
    "path": "mods/craftless-driver-fabric.jar"
  }
  ```

- [x] **Step 3: Keep artifact staging catalog-driven**

  Leave `stageFabricDriverLaneArtifacts` consuming private `artifactKey` and
  `distributionPath`.

### Task 4: Evidence, Verification, Commit

**Files:**
- Create: `docs/superpowers/evidence/2026-06-28-projected-driver-mod-manifest.md`

- [x] **Step 1: Run focused tests and tasks**

  ```sh
  mise exec -- bun test playwright/src/distribution.test.ts
  mise exec -- gradle :cli:writeDriverModManifest
  ```

- [x] **Step 2: Inspect generated manifest**

  ```sh
  grep -q '"path": "mods/craftless-driver-fabric.jar"' cli/build/generated/driver-mods/driver-mods.json
  ! grep -q '"artifactKey"' cli/build/generated/driver-mods/driver-mods.json
  ! grep -q '"distributionPath"' cli/build/generated/driver-mods/driver-mods.json
  ```

- [x] **Step 3: Run local gates**

  ```sh
  mise run package-cli
  git diff --check
  mise run ci
  ```

- [x] **Step 4: Commit and push**

  ```sh
  git add .mise.toml AGENTS.md cli/build.gradle.kts playwright/src/distribution.test.ts docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-130-projected-driver-mod-manifest-design.md docs/superpowers/plans/2026-06-28-130-projected-driver-mod-manifest-plan.md docs/superpowers/evidence/2026-06-28-projected-driver-mod-manifest.md
  git commit -m "build: project driver mod manifest from lane catalog"
  git push origin main
  ```

## Self-Review

- Spec coverage: public projection, private field rejection, existing package
  shape, focused verification, and full local gates are covered.
- Placeholder scan: no TODO/TBD placeholders.
- Scope: no gameplay action, public route, compiled lane, Fabric dependency
  change, or support claim.
