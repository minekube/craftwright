# Client Lifecycle Surface Clarity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make it obvious from generated Craftless surfaces that `clients create` launches a new real client and must not be used as a retry/select loop.

**Architecture:** Add lifecycle prose to protocol route metadata, project it into OpenAPI, and let generated CLI help print the same metadata. Mirror the exact warning in README and the repo-local public gameplay-agent skill.

**Tech Stack:** Kotlin/JVM protocol and CLI modules, kotlinx.serialization OpenAPI DTOs, Bun distribution-surface tests, Markdown docs.

---

### Task 1: Protocol And CLI Metadata

**Files:**
- Modify: `protocol/src/main/kotlin/com/minekube/craftless/protocol/ApiRoute.kt`
- Modify: `protocol/src/main/kotlin/com/minekube/craftless/protocol/OpenApiDocument.kt`
- Modify: `cli/src/main/kotlin/com/minekube/craftless/cli/GeneratedRouteCli.kt`
- Test: `protocol/src/test/kotlin/com/minekube/craftless/protocol/OpenApiGenerationTest.kt`
- Test: `cli/src/test/kotlin/com/minekube/craftless/cli/CraftlessCliTest.kt`

- [x] **Step 1: Write failing tests**

Add protocol assertions that `POST /clients` description includes process launch, non-reuse semantics, `GET /clients`, `POST /clients/{id}:stop`, and timestamped-id warning. Add CLI help assertions for the same warning.

- [x] **Step 2: Verify red**

Run:

```sh
mise exec -- gradle :protocol:test --tests com.minekube.craftless.protocol.OpenApiGenerationTest.stable\ supervisor\ openapi\ makes\ client\ creation\ lifecycle\ explicit
mise exec -- gradle :cli:test --tests com.minekube.craftless.cli.CraftlessCliTest.generated\ supervisor\ route\ help\ is\ loaded\ from\ supervisor\ openapi
```

Expected: tests fail because `OpenApiOperation.description` is absent or CLI help does not include the lifecycle warning.

- [x] **Step 3: Implement metadata path**

Add optional `summary` and `description` fields to `ApiRoute` and `OpenApiOperation`, set lifecycle descriptions for client list/create/get/connect/stop routes, and print summary/description in `GeneratedRouteCli.help()`.

- [x] **Step 4: Verify green**

Run the same focused Gradle tests and confirm both pass.

### Task 2: Public Docs And Agent Skill

**Files:**
- Modify: `README.md`
- Modify: `docs/agent-operating-contract.md`
- Modify: `.agents/skills/craftless-public-gameplay-agent/SKILL.md`
- Test: `playwright/src/distribution.test.ts`

- [x] **Step 1: Write failing docs guard**

Add a Bun distribution test that normalizes README and gameplay-agent skill text and checks for the process-launch warning, non-reuse wording, timestamped-id warning, and stop command.

- [x] **Step 2: Verify red**

Run:

```sh
mise exec -- bun test playwright/src/distribution.test.ts --test-name-pattern "public docs make client creation lifecycle explicit"
```

Expected: test fails until the README and skill include the warning.

- [x] **Step 3: Update surfaces**

Add lifecycle discipline text to README, the operating contract, and the public gameplay-agent skill. Replace broad unique-id retry advice with "fresh unique ids only for deliberate independent clients."

- [x] **Step 4: Verify green**

Run the focused Bun docs guard and confirm it passes.

### Task 3: Final Verification And Publish

**Files:**
- Modify: `docs/superpowers/phase-index.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/evidence/2026-06-29-client-lifecycle-surface-clarity.md`

- [ ] **Step 1: Run broad verification**

Run:

```sh
mise exec -- gradle :protocol:test :cli:test
mise exec -- bun test playwright/src/distribution.test.ts
git diff --check
```

- [ ] **Step 2: Record evidence**

Update the evidence file and phase index with exact commands and outcomes.

- [ ] **Step 3: Commit and push main**

Run:

```sh
git status --short --branch
git add .
git commit -m "fix: clarify client creation lifecycle"
git push origin main
```
