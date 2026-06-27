# Material Reach Evidence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent the process-external public agent from sending generated
`world.block.break` when public `player.query` state proves the target block is
still out of reach after generated navigation.

**Architecture:** Keep the correction in `testkit` public-agent policy. The
runner composes generated `world.block.query`, `navigation.plan`,
`navigation.follow`, `player.query`, `player.look`, `player.raycast`,
`world.block.break`, `entity.query`, and `inventory.query`; it does not add
product action ids or scenario shortcuts.

**Tech Stack:** Kotlin/JVM, Ktor Client MockEngine tests, kotlinx.serialization
JSON, Gradle through mise.

---

### Task 1: RED Test For Navigation Reach Evidence

**Files:**
- Modify: `testkit/src/test/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunnerTest.kt`

- [x] **Step 1: Add failing regression test**

Add `runner verifies public position after generated navigation reports
success`. Configure the fake generated API so `navigation.follow` reports
success but `player.query` returns a position far from the generated material
target. Assert the runner blocks with
`insufficient-public-evidence:navigation.follow.succeeded`, records
`player.query`, does not call `world.block.break`, and never emits a scenario
shortcut.

- [x] **Step 2: Run the RED test**

Run:

```sh
mise exec -- gradle :testkit:test --tests 'com.minekube.craftless.testkit.PublicAgentGameplayRunnerTest.runner verifies public position after generated navigation reports success'
```

Expected before implementation: FAIL because the runner sends
`world.block.break` after trusting the generated navigation response.

### Task 2: Material Reach Verification

**Files:**
- Modify: `testkit/src/main/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunner.kt`
- Modify: `testkit/src/test/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunnerTest.kt`

- [x] **Step 1: Check public player position before break**

After selecting the generated block target and querying `player.query`, compare
the public player position to the centered target block position. If the
distance exceeds the generated break reach budget, return
`insufficient-public-evidence:navigation.follow.succeeded`.

- [x] **Step 2: Preserve existing generic scenarios**

Adjust only test fixtures whose mocked player and block positions were
incoherent for the new public-state proof. Do not add material-specific or
combat-specific product actions.

- [x] **Step 3: Run focused tests**

Run:

```sh
mise exec -- gradle :testkit:test --tests 'com.minekube.craftless.testkit.PublicAgentGameplayRunnerTest.runner verifies public position after generated navigation reports success'
mise exec -- gradle :testkit:test --tests 'com.minekube.craftless.testkit.PublicAgentGameplayRunnerTest'
```

Expected after implementation: PASS.

### Task 3: Verification And Final Gameplay

**Files:**
- Modify: `docs/project-completion-checklist.md`

- [x] **Step 1: Run gates**

Run:

```sh
mise exec -- gradle :testkit:test
mise run lint
mise run jvm-test
```

Expected: all pass.

- [x] **Step 2: Re-run final gameplay**

Run:

```sh
CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS=90000 CRAFTLESS_FABRIC_SMOKE_ACTION_TIMEOUT_MS=720000 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=1800000 CRAFTLESS_FABRIC_SMOKE_READY_REMINDER_MS=120000 mise exec -- gradle :driver-fabric:fabricFinalGameplay
```

Observed: the public agent reached `publicAgentState=RAN`, crafted and equipped
a `Wooden Sword`, attacked a generated public entity target, picked up
`Raw Porkchop`, and wrote `final-gameplay-ready.json` for
`127.0.0.1:52826`. Robin confirmation remains pending until the exact
Minecraft chat phrase is observed and `final-gameplay-confirmation.json` is
written.
