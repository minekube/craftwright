# Reflective Fabric World-Change Callback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the compile-time `ClientWorldEvents` dependency while keeping
world-change callback registration when the runtime Fabric API exposes it.

**Architecture:** Keep direct typed Fabric registrations for current stable
events. Use reflection and a dynamic proxy only for the optional world-change
event that blocks representative older-lane compilation.

**Tech Stack:** Kotlin/JVM, Fabric API events, Java dynamic proxies, Gradle/mise
verification.

---

### Task 1: Governance

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/specs/2026-06-28-135-reflective-fabric-world-change-callback-design.md`
- Create: `docs/superpowers/plans/2026-06-28-135-reflective-fabric-world-change-callback-plan.md`

- [x] **Step 1: Add Phase 135 to AGENTS.md**

  Define it as optional callback compatibility plumbing only.

- [x] **Step 2: Add Phase 135 to checklist**

  Track it as support-enabling work that does not satisfy older support by
  itself.

### Task 2: Add Red Test

**Files:**
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [x] **Step 1: Add source-level dependency test**

  Add a test named
  `fabric event callbacks do not compile against optional world change event`
  that reads `FabricEventCallbacks.kt` and asserts it does not contain
  `ClientWorldEvents`.

- [x] **Step 2: Run the test red**

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric event callbacks do not compile against optional world change event*'
  ```

  Expected before implementation: fail because the source imports and invokes
  `ClientWorldEvents`.

### Task 3: Implement Reflective Registration

**Files:**
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricEventCallbacks.kt`

- [x] **Step 1: Remove the typed import and direct registration**

  Delete the `ClientWorldEvents` import and replace the direct registration with
  `registerClientWorldChangeCallbackReflectively()`.

- [x] **Step 2: Add reflection helper**

  Add a private helper that loads the event classes, creates a proxy listener,
  invokes `register`, records the callback, and returns `false` when any
  reflective lookup fails.

### Task 4: Update Probe And Evidence

**Files:**
- Modify: `.mise.toml`
- Create: `docs/superpowers/evidence/2026-06-28-reflective-fabric-world-change-callback.md`

- [x] **Step 1: Remove `ClientWorldEvents` from expected older-lane blockers**

  Keep `PlayerInput` and `RecipeDisplayEntry` as expected blockers.

- [x] **Step 2: Run verification**

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric event callbacks do not compile against optional world change event*'
  mise run fabric-lane-check-older
  mise run package-cli
  git diff --check
  mise run ci
  ```

### Task 5: Commit And Push

- [x] **Step 1: Commit and push**

  ```sh
  git add .mise.toml AGENTS.md docs/project-completion-checklist.md driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricEventCallbacks.kt driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt docs/superpowers/specs/2026-06-28-135-reflective-fabric-world-change-callback-design.md docs/superpowers/plans/2026-06-28-135-reflective-fabric-world-change-callback-plan.md docs/superpowers/evidence/2026-06-28-reflective-fabric-world-change-callback.md
  git commit -m "build: reflect optional fabric world callback"
  git push origin main
  ```

## Self-Review

- Spec coverage: optional callback registration, red test, older-lane probe,
  and no support claim are covered.
- Placeholder scan: no TODO/TBD placeholders.
- Scope: no public gameplay API, route family, CLI gameplay catalog, Fabric
  descriptor/binding pair, scenario shortcut, or support claim.
