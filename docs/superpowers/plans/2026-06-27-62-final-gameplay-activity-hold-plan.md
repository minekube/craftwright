# Final Gameplay Activity Hold Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the held final gameplay API alive during active Minecraft chat play before Robin sends the configured completion phrase.

**Architecture:** Add an optional activity-extension duration to the Fabric smoke controller's confirmation hold. The controller keeps the existing confirmation/timeout semantics, but extends the deadline when new non-confirmation chat evidence appears.

**Tech Stack:** Kotlin/JVM, Ktor test harness, Gradle via mise.

---

### Task 1: Controller Activity Extension

**Files:**
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricClientSmokeController.kt`
- Modify: `driver-fabric/build.gradle.kts`
- Test: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [x] **Step 1: Add a failing controller test**

Add a test that configures:

```kotlin
"CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS" to "40",
"CRAFTLESS_FABRIC_SMOKE_ACTIVITY_EXTENDS_HOLD_MS" to "150",
"CRAFTLESS_FABRIC_SMOKE_CONFIRM_CHAT_CONTAINS" to "goal may be completed",
```

After `final-gameplay-ready.json` exists, append a non-confirmation chat line
before the original 40 ms hold expires, and assert the controller has not
stopped at the original deadline. Then wait for final timeout and assert
`final-gameplay-confirmation-timeout.json` includes
`"activity-extends-hold-ms":"150"`.

- [x] **Step 2: Add controller config**

Add `activityExtendsHold: Duration = 0.milliseconds`, parse
`CRAFTLESS_FABRIC_SMOKE_ACTIVITY_EXTENDS_HOLD_MS`, validate it is not negative,
and export a final-gameplay Gradle default.

- [x] **Step 3: Extend the hold on new chat activity**

Track indexed chat evidence while waiting for confirmation. When a new
non-confirmation chat line appears and the extension duration is positive,
extend the deadline to at least `now + activityExtendsHold`, and append an
activity event to `final-gameplay-activity.jsonl`.

- [x] **Step 4: Verify**

Run:

```bash
mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric smoke controller extends final gameplay hold on chat activity*'
```

Expected: PASS.

### Task 2: Checklist And Guardrails

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`

- [x] **Step 1: Register Phase 62**

Add Phase 62 to `AGENTS.md` and the checklist as an activity-aware final
gameplay hold. State that it must not add public gameplay APIs or treat timeout
as success.

- [x] **Step 2: Verify docs**

Run:

```bash
git diff --check
```

Expected: no whitespace errors.
