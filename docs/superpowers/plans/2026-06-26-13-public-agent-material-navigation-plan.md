# Public Agent Material Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the public-agent runner compose generated block query and navigation actions to move toward material blocks.

**Architecture:** Keep this phase in `testkit`: the runner behaves like an external user by invoking only generated actions through `POST /clients/{id}:run`. The runner parses public action responses, derives a navigation goal from `world.block.query`, and records blockers when the generated API does not provide enough evidence.

**Tech Stack:** Kotlin/JVM, Ktor Client, kotlinx.serialization JSON, Gradle through mise.

---

### Task 1: Public Agent Navigation Composition Tests

**Files:**
- Modify: `testkit/src/test/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunnerTest.kt`

- [ ] **Step 1: Add a failing test for log query to navigation follow**

Add a test that uses a mock Craftless server with the complete generic action
catalog. The mock server returns one log block from `world.block.query`, returns
`plan-id = navigation.plan.public-agent.0001` from `navigation.plan`, and
records request bodies. Assert the public-agent action log is:

```kotlin
listOf(
    "inventory.query",
    "world.block.query",
    "navigation.plan",
    "navigation.follow",
    "entity.query",
)
```

Also assert request bodies contain `"category":"log"`, `"kind":"block"`,
the returned block position, and no `task.survival`.

- [ ] **Step 2: Run the test and verify RED**

Run:

```sh
mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest*'
```

Expected: FAIL because the runner does not yet invoke navigation.

### Task 2: Public Agent Policy Implementation

**Files:**
- Modify: `testkit/src/main/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunner.kt`

- [ ] **Step 1: Parse action responses**

Add helpers that decode action responses into `JsonObject`, extract
`data.blocks[0].position`, and extract `data["plan-id"]`.

- [ ] **Step 2: Build generated action invocations**

Replace the fixed probe invocation list with sequential logic:

1. invoke `inventory.query`;
2. invoke `world.block.query` with `radius = 32.0`, `limit = 16`,
   `category = "log"`;
3. return `BLOCKED` with
   `insufficient-public-evidence:world.block.query.log` if no block position
   is present;
4. invoke `navigation.plan` with `goal = { "kind": "block", "position": ...,
   "radius": 2.0 }`;
5. return `BLOCKED` with
   `insufficient-public-evidence:navigation.plan` if no plan id is present;
6. invoke `navigation.follow` with `plan = { "id": planId }`;
7. invoke `entity.query`.

- [ ] **Step 3: Keep artifact behavior**

Ensure `public-agent-gameplay-results.jsonl` records all accepted actions when
state is `RAN`, and records exactly one blocker line when state is `BLOCKED`.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run:

```sh
mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest*'
```

Expected: PASS.

### Task 3: Docs and Live Evidence

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`

- [ ] **Step 1: Add Phase 13 to the active phase list**

Document that Phase 13 composes public-agent block discovery with navigation
through generated actions.

- [ ] **Step 2: Update the checklist**

Record the implemented public-agent material-navigation evidence and leave the
full final survival gate open.

- [ ] **Step 3: Run live no-hold gameplay**

Run:

```sh
CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 mise exec -- gradle :driver-fabric:fabricFinalGameplay
```

Expected: Gradle task succeeds. Public-agent artifacts either show
`navigation.plan` and `navigation.follow`, or report an explicit
`insufficient-public-evidence:*` blocker.

### Task 4: Final Verification and Push

- [ ] **Step 1: Run architecture and CI**

Run:

```sh
mise run architecture-check
mise run ci
```

Expected: both pass.

- [ ] **Step 2: Commit and push**

Run:

```sh
git add AGENTS.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-26-13-public-agent-material-navigation-design.md docs/superpowers/plans/2026-06-26-13-public-agent-material-navigation-plan.md testkit/src/main/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunner.kt testkit/src/test/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunnerTest.kt
git commit -m "feat: compose public material navigation"
git push origin main
```
