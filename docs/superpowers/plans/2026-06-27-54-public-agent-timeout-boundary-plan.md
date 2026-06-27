# Public Agent Timeout Boundary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure final gameplay generated-action request failures are reported by the public-agent helper before the outer Fabric smoke harness times out.

**Architecture:** Keep final gameplay composed outside the driver through generated public actions. Make timeout ownership explicit: the public-agent helper uses an inner generated-action request timeout, while the Fabric smoke controller keeps a longer process timeout so blocker artifacts can be written.

**Tech Stack:** Kotlin/JVM, Gradle Kotlin DSL, Ktor Client, kotlin.test, mise.

---

### Task 1: Add Timeout Contract Tests

**Files:**
- Modify: `testkit/src/test/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunnerTest.kt`
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [x] **Step 1: Add config precedence test**

  Add a test proving `CRAFTLESS_FABRIC_SMOKE_ACTION_TIMEOUT_MS` wins over the
  long outer `CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS` when no explicit public-agent
  timeout is present.

- [x] **Step 2: Keep explicit override test coverage**

  Keep the existing explicit `CRAFTLESS_PUBLIC_AGENT_ACTION_REQUEST_TIMEOUT_MS`
  test and add the Fabric and outer timeout values to prove the explicit value
  still wins.

- [x] **Step 3: Add Gradle task environment test**

  Add a source test proving `driver-fabric/build.gradle.kts` exports
  `CRAFTLESS_PUBLIC_AGENT_ACTION_REQUEST_TIMEOUT_MS` from the final gameplay
  task.

- [x] **Step 4: Add controller process-timeout test**

  Add a controller test proving the public-agent helper process uses the long
  outer smoke timeout while generated action requests keep the shorter Fabric
  action timeout.

- [x] **Step 5: Verify RED**

  Run:

  ```sh
  mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest.runner config prefers fabric smoke action timeout over outer smoke timeout*' :driver-fabric:test --tests '*FabricDriverModuleTest.final gameplay config exports public agent action request timeout below fabric action timeout*'
  ```

  Expected: fails before implementation because the config still uses the
  outer smoke timeout and the Gradle task does not export the public-agent
  timeout.

### Task 2: Implement Timeout Boundary

**Files:**
- Modify: `testkit/src/main/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunner.kt`
- Modify: `driver-fabric/build.gradle.kts`

- [x] **Step 1: Prefer Fabric timeout in runner config**

  Add `CRAFTLESS_FABRIC_SMOKE_ACTION_TIMEOUT_MS` to
  `PublicAgentGameplayRunnerConfig.fromEnvironment()` between the explicit
  public-agent timeout and the outer smoke timeout.

- [x] **Step 2: Export final gameplay public-agent timeout**

  Add a helper in `driver-fabric/build.gradle.kts` that returns a guarded
  public-agent request timeout below `finalGameplayFabricActionTimeout()`, and
  export it as `CRAFTLESS_PUBLIC_AGENT_ACTION_REQUEST_TIMEOUT_MS` in
  `fabricFinalGameplay`.

- [x] **Step 3: Split public-agent process timeout from generated action timeout**

  Give `FabricClientSmokeController` a separate public-agent helper process
  timeout sourced from the long outer smoke timeout, while still passing the
  shorter action timeout to `CRAFTLESS_PUBLIC_AGENT_ACTION_REQUEST_TIMEOUT_MS`.

- [x] **Step 4: Verify focused tests GREEN**

  Run:

  ```sh
  mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest.runner config prefers fabric smoke action timeout over outer smoke timeout*' :driver-fabric:test --tests '*FabricDriverModuleTest.final gameplay config exports public agent action request timeout below fabric action timeout*'
  ```

### Task 3: Register Phase 54

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Modify: `.mise.toml`

- [x] **Step 1: Register Phase 54 in `AGENTS.md`**

  Add `54. public-agent timeout boundary.` to the active phase list and explain
  that generated-action request timeouts must resolve inside the final
  gameplay helper before the outer smoke process timeout.

- [x] **Step 2: Add checklist evidence**

  Add a Phase 54 checklist section with spec path, plan path, timeout
  precedence, Gradle exported timeout, and verification commands.

- [x] **Step 3: Keep architecture check Gradle tests sequential**

  Keep `mise run architecture-check` running Gradle module test targets as
  separate commands before the Bun Playwright tests so local verification does
  not corrupt Gradle binary test result files through concurrent test tasks.

### Task 4: Verify, Commit, Push, And Monitor

**Files:**
- Commit all Phase 54 changes.

- [x] **Step 1: Run quality gates**

  Run:

  ```sh
  git diff --check
  mise exec -- gradle :testkit:test :driver-fabric:test
  mise run lint
  mise run architecture-check
  mise run ci
  ```

- [ ] **Step 2: Rerun final gameplay**

  Run the held final gameplay command through `mise`. If the public agent
  reaches the ready hold, ask Robin to join and confirm in Minecraft chat. If
  the public agent blocks, preserve the artifacts and fix the next concrete
  blocker.

- [x] **Step 3: Commit and push**

  Run:

  ```sh
  git add .mise.toml AGENTS.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-27-54-public-agent-timeout-boundary-design.md docs/superpowers/plans/2026-06-27-54-public-agent-timeout-boundary-plan.md driver-fabric/build.gradle.kts driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt testkit/src/main/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunner.kt testkit/src/test/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunnerTest.kt
  git commit -m "testkit: bound public agent action timeouts"
  git push origin main
  ```

- [x] **Step 4: Verify remote CI**

  Run:

  ```sh
  gh run list --repo minekube/craftless --branch main --limit 3
  latest_run_id="$(gh run list --repo minekube/craftless --branch main --json databaseId --jq '.[0].databaseId')"
  gh run watch "$latest_run_id" --repo minekube/craftless --exit-status
  ```
