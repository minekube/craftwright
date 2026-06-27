# Public Agent Pickup Convergence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make material pickup evidence converge through generated public movement when navigation cannot start near a visible dropped item, including elevated drops that require jumping.

**Architecture:** Keep this in the external public-agent runner. Compose existing generated `entity.query`, `player.query`, `player.look`, `player.move`, and `inventory.query`; do not add product actions or route families.

**Tech Stack:** Kotlin/JVM, kotlin.test, Ktor test HTTP server, Gradle, mise.

---

### Task 1: Add Elevated Pickup Convergence Test

**Files:**
- Modify: `testkit/src/test/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunnerTest.kt`

- [x] **Step 1: Write failing test**

  Add a test near the existing material pickup tests proving that, when
  `navigation.follow` returns `navigation-did-not-start`, the runner performs
  generated `player.move` with `jump=true` toward an elevated visible `Oak Log`
  drop until `inventory.query` proves the log count increased.

- [x] **Step 2: Verify RED**

  Run:

  ```sh
  mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest.runner jumps during generated pickup fallback movement toward elevated material drop*'
  ```

  Expected: fails before implementation because fallback generated movement
  does not request jump for the elevated public drop.

### Task 2: Implement Elevated Pickup Movement

**Files:**
- Modify: `testkit/src/main/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunner.kt`

- [x] **Step 1: Add target-aware jump flag**

  In `moveToward(...)`, compare the latest public target position with the
  latest public player position and set generated `player.move` `jump=true`
  when the target is meaningfully higher.

- [x] **Step 2: Keep existing bounded public pickup loop**

  Keep the existing bounded material-drop loop that re-queries `entity.query`,
  moves toward the latest visible drop, and checks `inventory.query`; do not add
  product actions or route families.

- [x] **Step 3: Verify GREEN**

  Run:

  ```sh
  mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest.runner jumps during generated pickup fallback movement toward elevated material drop*'
  ```

### Task 3: Register Phase 55

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`

- [x] **Step 1: Add AGENTS phase**

  Add `55. public-agent pickup convergence.` and state that this phase is
  external public-agent policy only, with no new product gameplay action.

- [x] **Step 2: Add checklist evidence**

  Add Phase 55 checklist items for spec, plan, repeated generated pickup
  movement, elevated jump behavior, bounded behavior, and no product API
  growth.

### Task 4: Verify And Rerun Final Gameplay

**Files:**
- Commit all Phase 55 changes after successful verification.

- [x] **Step 1: Run gates**

  Run:

  ```sh
  git diff --check
  mise exec -- gradle :testkit:test
  mise exec -- gradle :driver-fabric:test
  mise run lint
  mise run architecture-check
  mise run ci
  ```

- [ ] **Step 2: Rerun final gameplay**

  Run:

  ```sh
  CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS=90000 CRAFTLESS_FABRIC_SMOKE_ACTION_TIMEOUT_MS=120000 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=1800000 mise exec -- gradle :driver-fabric:fabricFinalGameplay
  ```

  If the run blocks, preserve artifacts and fix the next concrete blocker. If
  it reaches ready without `public-agent-blocked`, ask Robin to join and
  confirm completion in Minecraft chat.
