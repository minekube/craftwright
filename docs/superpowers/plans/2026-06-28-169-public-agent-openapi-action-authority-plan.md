# Public Agent OpenAPI Action Authority Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the public-agent gameplay runner use generated per-client OpenAPI `x-craftless-actions` as its action metadata authority.

**Architecture:** Keep the runner's existing HTTP flow, but treat `/clients/{id}/actions` as an artifact/projection fetch only. Parse action ids and argument metadata from the already-fetched client OpenAPI document.

**Tech Stack:** Kotlin, Ktor MockEngine, kotlinx.serialization JSON, Gradle through mise.

---

### Task 1: Add The Regression Test

**Files:**
- Modify: `testkit/src/test/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunnerTest.kt`

- [ ] **Step 1: Write the failing test**

Add a test where the mock server returns a complete generated OpenAPI
`x-craftless-actions` list but returns an empty `/actions` projection. Assert
that the runner still completes and that `/actions` was fetched.

- [ ] **Step 2: Run the focused test**

Run:

```sh
mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest.runner uses generated client openapi actions as authority over actions projection*'
```

Expected: FAIL with `missing-generic-primitive:*` because the current runner
uses `/actions`.

### Task 2: Parse OpenAPI Actions In The Runner

**Files:**
- Modify: `testkit/src/main/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunner.kt`

- [ ] **Step 1: Add OpenAPI action parsing helpers**

Add helpers that parse `x-craftless-actions` from the client OpenAPI JSON and
return the action descriptor array, action ids, and argument support.

- [ ] **Step 2: Change discovery authority**

Use the client OpenAPI helpers for `actionIds` and `actionSupportsArgument`.
Keep `/actions` as the stored projection string in `PublicAgentGameplayResult`.

- [ ] **Step 3: Run the focused test**

Run:

```sh
mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest.runner uses generated client openapi actions as authority over actions projection*'
```

Expected: PASS.

### Task 3: Verify And Record Evidence

**Files:**
- Modify: `docs/project-completion-checklist.md`
- Modify: `docs/superpowers/phase-index.md`
- Create: `docs/superpowers/evidence/2026-06-28-public-agent-openapi-action-authority.md`

- [ ] **Step 1: Run focused public-agent tests**

Run:

```sh
mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest*'
```

Expected: PASS.

- [ ] **Step 2: Run broader verification**

Run:

```sh
mise exec -- gradle :testkit:test
mise run ci
git diff --check
```

Expected: PASS.

- [ ] **Step 3: Commit and push**

Run:

```sh
git add testkit/src/main/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunner.kt testkit/src/test/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunnerTest.kt docs/project-completion-checklist.md docs/superpowers/phase-index.md docs/superpowers/specs/2026-06-28-169-public-agent-openapi-action-authority-design.md docs/superpowers/plans/2026-06-28-169-public-agent-openapi-action-authority-plan.md docs/superpowers/evidence/2026-06-28-public-agent-openapi-action-authority.md
git commit -m "refactor: use openapi actions in public agent"
git push origin main
```
