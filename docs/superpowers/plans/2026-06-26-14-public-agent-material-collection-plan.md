# Public Agent Material Collection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the public-agent runner attempt generic material collection after navigating to a discovered log and verify inventory evidence.

**Architecture:** Keep the work in `testkit` as an external public API consumer. The runner composes generated actions and records explicit blockers when the public API does not provide enough state evidence.

**Tech Stack:** Kotlin/JVM, Ktor Client, kotlinx.serialization JSON, Gradle through mise.

---

### Task 1: Public Agent Mining Tests

**Files:**
- Modify: `testkit/src/test/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunnerTest.kt`

- [ ] **Step 1: Add RED test for look/raycast/break/inventory sequence**

Extend the complete-catalog mock server so it can return:

- first `inventory.query`: empty inventory;
- `player.query`: position near the target block;
- `player.raycast`: a block hit;
- `world.block.break`: accepted break evidence;
- second `inventory.query`: one slot with `item-name = "Oak Log"`.

Assert the action log is:

```kotlin
listOf(
    "inventory.query",
    "world.block.query",
    "navigation.plan",
    "navigation.follow",
    "player.query",
    "player.look",
    "player.raycast",
    "world.block.break",
    "inventory.query",
    "entity.query",
)
```

Assert request bodies contain `"yaw"`, `"pitch"`, `player.raycast`,
`world.block.break`, and no `task.survival`.

- [ ] **Step 2: Add RED test for missing inventory proof**

Configure the mock final inventory to remain empty. Assert the runner returns
`BLOCKED` with `insufficient-public-evidence:inventory.query.log`, records the
break action, and does not invoke `entity.query` after the failed inventory
verification.

- [ ] **Step 3: Run focused tests**

Run:

```sh
mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest*'
```

Expected: FAIL before implementation because the runner does not yet invoke
look/raycast/break/final inventory verification.

### Task 2: Public Agent Material Collection Policy

**Files:**
- Modify: `testkit/src/main/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunner.kt`

- [ ] **Step 1: Require `player.query`**

Add `player.query` to the runner's required generated action list.

- [ ] **Step 2: Add JSON helpers**

Add helpers to parse:

- `data.position` from `player.query`;
- log presence from `data.slots[*].item-name`;
- numeric x/y/z values from JSON positions.

- [ ] **Step 3: Add look calculation**

Add a small internal point type and compute yaw/pitch from the public player
position to the public block position. Use block center offsets for targeting.

- [ ] **Step 4: Invoke generated actions**

After `navigation.follow`, invoke:

1. `player.query`;
2. `player.look`;
3. `player.raycast`;
4. `world.block.break`;
5. final `inventory.query`.

If required public evidence is missing, return an
`insufficient-public-evidence:*` blocker with action evidence preserved.

- [ ] **Step 5: Verify focused tests pass**

Run:

```sh
mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest*'
```

Expected: PASS.

### Task 3: Docs, Live Evidence, Verification, Push

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`

- [ ] **Step 1: Add Phase 14 to active phase list**

Document that Phase 14 composes public navigation with look/raycast/break and
inventory verification.

- [ ] **Step 2: Run live no-hold gameplay**

Run:

```sh
CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 mise exec -- gradle :driver-fabric:fabricFinalGameplay
```

Expected: Gradle succeeds. Public-agent artifacts either show final inventory
log evidence or report `insufficient-public-evidence:inventory.query.log`.

- [ ] **Step 3: Run gates**

Run:

```sh
git diff --check
mise run architecture-check
mise run ci
```

Expected: all pass.

- [ ] **Step 4: Commit and push**

Run:

```sh
git add AGENTS.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-26-14-public-agent-material-collection-design.md docs/superpowers/plans/2026-06-26-14-public-agent-material-collection-plan.md testkit/src/main/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunner.kt testkit/src/test/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunnerTest.kt
git commit -m "feat: verify public material collection"
git push origin main
```
