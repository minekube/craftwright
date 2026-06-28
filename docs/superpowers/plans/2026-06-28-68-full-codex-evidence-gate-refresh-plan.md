# Full Codex Evidence Gate Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Record a fresh, current completion-gate evidence set after the final gameplay default changed to Codex evidence.

**Architecture:** Treat this as evidence and docs refresh only. Use existing generated public API gameplay surfaces, release/distribution scripts, compatibility matrix tests, and final gameplay harness. Do not add public gameplay breadth or version support.

**Tech Stack:** mise, Gradle, Bun, Docker, GitHub Releases installer, Markdown evidence docs.

---

### Task 1: Refresh Distribution Evidence

**Files:**
- Create: `docs/superpowers/evidence/2026-06-28-full-codex-evidence-gate-refresh.md`
- Modify: `docs/project-completion-checklist.md`

- [x] **Step 1: Build the CLI distribution**

  Run:

  ```sh
  mise run package-cli
  ```

  Expected: `:cli:distZip` and `:cli:distTar` succeed, and
  `build/docker/craftless/` is refreshed.

- [x] **Step 2: Smoke-run the packaged CLI**

  Run:

  ```sh
  build/docker/craftless/bin/craftless server start --once --port 0 --workspace /tmp/craftless-cli-smoke-$(date +%s)
  ```

  Expected: JSON response with `"ok":true`.

- [x] **Step 3: Build Docker image**

  Run:

  ```sh
  docker build -t craftless:local .
  ```

  Expected: Docker image build succeeds using `build/docker/craftless/`.

- [x] **Step 4: Smoke-run Docker image**

  Run:

  ```sh
  docker run --rm craftless:local /opt/craftless/bin/craftless server start --once --port 0 --workspace /tmp/craftless
  ```

  Expected: JSON response with `"ok":true`.

- [x] **Step 5: Smoke-run installer**

  Run:

  ```sh
  tmp="$(mktemp -d /tmp/craftless-install-smoke.XXXXXX)" && CRAFTLESS_VERSION=v0.1.0 CRAFTLESS_INSTALL_DIR="$tmp/bin" CRAFTLESS_HOME="$tmp/home" ./install.sh && "$tmp/bin/craftless" server start --once --port 0 --workspace "$tmp/workspace"
  ```

  Expected: installer reports `craftless 0.1.0 installed`, and installed
  binary returns `"ok":true`.

### Task 2: Refresh Compatibility Evidence

**Files:**
- Create: `docs/superpowers/evidence/2026-06-28-full-codex-evidence-gate-refresh.md`
- Modify: `docs/project-completion-checklist.md`

- [x] **Step 1: Fetch live Mojang metadata**

  Run the Bun manifest command recorded in the evidence file.

  Expected: latest release `26.2` with Java 25 and representative older
  release `1.20.6` with Java 21.

- [x] **Step 2: Run matrix and probe tests**

  Run:

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricCompatibilityMatrixTest*' --tests '*FabricCapabilityProbeTest.runtime metadata probe emits sanitized compatibility lane evidence*' :testkit:test --tests '*LocalMinecraftServerSmokeTest.local server smoke records unsupported runtime lane without provisioning server*'
  ```

  Expected: pass.

- [x] **Step 3: Run latest unsupported lane smoke**

  Run:

  ```sh
  rm -rf /tmp/craftless-fabric-smoke-26-lane-refresh && CRAFTLESS_FABRIC_CLIENT_SMOKE=1 CRAFTLESS_SMOKE_MINECRAFT_VERSION=26.2 CRAFTLESS_LOCAL_SERVER_SMOKE_ROOT=/tmp/craftless-fabric-smoke-26-lane-refresh CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 mise exec -- gradle :driver-fabric:fabricClientSmoke
  ```

  Expected: task exits successfully and writes `runtime-lane.json` with
  `latest-release-26-2`, `UNSUPPORTED`, Java 25, and
  `runtime-lane-missing`.

### Task 3: Refresh Final Gameplay Evidence

**Files:**
- Create: `docs/superpowers/evidence/2026-06-28-full-codex-evidence-gate-refresh.md`
- Modify: `docs/final-gameplay-runbook.md`
- Modify: `docs/project-completion-checklist.md`

- [x] **Step 1: Run no-confirmation final gameplay**

  Run:

  ```sh
  CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS=90000 CRAFTLESS_FABRIC_SMOKE_ACTION_TIMEOUT_MS=120000 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 mise exec -- gradle :driver-fabric:fabricFinalGameplay
  ```

  Expected: Gradle task exits `0`.

- [x] **Step 2: Inspect required artifacts**

  Verify non-empty artifacts for server evidence, connected OpenAPI/actions
  and resources, SSE events, runtime metadata, gameplay results, public-agent
  results, and public-agent state.

- [x] **Step 3: Inspect gameplay outcome**

  Confirm public-agent evidence includes generated action usage for material
  collection/crafting, Wooden Sword creation/equip, Cow perception,
  `entity.attack`, Cow `alive:false`, and dropped `Raw Beef`/`Leather`.

- [x] **Step 4: Fix runbook wording**

  Update optional human observation docs so macOS ready prompts are described
  as opt-in through `CRAFTLESS_FABRIC_SMOKE_READY_COMMAND_JSON`, not injected
  by default.

### Task 4: Verify, Commit, Push, And Monitor

**Files:**
- Commit all Phase 68 files and docs.

- [x] **Step 1: Run final verification**

  Run:

  ```sh
  git diff --check
  mise run architecture-check
  mise run ci
  ```

- [ ] **Step 2: Commit and push**

  Run:

  ```sh
  git add AGENTS.md docs/final-gameplay-runbook.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-68-full-codex-evidence-gate-refresh-design.md docs/superpowers/plans/2026-06-28-68-full-codex-evidence-gate-refresh-plan.md docs/superpowers/evidence/2026-06-28-full-codex-evidence-gate-refresh.md
  git commit -m "docs: refresh codex evidence gate"
  git push origin main
  ```

- [ ] **Step 3: Verify remote CI**

  Run:

  ```sh
  gh run list --repo minekube/craftless --branch main --limit 5 --json databaseId,headSha,status,conclusion,name,event,createdAt
  gh run watch <latest-run-id> --repo minekube/craftless --exit-status
  ```
