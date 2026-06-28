# Remote Driver Action Graph Authority Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> superpowers:subagent-driven-development or superpowers:executing-plans to
> implement this plan task-by-task. Follow TDD: prove the stale `/actions`
> authority path fails before changing production code.

**Goal:** Make remote attached `HttpDriverSession.actions()` derive from
`runtimeGraph()` instead of fetching a separate `/actions` endpoint.

**Architecture:** The shared `DriverSession.actions()` default is the only
compatibility projection needed for graph-backed sessions. Remote HTTP driver
sessions should fetch `runtime-graph` and project descriptors locally.

**Tech Stack:** Kotlin, Ktor Client/Server, kotlinx.serialization JSON,
Gradle through mise.

---

### Task 1: Add The Regression Test

**Files:**

- Create:
  `daemon/src/test/kotlin/com/minekube/craftless/daemon/HttpDriverSessionTest.kt`

- [x] Start a Ktor loopback test endpoint that exposes `runtime-graph`.
- [x] Make `/actions` fail and count calls.
- [x] Assert `HttpDriverSession.actions()` returns the graph-projected
  `player.chat` descriptor.
- [x] Assert `/actions` was not called.
- [x] Run the focused test before implementation and capture the red failure.

### Task 2: Remove The Remote Action Fetch

**Files:**

- Modify:
  `daemon/src/main/kotlin/com/minekube/craftless/daemon/HttpDriverSession.kt`

- [x] Remove the `DriverActionDescriptor` import.
- [x] Remove the `override fun actions(): List<DriverActionDescriptor> =
  get("actions")` implementation.
- [x] Let the inherited `DriverSession.actions()` default fetch
  `runtimeGraph()` and project operations.

### Task 3: Verify Attach Compatibility

**Files:**

- No production changes required in `driver-fabric-attach`.

- [x] Run the focused daemon regression.
- [x] Run `:driver-fabric-attach:test` to prove self-attach still works.
- [x] Run broader daemon/architecture checks before committing.

### Task 4: Record Evidence

**Files:**

- Create:
  `docs/superpowers/evidence/2026-06-28-remote-driver-action-graph-authority.md`
- Modify:
  `docs/superpowers/phase-index.md`
- Modify:
  `docs/project-completion-checklist.md`

- [x] Record red/green focused evidence.
- [x] Record attach compatibility evidence.
- [x] Record final local verification.
- [ ] Commit and push to `main`.
