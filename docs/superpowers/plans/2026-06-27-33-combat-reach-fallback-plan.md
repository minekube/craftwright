# Combat Reach Fallback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent the process-external public agent from blocking when
generated combat navigation reports success but public state still places the
target just outside attack reach and a generic `player.move` nudge is
available.

**Architecture:** Keep the correction in `testkit` public-agent policy. The
runner composes generated `entity.query`, `navigation.plan`,
`navigation.follow`, `player.query`, `player.look`, `player.move`, and
`entity.attack`; it does not add product action ids or scenario shortcuts.

**Tech Stack:** Kotlin/JVM, Ktor Client MockEngine tests,
kotlinx.serialization JSON, Gradle through mise.

---

### Task 1: RED Test For Post-Navigation Combat Reach

**Files:**
- Modify:
  `testkit/src/test/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunnerTest.kt`

- [x] **Step 1: Add failing regression test**

Add `runner uses generated player move when combat navigation succeeds but
target remains out of reach`. Configure the fake generated API so
`navigation.follow` reports success, public `entity.query` still reports the
preferred target outside attack reach, and later public perception after
generated `player.move` proves the same handle reachable. Assert the runner
invokes `player.move` before `entity.attack`, reaches `RAN`, and never emits a
scenario shortcut.

- [x] **Step 2: Run the RED test**

Run:

```sh
mise exec -- gradle :testkit:test --tests 'com.minekube.craftless.testkit.PublicAgentGameplayRunnerTest.runner uses generated player move when combat navigation succeeds but target remains out of reach'
```

Expected before implementation: FAIL because the runner re-navigates and
blocks with `insufficient-public-evidence:entity.query.attack-target.reachable`
without using the discovered generic movement fallback.

### Task 2: Generic Combat Nudge

**Files:**
- Modify:
  `testkit/src/main/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunner.kt`
- Modify:
  `testkit/src/test/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunnerTest.kt`

- [x] **Step 1: Track whether the existing close attempt already used
  `player.move`**

Return a small internal result from the combat close-distance helper so the
runner knows whether generated navigation failed and already used the generic
movement fallback.

- [x] **Step 2: Nudge only when public state still contradicts reachability**

After generated navigation and follow-up public re-query, if the target remains
outside attack reach and `player.move` is discovered, invoke bounded
`player.move`, re-query `player.query` and `entity.query`, and only proceed if
reach is proven.

- [x] **Step 3: Preserve optional primitive semantics**

If `player.move` is not discovered after a successful navigation response,
preserve the reach-evidence blocker instead of reporting
`missing-generic-primitive:player.move`.

### Task 3: Verification And Final Gameplay

**Files:**
- Modify: `docs/project-completion-checklist.md`
- Modify: `AGENTS.md`

- [x] **Step 1: Run focused and module tests**

Run:

```sh
mise exec -- gradle :testkit:test --tests 'com.minekube.craftless.testkit.PublicAgentGameplayRunnerTest.runner uses generated player move when combat navigation succeeds but target remains out of reach'
mise exec -- gradle :testkit:test
```

Expected after implementation: PASS.

- [ ] **Step 2: Run broader gates**

Run:

```sh
mise run lint
mise run jvm-test
```

Expected: all pass before pushing.

- [ ] **Step 3: Re-run final gameplay**

Run:

```sh
CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS=90000 CRAFTLESS_FABRIC_SMOKE_ACTION_TIMEOUT_MS=720000 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=1800000 CRAFTLESS_FABRIC_SMOKE_READY_REMINDER_MS=120000 mise exec -- gradle :driver-fabric:fabricFinalGameplay
```

Expected: final gameplay either reaches `publicAgentState=RAN` and waits for
Robin's Minecraft chat confirmation, or records the next precise generic
public-evidence blocker.
