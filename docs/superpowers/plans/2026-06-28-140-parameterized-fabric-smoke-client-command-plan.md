# Parameterized Fabric Smoke Client Command Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure Fabric smoke launches the same compiled lane that the smoke task was parameterized to verify.

**Architecture:** Add a small Gradle helper that renders the active `craftless.fabric.*` lane properties as `-P` arguments, and splice those arguments into the default `CRAFTLESS_SMOKE_ACTION_COMMAND_JSON` for `:driver-fabric:runClient`. Keep explicit environment overrides unchanged.

**Tech Stack:** Gradle Kotlin DSL, Kotlin source-policy tests, mise.

---

### Task 1: Governance And Red Test

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/specs/2026-06-28-140-parameterized-fabric-smoke-client-command-design.md`
- Create: `docs/superpowers/plans/2026-06-28-140-parameterized-fabric-smoke-client-command-plan.md`
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [x] **Step 1: Record Phase 140 governance**

  Add Phase 140 to the active sequence and checklist. State that older-lane
  smoke evidence is invalid if the inner client command silently launches the
  default current lane.

- [x] **Step 2: Add failing source guard**

  Add a test named
  `fabric client smoke runClient command preserves parameterized lane properties`
  that reads `driver-fabric/build.gradle.kts` and asserts it contains:

  ```kotlin
  "fabricSmokeLaneGradleProperties"
  "\"-Pcraftless.fabric.minecraftVersion=$fabricCompiledMinecraftVersion\""
  "\"-Pcraftless.fabric.yarnMappings=$fabricCompiledYarnMappings\""
  "\"-Pcraftless.fabric.loaderVersion=$fabricCompiledLoaderVersion\""
  "\"-Pcraftless.fabric.apiVersion=$fabricCompiledApiVersion\""
  "\"-Pcraftless.fabric.javaMajorVersion=$fabricCompiledJavaMajorVersion\""
  "\"-Pcraftless.fabric.laneId=$fabricCompiledLaneId\""
  "\"-Pcraftless.fabric.providerId=$fabricCompiledProviderId\""
  "\"-Pcraftless.fabric.artifactKey=$fabricCompiledArtifactKey\""
  "\"-Pcraftless.fabric.mappingsFingerprint=$fabricCompiledMappingsFingerprint\""
  ```

- [x] **Step 3: Run the test red**

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric client smoke runClient command preserves parameterized lane properties'
  ```

  Expected: fail because the build script does not yet render those properties
  into the default action command.

### Task 2: Gradle Command Propagation

**Files:**
- Modify: `driver-fabric/build.gradle.kts`

- [x] **Step 1: Add lane property helper**

  Add:

  ```kotlin
  fun fabricSmokeLaneGradleProperties(): List<String> =
      listOf(
          "-Pcraftless.fabric.minecraftVersion=$fabricCompiledMinecraftVersion",
          "-Pcraftless.fabric.yarnMappings=$fabricCompiledYarnMappings",
          "-Pcraftless.fabric.loaderVersion=$fabricCompiledLoaderVersion",
          "-Pcraftless.fabric.apiVersion=$fabricCompiledApiVersion",
          "-Pcraftless.fabric.javaMajorVersion=$fabricCompiledJavaMajorVersion",
          "-Pcraftless.fabric.laneId=$fabricCompiledLaneId",
          "-Pcraftless.fabric.providerId=$fabricCompiledProviderId",
          "-Pcraftless.fabric.artifactKey=$fabricCompiledArtifactKey",
          "-Pcraftless.fabric.mappingsFingerprint=$fabricCompiledMappingsFingerprint",
      )
  ```

- [x] **Step 2: Use helper in default action command**

  Build the default JSON array as:

  ```kotlin
  listOf(
      "mise", "-C", rootProjectPath, "exec", "--", "gradle", "-p", rootProjectPath,
  ) + fabricSmokeLaneGradleProperties() + listOf(":driver-fabric:runClient")
  ```

  Encode it with the existing JSON string helper so spaces and paths remain
  safe.

### Task 3: Verification And Evidence

**Files:**
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/evidence/2026-06-28-parameterized-fabric-smoke-client-command.md`

- [x] **Step 1: Run focused tests**

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric client smoke runClient command preserves parameterized lane properties' --tests '*FabricDriverModuleTest.fabric client smoke passes runtime lane evidence before launching client'
  ```

- [x] **Step 2: Run local hygiene**

  ```sh
  git diff --check
  ```

- [x] **Step 3: Record evidence**

  Include red and green command output plus the caveat that this prepares the
  harness for real older-lane smoke but does not itself prove older runtime
  launch/attach.

- [ ] **Step 4: Commit and push**

  ```sh
  git add AGENTS.md driver-fabric/build.gradle.kts driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-140-parameterized-fabric-smoke-client-command-design.md docs/superpowers/plans/2026-06-28-140-parameterized-fabric-smoke-client-command-plan.md docs/superpowers/evidence/2026-06-28-parameterized-fabric-smoke-client-command.md
  git commit -m "build: preserve fabric smoke lane properties"
  git push origin main
  ```

## Self-Review

- Spec coverage: default action command propagation, source guard, focused
  verification, and no support claim are covered.
- Placeholder scan: no TODO/TBD placeholders.
- Scope: no public gameplay API, static gameplay catalog, route family, or
  scenario shortcut.
