# Generic Recipe And Crafting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add generic runtime-discovered recipe query and craft primitives so public agents can craft through generated OpenAPI without survival shortcuts.

**Architecture:** Extend the Fabric runtime capability graph with resource `recipe`, handle `recipe.handle`, and operations `recipe.query` and `recipe.craft`. Back invocation with Fabric client-thread adapters that expose Craftless-owned recipe handles and inventory evidence, then update the public-agent runner to compose those generated actions when available.

**Tech Stack:** Kotlin/JVM, Gradle Kotlin DSL, kotlinx.serialization JSON, Fabric client-thread gateway, Ktor-backed testkit HTTP fakes, mise.

**Current slice status:** The graph/action names are generic, `recipe.query`
projects live Fabric recipe-book display entries into Craftless-owned records,
and `recipe.craft` can invoke a discovered live recipe handle through the
client recipe-click path when the runtime graph reports a craft context. Public
agent policy composition is covered by fake-server evidence. Final live
survival evidence, broader screen/handler coverage, and post-server inventory
confirmation still remain open.

---

### Task 1: Graph Discovery

**Files:**
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricCapabilityProbe.kt`
- Test: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [x] Write a failing test named `fabric runtime discovery exposes recipe operations only from live client state`.
- [x] Run `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric runtime discovery exposes recipe operations only from live client state*'` and confirm it fails because `recipe.query` is missing.
- [x] Add resource `recipe`, handle `recipe.handle`, and operations `recipe.query` and `recipe.craft` from `FabricClientStateCapabilityProbe` using runtime availability derived from player, inventory, world, and screen/handler state.
- [x] Keep operation adapters as `fabric.recipe-query` and `fabric.recipe-craft`.
- [x] Run the focused test and confirm it passes.

### Task 2: Generic Recipe Query Adapter

**Files:**
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt`
- Test: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [x] Write a failing test named `fabric backend queries generic recipe handles through runtime graph adapter`.
- [x] Run the focused test and confirm it fails because `fabric.recipe-query` has no adapter.
- [x] Add `fabric.recipe-query` to `navigationTaskOperationAdapters`.
- [x] Implement `queryRecipes(invocation)` on the client thread. It returns Craftless-owned handles, category, craftable state, required public ingredients, and produced public outputs.
- [x] Ensure public recipe records do not expose raw registry ids or Minecraft/Fabric/Yarn names.
- [x] Run the focused test and confirm it passes.

### Task 3: Generic Recipe Craft Adapter

**Files:**
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt`
- Test: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [x] Write a failing test named `fabric backend crafts a discovered recipe handle through runtime graph adapter`.
- [x] Run the focused test and confirm it fails because `fabric.recipe-craft` has no adapter.
- [x] Add `fabric.recipe-craft` to `navigationTaskOperationAdapters`.
- [x] Implement `craftRecipe(invocation)` by validating a public recipe handle from `recipe.query`, count bounds, live craftability, and inventory fingerprint evidence.
- [x] Return machine-readable failure results for missing target, stale handle, unavailable crafting context, and unchanged inventory.
- [x] Report `crafted-count` from the observed output slot stack count when
  taking crafting output, so multi-output and craft-many requests do not fall
  back to a single-item assumption.
- [x] Run the focused test and confirm it passes.

### Task 4: Public-Agent Composition

**Files:**
- Modify: `testkit/src/main/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunner.kt`
- Modify: `testkit/src/test/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunnerTest.kt`

- [x] Write a failing public-agent test named `runner crafts useful inventory output through generated recipe actions when available`.
- [x] Run `mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest.runner crafts useful inventory output through generated recipe actions when available*'` and confirm it fails because the runner does not use recipe actions.
- [x] Query `recipe.query` only if the generated action catalog advertises it.
- [x] Select a useful craftable public recipe by category/output evidence, invoke `recipe.craft` by handle, and verify with `inventory.query`.
- [x] Keep policy generic; do not add material-specific or weapon-specific product actions.
- [x] Run the focused test and the full `PublicAgentGameplayRunnerTest` class.

### Task 5: Checklist, Live Evidence, And Push

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Modify: `docs/superpowers/specs/2026-06-26-28-generic-recipe-crafting-design.md`
- Modify: `docs/superpowers/plans/2026-06-26-28-generic-recipe-crafting-plan.md`

- [ ] Add Phase 28 to the active checklist with honest implemented and remaining evidence.
- [ ] Run `git diff --check`.
- [ ] Run focused driver and testkit tests from Tasks 1-4.
- [ ] Run `mise run lint`.
- [ ] Run `mise run ci`.
- [ ] If practical, run `CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS=90000 CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS=120000 mise exec -- gradle :driver-fabric:fabricFinalGameplay`.
- [ ] Commit and push directly to `main`.
