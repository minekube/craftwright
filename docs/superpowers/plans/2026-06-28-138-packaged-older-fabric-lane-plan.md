# Packaged Older Fabric Lane Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Package the representative older Fabric lane as a real selectable driver-mod artifact in the CLI distribution.

**Architecture:** Keep `driver-fabric` as one parameterized module. Use mise packaging orchestration to build the older lane first, stage its remapped jar and private lane catalog, then run the normal current-lane CLI distribution build with an extra-lane root that the CLI Gradle packaging tasks merge into `driver-mods.json` and staged artifacts.

**Tech Stack:** Gradle Kotlin DSL, Fabric Loom, mise, Bun distribution tests.

---

### Task 1: Governance And Red Test

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/specs/2026-06-28-138-packaged-older-fabric-lane-design.md`
- Create: `docs/superpowers/plans/2026-06-28-138-packaged-older-fabric-lane-plan.md`
- Modify: `playwright/src/distribution.test.ts`

- [x] **Step 1: Add Phase 138 governance**

  Record that packaging an older lane is artifact plumbing only until runtime
  launch/attach/API evidence exists.

- [x] **Step 2: Add failing distribution policy test**

  Add expectations that `driver-fabric/build.gradle.kts`,
  `cli/build.gradle.kts`, and `.mise.toml` support a staged older lane with
  `mods/fabric-1.20.6/craftless-driver-fabric.jar`.

- [x] **Step 3: Run the test red**

  ```sh
  mise exec -- bun test playwright/src/distribution.test.ts --test-name-pattern "CLI distribution packages representative older fabric lane"
  ```

### Task 2: Build Script Support

**Files:**
- Modify: `driver-fabric/build.gradle.kts`
- Modify: `cli/build.gradle.kts`

- [x] **Step 1: Parameterize lane distribution path**

  Add `craftless.fabric.distributionPath` to the generated private lane
  catalog. Default remains `mods/craftless-driver-fabric.jar`.

- [x] **Step 2: Merge extra lane catalogs in CLI packaging**

  Add `craftless.extraFabricDriverLaneRoot` support. When present, read every
  `fabric-driver-lanes.json` under that root, append those entries to the
  current catalog entries, render all entries into public `driver-mods.json`,
  and stage extra artifacts by resolving their `distributionPath` below the
  extra root.

### Task 3: Mise Packaging Orchestration

**Files:**
- Modify: `.mise.toml`

- [x] **Step 1: Stage the older lane before current packaging**

  Update `package-cli` to build the older lane with:

  ```sh
  -Pcraftless.fabric.minecraftVersion=1.20.6
  -Pcraftless.fabric.yarnMappings=1.20.6+build.3
  -Pcraftless.fabric.loaderVersion=0.19.3
  -Pcraftless.fabric.apiVersion=0.100.8+1.20.6
  -Pcraftless.fabric.javaMajorVersion=21
  -Pcraftless.fabric.laneId=fabric-1-20-6-lane
  -Pcraftless.fabric.providerId=fabric-1-20-6-lane
  -Pcraftless.fabric.artifactKey=fabric-1-20-6-remap-jar
  -Pcraftless.fabric.mappingsFingerprint=craftless-fabric-bindings-1-20-6
  -Pcraftless.fabric.distributionPath=mods/fabric-1.20.6/craftless-driver-fabric.jar
  ```

- [x] **Step 2: Copy older artifact and catalog**

  Copy the older remapped jar and generated lane catalog under
  `build/driver-lanes/older`.

- [x] **Step 3: Build current package with extra-lane root**

  Run the normal current-lane CLI distribution build with
  `-Pcraftless.extraFabricDriverLaneRoot=build/driver-lanes/older`.

### Task 4: Verification

- [x] **Step 1: Run focused tests**

  ```sh
  mise exec -- bun test playwright/src/distribution.test.ts --test-name-pattern "CLI distribution packages representative older fabric lane"
  mise run package-cli
  ```

- [x] **Step 2: Run local gates**

  ```sh
  git diff --check
  mise run ci
  ```

### Task 5: Evidence And Push

**Files:**
- Create: `docs/superpowers/evidence/2026-06-28-packaged-older-fabric-lane.md`

- [x] **Step 1: Record evidence**

  Include red test evidence, green test evidence, package artifact checks, and
  the explicit caveat that runtime support still needs launch/attach/API smoke.

- [x] **Step 2: Commit and push**

  ```sh
  git add .
  git commit -m "build: package older fabric lane"
  git push origin main
  ```

## Self-Review

- Spec coverage: governance, red test, distribution path, extra catalog merge,
  mise packaging orchestration, package smoke, and no support claim are covered.
- Placeholder scan: no TODO/TBD placeholders.
- Scope: no public gameplay API, static gameplay catalog, scenario shortcut,
  or older runtime support claim.
