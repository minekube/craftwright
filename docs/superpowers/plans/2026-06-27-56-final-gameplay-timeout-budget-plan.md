# Final Gameplay Timeout Budget Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure final gameplay produces confirmation or timeout artifacts by giving the outer smoke process enough budget for public-agent runtime plus the human hold window.

**Architecture:** Add one internal Fabric smoke env var for the public-agent helper process timeout and make the final Gradle task derive the outer local-smoke timeout from that process budget plus hold plus buffer. Preserve the existing shorter per-action request timeout and keep all changes outside public gameplay API breadth.

**Tech Stack:** Kotlin/JVM, Gradle Kotlin DSL, Fabric smoke controller tests, mise.

---

### Task 1: RED Tests For Non-Circular Timeout Budgets

**Files:**
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [x] **Step 1: Add failing Gradle script assertion**

  Replace the old outer-timeout assertion with checks that the build script
  contains a dedicated public-agent command timeout function and computes the
  outer timeout from `publicAgentCommandMillis + holdMillis + 180_000L`.

  ```kotlin
  assertTrue(buildScript.contains("finalGameplayPublicAgentCommandTimeout"))
  assertTrue(buildScript.contains("\"CRAFTLESS_FABRIC_SMOKE_PUBLIC_AGENT_COMMAND_TIMEOUT_MS\""))
  assertTrue(buildScript.contains("publicAgentCommandMillis + holdMillis + 180_000L"))
  assertFalse(buildScript.contains("fabricActionMillis + holdMillis + 180_000L"))
  ```

- [x] **Step 2: Add failing controller config assertion**

  Add a focused test:

  ```kotlin
  @Test
  fun `fabric smoke controller parses public agent process timeout separately from outer smoke timeout`() {
      val controller =
          FabricClientSmokeController.fromEnvironment(
              mapOf(
                  "CRAFTLESS_FABRIC_CLIENT_SMOKE" to "1",
                  "CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS" to "2100000",
                  "CRAFTLESS_FABRIC_SMOKE_ACTION_TIMEOUT_MS" to "120000",
                  "CRAFTLESS_FABRIC_SMOKE_PUBLIC_AGENT_COMMAND_TIMEOUT_MS" to "2400000",
              ),
          )

      assertEquals(120_000.milliseconds, controller.actionTimeout)
      assertEquals(2_400_000.milliseconds, controller.publicAgentCommandTimeout)
  }
  ```

- [x] **Step 3: Verify RED**

  Run:

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric final gameplay outer timeout covers public agent runtime and human hold window*' --tests '*FabricDriverModuleTest.fabric smoke controller parses public agent process timeout separately from outer smoke timeout*'
  ```

  Expected: FAIL because the dedicated env var/function and outer-timeout formula
  do not exist yet.

### Task 2: Implement Dedicated Public-Agent Process Timeout

**Files:**
- Modify: `driver-fabric/build.gradle.kts`
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricClientSmokeController.kt`

- [x] **Step 1: Add Gradle timeout function**

  Add:

  ```kotlin
  fun finalGameplayPublicAgentCommandTimeout(): String =
      (
          envLong("CRAFTLESS_FABRIC_SMOKE_PUBLIC_AGENT_COMMAND_TIMEOUT_MS")
              ?: envLong("CRAFTLESS_PUBLIC_AGENT_COMMAND_TIMEOUT_MS")
              ?: envLong("CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS")
              ?: 2_700_000L
      ).toString()
  ```

- [x] **Step 2: Use process timeout for outer budget**

  Change `finalGameplayOuterActionTimeout()` to:

  ```kotlin
  fun finalGameplayOuterActionTimeout(): String {
      val holdMillis = envLong("CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS") ?: 600_000L
      val publicAgentCommandMillis = finalGameplayPublicAgentCommandTimeout().toLong()
      val requestedOuter = envLong("CRAFTLESS_LOCAL_SERVER_SMOKE_ACTION_TIMEOUT_MS")
      return maxOf(requestedOuter ?: 0L, publicAgentCommandMillis + holdMillis + 180_000L, 1_500_000L).toString()
  }
  ```

- [x] **Step 3: Export process timeout to the Fabric controller**

  In `fabricFinalGameplay`, add:

  ```kotlin
  environment(
      "CRAFTLESS_FABRIC_SMOKE_PUBLIC_AGENT_COMMAND_TIMEOUT_MS",
      finalGameplayPublicAgentCommandTimeout(),
  )
  ```

- [x] **Step 4: Parse process timeout in controller**

  Add env constants:

  ```kotlin
  private const val PUBLIC_AGENT_COMMAND_TIMEOUT = "CRAFTLESS_FABRIC_SMOKE_PUBLIC_AGENT_COMMAND_TIMEOUT_MS"
  private const val PUBLIC_AGENT_COMMAND_TIMEOUT_LEGACY = "CRAFTLESS_PUBLIC_AGENT_COMMAND_TIMEOUT_MS"
  ```

  Change `publicAgentCommandTimeout` derivation to prefer the dedicated env var,
  then legacy spelling, then the outer smoke timeout for compatibility, then the
  action timeout.

- [x] **Step 5: Verify GREEN**

  Run the focused command from Task 1 Step 3. Expected: PASS.

### Task 3: Docs, Checklist, And Final Verification

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/specs/2026-06-27-56-final-gameplay-timeout-budget-design.md`
- Create: `docs/superpowers/plans/2026-06-27-56-final-gameplay-timeout-budget-plan.md`

- [x] **Step 1: Add Phase 56 guardrails**

  Add Phase 56 to `AGENTS.md` and the checklist. Record that this phase changes
  only timeout/evidence plumbing and does not add public gameplay breadth.

- [x] **Step 2: Run local verification serially**

  Run:

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric final gameplay outer timeout covers public agent runtime and human hold window*' --tests '*FabricDriverModuleTest.fabric smoke controller parses public agent process timeout separately from outer smoke timeout*'
  mise run lint
  mise run architecture-check
  mise run ci
  ```

- [ ] **Step 3: Commit and push**

  Run:

  ```sh
  git add AGENTS.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-27-56-final-gameplay-timeout-budget-design.md docs/superpowers/plans/2026-06-27-56-final-gameplay-timeout-budget-plan.md driver-fabric/build.gradle.kts driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricClientSmokeController.kt driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt
  git commit -m "driver-fabric: separate final gameplay timeout budgets"
  git push origin main
  ```

- [ ] **Step 4: Rerun held final gameplay**

  Run:

  ```sh
  CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS=90000 CRAFTLESS_FABRIC_SMOKE_ACTION_TIMEOUT_MS=120000 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=1800000 mise exec -- gradle :driver-fabric:fabricFinalGameplay
  ```

  Expected: if Robin does not confirm in Minecraft chat, the task exits
  successfully with `final-gameplay-confirmation-timeout.json`. If Robin does
  confirm, it exits successfully with `final-gameplay-confirmation.json`.

## Self-Review

- Spec coverage: covers the observed failure, non-circular timeout model, tests,
  docs, final rerun, and no-public-gameplay-breadth rule.
- Placeholder scan: no TBD/TODO/fill-in placeholders.
- Type consistency: env var and function names match across tasks.
