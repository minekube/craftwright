# Generated Actions Help Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `craftless clients <id> actions --help` render generated action help from the live per-client OpenAPI document.

**Architecture:** Preserve JSON output for `clients <id> actions`. Add a help-only rendering path in the CLI that fetches the same live OpenAPI authority and formats action ids, arguments, and generated route evidence without adding static gameplay commands.

**Tech Stack:** Kotlin/JVM CLI, Ktor Client, Gradle through mise.

---

### Task 1: Add Generated Help Test

**Files:**
- Modify: `cli/src/test/kotlin/com/minekube/craftless/cli/CraftlessCliTest.kt`

- [x] **Step 1: Write failing test**

  Add a test named `clients actions help is generated from live openapi actions`
  that starts `LocalTestApiServer`, creates `alice`, runs:

  ```sh
  craftless clients alice actions --help --api <server-url>
  ```

  Assert stdout contains:

  - `Actions for client alice`;
  - `craftless clients alice player chat`;
  - `--message string required`;
  - `craftless clients alice player move`;
  - `--forward boolean`;
  - and does not start with a JSON array.

- [x] **Step 2: Verify RED**

  Run:

  ```sh
  mise exec -- gradle :cli:test --tests '*CraftlessCliTest.clients actions help is generated from live openapi actions'
  ```

  Expected before implementation: FAIL because the command still returns JSON.

### Task 2: Render Help From Live OpenAPI

**Files:**
- Modify: `cli/src/main/kotlin/com/minekube/craftless/cli/Main.kt`

- [x] **Step 1: Add help rendering branch**

  In `getClientActions`, after decoding the live `OpenApiDocument`, render
  generated help when `args.contains("--help")`; otherwise preserve existing
  JSON output.

- [x] **Step 2: Add renderer**

  Add a private `OpenApiDocument.generatedActionsHelp(clientId: String)` helper
  that iterates `actions`, formats command segments from action ids, appends
  argument flags from `OpenApiAction.arguments`, and includes the route from
  `OpenApiAction.toolRoute(...)`.

- [x] **Step 3: Verify GREEN**

  Run:

  ```sh
  mise exec -- gradle :cli:test --tests '*CraftlessCliTest.clients actions help is generated from live openapi actions'
  ```

  Expected after implementation: PASS.

### Task 3: Register Phase 72 And Verify

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/specs/2026-06-28-72-generated-actions-help-design.md`
- Create: `docs/superpowers/plans/2026-06-28-72-generated-actions-help-plan.md`

- [x] **Step 1: Register governance**

  Add Phase 72 to `AGENTS.md` and the checklist as adaptive CLI generated help
  only. State that it adds no static gameplay catalog or support claim.

- [x] **Step 2: Run verification**

  Run:

  ```sh
  mise exec -- gradle :cli:test --tests '*CraftlessCliTest.clients actions help is generated from live openapi actions'
  mise exec -- gradle :cli:test
  git diff --check
  mise run architecture-check
  mise run ci
  ```

- [x] **Step 3: Commit, push, and verify CI**

  Run:

  ```sh
  git add AGENTS.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-72-generated-actions-help-design.md docs/superpowers/plans/2026-06-28-72-generated-actions-help-plan.md cli/src/main/kotlin/com/minekube/craftless/cli/Main.kt cli/src/test/kotlin/com/minekube/craftless/cli/CraftlessCliTest.kt
  git commit -m "cli: generate actions help from live openapi"
  git push origin main
  gh run watch <latest-run-id> --repo minekube/craftless --exit-status
  ```

### Guardrails

- [x] No static gameplay action descriptor, route family, CLI gameplay command,
  Fabric descriptor/binding pair, or scenario shortcut is added.
- [x] `clients <id> actions` JSON output remains the default.
- [x] Generated help is derived from live per-client OpenAPI metadata.
