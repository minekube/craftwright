# Installed Packaged Older Fabric Live Attach Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove the packaged Craftless CLI distribution can launch and attach the representative older Minecraft `1.20.6` Fabric lane.

**Architecture:** Use the existing package task to build the distribution and Docker staging directory. Start the packaged supervisor from `build/docker/craftless/bin/craftless`, create the older client through packaged `clients create`, then fetch generated API evidence through the packaged CLI/API surface. Record artifacts and keep the final survival gate separate.

**Tech Stack:** mise, Gradle packaging, packaged Craftless CLI, Ktor supervisor, Fabric Loader.

---

### Task 1: Governance

**Files:**
- Modify: `AGENTS.md`
- Modify: `daemon/AGENTS.md`
- Modify: `driver-api/AGENTS.md`
- Modify: `driver-runtime/AGENTS.md`
- Modify: `driver-fabric/AGENTS.md`
- Modify: `protocol/AGENTS.md`
- Modify: `cli/AGENTS.md`
- Modify: `testkit/AGENTS.md`
- Modify: `docs/AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/specs/2026-06-28-142-installed-packaged-older-fabric-live-attach-design.md`
- Create: `docs/superpowers/plans/2026-06-28-142-installed-packaged-older-fabric-live-attach-plan.md`

- [x] **Step 1: Record Phase 142 governance**

  State that this phase proves installed packaged older-lane live attach but
  not final honest survival gameplay.

- [x] **Step 2: Align scoped agent instructions**

  Tighten driver, daemon, protocol, CLI, docs, and testkit module instructions
  so future work stays version-agnostic by default and isolates per-version
  code only where runtime APIs actually diverge.

### Task 2: Build Packaged CLI

**Files:**
- Uses: `build/docker/craftless/bin/craftless`

- [x] **Step 1: Build the package**

  ```sh
  mise run package-cli
  ```

  Expected: tar/zip and Docker staging contain current and older Fabric driver
  mod entries.

### Task 3: Run Packaged Older Live Attach

**Files:**
- Evidence root: `/tmp/craftless-packaged-older-live-attach`

- [x] **Step 1: Start packaged supervisor**

  ```sh
  rm -rf /tmp/craftless-packaged-older-live-attach
  build/docker/craftless/bin/craftless server start \
    --port 18082 \
    --workspace /tmp/craftless-packaged-older-live-attach/workspace
  ```

- [x] **Step 2: Create older client through packaged CLI**

  ```sh
  CRAFTLESS_HTTP_REQUEST_TIMEOUT_MS=900000 \
  build/docker/craftless/bin/craftless clients create older-cli \
    --api http://127.0.0.1:18082 \
    --version 1.20.6 \
    --loader fabric \
    --loader-version 0.19.3 \
    --offline-name OlderCli
  ```

- [x] **Step 3: Fetch generated API evidence**

  ```sh
  build/docker/craftless/bin/craftless clients older-cli openapi --api http://127.0.0.1:18082
  build/docker/craftless/bin/craftless clients older-cli actions --api http://127.0.0.1:18082
  build/docker/craftless/bin/craftless clients older-cli resources --api http://127.0.0.1:18082
  build/docker/craftless/bin/craftless clients older-cli events --api http://127.0.0.1:18082
  ```

- [x] **Step 4: Stop the client and supervisor**

  ```sh
  build/docker/craftless/bin/craftless clients older-cli stop --api http://127.0.0.1:18082
  ```

  Then stop the `server start` process.

### Task 4: Evidence And Push

**Files:**
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/evidence/2026-06-28-installed-packaged-older-fabric-live-attach.md`

- [x] **Step 1: Record evidence**

  Include package command output, create-client output, generated API artifact
  summaries, event evidence, runtime identity, and limitation that final honest
  survival gameplay remains incomplete.

- [ ] **Step 2: Run docs hygiene**

  ```sh
  git diff --check
  ```

- [ ] **Step 3: Commit and push**

  ```sh
  git add AGENTS.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-142-installed-packaged-older-fabric-live-attach-design.md docs/superpowers/plans/2026-06-28-142-installed-packaged-older-fabric-live-attach-plan.md docs/superpowers/evidence/2026-06-28-installed-packaged-older-fabric-live-attach.md
  git commit -m "docs: record packaged older live attach"
  git push origin main
  ```

## Self-Review

- Spec coverage: package, packaged supervisor, packaged client create,
  generated API evidence, events, cleanup, and limitation are covered.
- Placeholder scan: no TODO/TBD placeholders.
- Scope: no public gameplay API, static gameplay catalog, route family, or
  scenario shortcut.
