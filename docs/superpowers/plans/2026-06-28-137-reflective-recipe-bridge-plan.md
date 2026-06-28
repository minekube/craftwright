# Reflective Recipe Bridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans
> to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for
> tracking.

**Goal:** Remove direct compile-time dependencies on version-specific recipe
display/click APIs and make the representative older Fabric source lane compile.

**Architecture:** Keep the public action shape unchanged. Move recipe discovery,
projection, handle matching, click invocation, and output-slot lookup behind
reflection over the running client's recipe book and screen handler.

**Tech Stack:** Kotlin/JVM, Fabric/Minecraft client recipe book, reflection,
Gradle/mise.

---

### Task 1: Governance

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/specs/2026-06-28-137-reflective-recipe-bridge-design.md`
- Create: `docs/superpowers/plans/2026-06-28-137-reflective-recipe-bridge-plan.md`

- [x] **Step 1: Add Phase 137 governance**

  Define the bridge as compatibility plumbing only.

### Task 2: Add Red Guard

**Files:**
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [x] **Step 1: Add source-level guard**

  Assert backend/projection sources and mixin config do not contain
  current-only recipe display/click API names.

- [x] **Step 2: Run the guard red**

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric recipe bridge does not compile against version-specific recipe display types*'
  ```

### Task 3: Implement Reflective Bridge

**Files:**
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt`
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricRecipeProjection.kt`
- Modify: `driver-fabric/src/main/resources/craftless-driver-fabric.mixins.json`
- Delete: `driver-fabric/src/main/java/com/minekube/craftless/driver/fabric/v1_21_6/mixin/ClientRecipeBookAccessor.java`

- [x] **Step 1: Replace typed recipe projection**

  Project recipe entries and slot displays through reflection and Craftless JSON
  schemas.

- [x] **Step 2: Replace typed backend recipe calls**

  Resolve recipe handles, craftability, recipe clicks, recipe-displayed calls,
  and crafting output slots reflectively.

- [x] **Step 3: Remove typed accessor mixin**

  Delete the accessor and remove it from the mixin config.

### Task 4: Verification

- [x] **Step 1: Run local gates**

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric recipe bridge does not compile against version-specific recipe display types*'
  mise run fabric-lane-check-older
  mise run package-cli
  git diff --check
  mise run ci
  ```

### Task 5: Commit And Push

- [x] **Step 1: Commit and push**

  ```sh
  git add .
  git commit -m "build: reflect recipe bridge"
  git push origin main
  ```

## Self-Review

- Spec coverage: source guard, reflection bridge, accessor removal, older-lane
  compile, and no runtime support claim are covered.
- Placeholder scan: no TODO/TBD placeholders.
- Scope: no new public gameplay action, route family, static catalog, or
  support claim.
