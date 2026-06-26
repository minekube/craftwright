# Bounded Material Exploration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce public-agent exploration navigation failures by using shorter overlapping waypoints when local material query returns empty.

**Architecture:** Keep exploration in external `testkit` public-agent policy. The runner composes generated `player.query`, `navigation.plan`, `navigation.follow`, and `world.block.query`; it does not change product action ids.

**Tech Stack:** Kotlin/JVM, Ktor Client MockEngine tests, kotlinx.serialization JSON, Gradle through mise.

---

### Task 1: RED Test For Shorter Exploration Waypoints

**Files:**
- Modify: `testkit/src/test/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunnerTest.kt`

- [ ] **Step 1: Add assertion to exploration test**

In `runner explores with generic navigation when local material query is empty`,
assert that the first generated navigation waypoint uses `x = 35.0` for the
test player's `x = 11.0`, proving a 24-block step:

```kotlin
assertTrue(server.requestBodies.any { it.contains(""""x":35.0""") })
assertFalse(server.requestBodies.any { it.contains(""""x":59.0""") })
```

- [ ] **Step 2: Run RED test**

Run:

```sh
mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest*'
```

Expected: FAIL because the current first waypoint is 48 blocks away.

### Task 2: Public-Agent Exploration Policy

**Files:**
- Modify: `testkit/src/main/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunner.kt`

- [ ] **Step 1: Change waypoint spacing**

Update `CraftlessPoint.explorationWaypoints()` to use a 24-block step and an
overlapping sequence:

```kotlin
private const val MATERIAL_EXPLORATION_STEP = 24.0
```

Return east, north, west, south, then diagonals from the original origin.

- [ ] **Step 2: Run focused tests**

Run:

```sh
mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest*'
```

Expected: PASS.

### Task 3: Live Evidence, Gates, Push

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`

- [ ] **Step 1: Add Phase 22 to docs**

Document the bounded exploration policy and current live blocker.

- [ ] **Step 2: Re-run live no-hold gameplay**

Run:

```sh
CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS=90000 CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS=120000 mise exec -- gradle :driver-fabric:fabricFinalGameplay
```

Expected: public-agent artifacts either progress to material target/break/drop
perception/equip or report a precise generated-action/evidence blocker.

- [ ] **Step 3: Run gates**

Run:

```sh
git diff --check
mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest*'
mise run lint
mise run architecture-check
mise run ci
```

Expected: all pass.
