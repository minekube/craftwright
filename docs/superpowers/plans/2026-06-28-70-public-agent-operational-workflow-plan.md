# Public Agent Operational Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Craftless public gameplay agent skill operational for generated API, adaptive CLI, JSON-RPC, SSE, and evidence-driven final gameplay.

**Architecture:** Keep product behavior unchanged. Update the repo-local skill and docs, then add a protocol policy test that scans the skill for the operational guidance future agents must not lose.

**Tech Stack:** Markdown agent skills, Kotlin protocol policy test, Gradle, mise.

---

### Task 1: Add A Policy Test

**Files:**
- Modify: `protocol/src/test/kotlin/com/minekube/craftless/protocol/NamespacePolicyTest.kt`

- [x] **Step 1: Add skill guidance test**

  Add a test named `public gameplay agent skill keeps generated workflow guidance`
  that reads `.agents/skills/craftless-public-gameplay-agent/SKILL.md` and
  asserts it contains these phrases:

  ```kotlin
  val required = listOf(
      "GET /openapi.json",
      "GET /clients/{id}/openapi.json",
      "craftless clients <id> actions",
      "craftless clients <id> run <action>",
      "POST /clients/{id}:run",
      "POST JSON-RPC-style",
      "GET /clients/{id}/events:stream",
      "missing-generic-primitive",
      "public-agent-state.jsonl",
      "without server-provisioned inventory",
  )
  ```

- [x] **Step 2: Run the focused test and observe failure**

  Run:

  ```sh
  mise exec -- gradle :protocol:test --tests '*NamespacePolicyTest.public gameplay agent skill keeps generated workflow guidance' --rerun-tasks
  ```

  Expected: fail before the skill update if any required phrase is missing.

### Task 2: Update Public Gameplay Agent Skill

**Files:**
- Modify: `.agents/skills/craftless-public-gameplay-agent/SKILL.md`

- [x] **Step 1: Add operational workflow**

  Add concise sections covering adaptive CLI discovery, generated invocation,
  POST JSON-RPC-style query/control, SSE observation, and artifact capture.

- [x] **Step 2: Keep shortcut blockers explicit**

  Preserve the forbidden shortcut list and require
  `missing-generic-primitive:<action-or-resource>` when a generated primitive
  is absent.

### Task 3: Register Phase 70

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/agent-skills.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/specs/2026-06-28-70-public-agent-operational-workflow-design.md`
- Create: `docs/superpowers/plans/2026-06-28-70-public-agent-operational-workflow-plan.md`

- [x] **Step 1: Update AGENTS phase list**

  Add Phase 70 after Phase 69 and describe it as operational public-agent
  generated API/CLI/SSE/JSON-RPC guidance only.

- [x] **Step 2: Update agent skills doc and checklist**

  Record that the public gameplay skill now includes adaptive CLI,
  JSON-RPC-style control, SSE observation, and evidence artifact workflow.

### Task 4: Verify, Commit, Push

**Files:**
- Verify changed files.

- [x] **Step 1: Run focused and full verification**

  Run:

  ```sh
  mise exec -- gradle :protocol:test --tests '*NamespacePolicyTest.public gameplay agent skill keeps generated workflow guidance' --rerun-tasks
  git diff --check
  mise run architecture-check
  mise run ci
  ```

- [ ] **Step 2: Commit and push**

  Run:

  ```sh
  git add AGENTS.md docs/agent-skills.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-70-public-agent-operational-workflow-design.md docs/superpowers/plans/2026-06-28-70-public-agent-operational-workflow-plan.md .agents/skills/craftless-public-gameplay-agent/SKILL.md protocol/src/test/kotlin/com/minekube/craftless/protocol/NamespacePolicyTest.kt
  git commit -m "docs: strengthen public agent workflow"
  git push origin main
  ```

- [ ] **Step 3: Verify remote CI**

  Run:

  ```sh
  gh run list --repo minekube/craftless --branch main --limit 5 --json databaseId,headSha,status,conclusion,name,event,createdAt
  gh run watch <latest-run-id> --repo minekube/craftless --exit-status
  ```
