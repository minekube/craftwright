# Public Agent Actions Projection Optional Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> superpowers:subagent-driven-development or superpowers:executing-plans to
> implement this plan task-by-task. Follow TDD for the public-agent behavior
> change.

**Goal:** Make public-agent gameplay independent from successful
`/clients/{id}/actions` projection responses.

**Architecture:** Generated per-client OpenAPI is the action authority.
`/actions` is projection/evidence only.

**Tech Stack:** Kotlin, Ktor Client MockEngine, Gradle through mise.

---

### Task 1: Add The Regression Test

**Files:**

- Modify:
  `testkit/src/test/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunnerTest.kt`

- [x] Add a mock server mode where generated OpenAPI advertises the complete
  action set but `/actions` returns `404 Not Found`.
- [x] Assert the public-agent runner reaches `RAN`.
- [x] Assert the runner's recorded `actions` artifact is `[]`.
- [x] Assert available actions come from generated OpenAPI.
- [x] Run the focused test before implementation and capture the red failure.

### Task 2: Make Projection Fetch Optional

**Files:**

- Modify:
  `testkit/src/main/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunner.kt`

- [x] Add a helper for optional action projection fetches.
- [x] Return `[]` on non-2xx projection responses.
- [x] Return `[]` on projection fetch IO failures.
- [x] Keep OpenAPI action metadata as the required primitive authority.

### Task 3: Add Authority Guards

**Files:**

- Modify:
  `cli/src/test/kotlin/com/minekube/craftless/cli/CraftlessCliTest.kt`
- Modify:
  `daemon/src/test/kotlin/com/minekube/craftless/daemon/HttpDriverSessionTest.kt`

- [x] Add a CLI source guard proving adaptive CLI code does not fetch
  `/clients/{id}/actions` as gameplay authority.
- [x] Add an HTTP remote source guard proving `HttpDriverSession` does not
  fetch `get("actions")`.
- [x] Fix the test repository-root helper so Gradle module working directories
  resolve correctly.

### Task 4: Verify And Close CL-01

**Files:**

- Modify: `docs/project-completion-checklist.md`
- Modify: `docs/superpowers/phase-index.md`
- Create:
  `docs/superpowers/evidence/2026-06-28-public-agent-actions-projection-optional.md`

- [x] Record red/green public-agent evidence.
- [x] Record CLI and HTTP guard evidence.
- [x] Run local verification gates.
- [ ] Commit and push to `main`.
