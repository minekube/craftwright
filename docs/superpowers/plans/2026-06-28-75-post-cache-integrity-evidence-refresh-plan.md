# Post-Cache-Integrity Evidence Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Record current completion-gate evidence after Phases 73 and 74 changed cache integrity behavior.

**Architecture:** Treat this as an evidence refresh only. Use the existing distribution tasks, installer, Docker image, compatibility probes, final gameplay harness, generated public API/SSE evidence, and CI gates; do not add public gameplay breadth or Minecraft support claims.

**Tech Stack:** mise, Gradle, Bun, Docker, GitHub Actions, Markdown evidence docs.

---

### Task 1: Refresh Distribution Evidence

**Files:**
- Create: `docs/superpowers/evidence/2026-06-28-post-cache-integrity-evidence-refresh.md`
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
  tmp="$(mktemp -d /tmp/craftless-cli-smoke.XXXXXX)" && build/docker/craftless/bin/craftless server start --once --port 0 --workspace "$tmp/workspace"
  ```

  Expected: JSON output contains `"ok":true`, a localhost `url`, and the temp
  workspace path.

- [x] **Step 3: Build Docker image**

  Run:

  ```sh
  docker build -t craftless:local .
  ```

  Expected: Docker image build succeeds from the copied
  `build/docker/craftless/` CLI distribution.

- [x] **Step 4: Smoke-run Docker image**

  Run:

  ```sh
  docker run --rm craftless:local /opt/craftless/bin/craftless server start --once --port 0 --workspace /tmp/craftless
  ```

  Expected: JSON output contains `"ok":true`.

- [x] **Step 5: Smoke-run installer**

  Run:

  ```sh
  tmp="$(mktemp -d /tmp/craftless-install-smoke.XXXXXX)" && CRAFTLESS_VERSION=v0.1.0 CRAFTLESS_INSTALL_DIR="$tmp/bin" CRAFTLESS_HOME="$tmp/home" ./install.sh && "$tmp/bin/craftless" server start --once --port 0 --workspace "$tmp/workspace"
  ```

  Expected: installer reports `craftless 0.1.0 installed`, and installed
  binary returns `"ok":true`.

### Task 2: Refresh Compatibility Evidence

**Files:**
- Create: `docs/superpowers/evidence/2026-06-28-post-cache-integrity-evidence-refresh.md`
- Modify: `docs/project-completion-checklist.md`

- [x] **Step 1: Fetch live Mojang metadata**

  Run:

  ```sh
  mise exec -- bun -e '
  const manifest = await (await fetch("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")).json();
  const release = manifest.versions.find((version) => version.id === manifest.latest.release);
  const snapshot = manifest.versions.find((version) => version.id === manifest.latest.snapshot);
  const older = manifest.versions.find((version) => version.id === "1.20.6");
  if (!release || !snapshot || !older) throw new Error("missing expected manifest entry");
  const releaseMeta = await (await fetch(release.url)).json();
  const olderMeta = await (await fetch(older.url)).json();
  console.log(JSON.stringify({
    latest: manifest.latest,
    latestRelease: { id: release.id, type: release.type, releaseTime: release.releaseTime, time: release.time, sha1: release.sha1, javaVersion: releaseMeta.javaVersion },
    latestSnapshot: { id: snapshot.id, type: snapshot.type, releaseTime: snapshot.releaseTime, time: snapshot.time, sha1: snapshot.sha1 },
    representativeOlder: { id: older.id, type: older.type, releaseTime: older.releaseTime, time: older.time, sha1: older.sha1, javaVersion: olderMeta.javaVersion },
  }, null, 2));
  '
  ```

  Expected: latest release `26.2` with Java 25, latest snapshot
  `26.3-snapshot-1`, and representative older release `1.20.6` with Java 21.

- [x] **Step 2: Run matrix and probe tests**

  Run:

  ```sh
  mise exec -- gradle :driver-fabric:test --tests '*FabricCompatibilityMatrixTest*' --tests '*FabricCapabilityProbeTest.runtime metadata probe emits sanitized compatibility lane evidence*' :testkit:test --tests '*LocalMinecraftServerSmokeTest.local server smoke records unsupported runtime lane without provisioning server*'
  ```

  Expected: `BUILD SUCCESSFUL`.

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
- Create: `docs/superpowers/evidence/2026-06-28-post-cache-integrity-evidence-refresh.md`
- Modify: `docs/project-completion-checklist.md`

- [x] **Step 1: Run no-confirmation final gameplay**

  Run:

  ```sh
  CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS=90000 CRAFTLESS_FABRIC_SMOKE_ACTION_TIMEOUT_MS=120000 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 mise exec -- gradle :driver-fabric:fabricFinalGameplay
  ```

  Expected: Gradle task exits `0`.

- [x] **Step 2: Inspect required artifacts**

  Run:

  ```sh
  for file in server-evidence.jsonl client-openapi-connected.json client-actions-connected.json client-resources-connected.json client-events.jsonl client-events-stream.sse gameplay-results.jsonl public-agent-gameplay-results.jsonl public-agent-state.jsonl runtime-metadata.json; do test -s "driver-fabric/build/craftless-final-gameplay/artifacts/$file" || exit 1; done
  ```

  Expected: command exits `0`.

- [x] **Step 3: Inspect gameplay outcome**

  Run:

  ```sh
  rg -n '"public-agent-blocked"|task\\.survival|find\\.tree|mine\\.log|craft\\.sword|kill\\.cow' driver-fabric/build/craftless-final-gameplay/artifacts/public-agent-gameplay-results.jsonl driver-fabric/build/craftless-final-gameplay/artifacts/public-agent-state.jsonl
  ```

  Expected: no matches and exit code `1`.

  Then run:

  ```sh
  rg -n 'Wooden Sword|entity\\.attack|Raw Beef|Leather|Raw Chicken|Feather|Raw Mutton|White Wool|alive":false|public-agent-complete' driver-fabric/build/craftless-final-gameplay/artifacts/public-agent-gameplay-results.jsonl driver-fabric/build/craftless-final-gameplay/artifacts/public-agent-state.jsonl
  ```

  Expected: matches proving generated combat, weapon, and loot evidence.

### Task 4: Record Evidence And Verify

**Files:**
- Create: `docs/superpowers/evidence/2026-06-28-post-cache-integrity-evidence-refresh.md`
- Modify: `docs/project-completion-checklist.md`
- Modify: `docs/superpowers/plans/2026-06-28-75-post-cache-integrity-evidence-refresh-plan.md`

- [x] **Step 1: Write evidence summary**

  Update the evidence file with the exact command outputs, artifact names,
  compatibility lane result, final gameplay outcome, and statement that no
  public gameplay API or support claim was added.

- [x] **Step 2: Update checklist**

  Mark Phase 75 evidence items checked only for gates that passed in this run.

- [x] **Step 3: Run final local verification**

  Run:

  ```sh
  git diff --check
  mise run architecture-check
  mise run ci
  ```

  Expected: all commands exit `0`.

- [ ] **Step 4: Commit and push**

  Run:

  ```sh
  git add AGENTS.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-75-post-cache-integrity-evidence-refresh-design.md docs/superpowers/plans/2026-06-28-75-post-cache-integrity-evidence-refresh-plan.md docs/superpowers/evidence/2026-06-28-post-cache-integrity-evidence-refresh.md
  git commit -m "docs: refresh evidence after cache integrity"
  git push origin main
  ```

- [ ] **Step 5: Verify remote CI**

  Run:

  ```sh
  gh run list --repo minekube/craftless --branch main --limit 5 --json databaseId,headSha,status,conclusion,name,event,createdAt
  gh run watch <latest-run-id> --repo minekube/craftless --exit-status
  ```

  Expected: pushed commit's GitHub Actions CI exits successfully.

### Guardrails

- [x] No public gameplay action, generated route family, CLI gameplay catalog,
  Fabric descriptor/binding pair, scenario shortcut, new compiled lane, public
  version-specific API, or new Minecraft support claim is added.
- [x] Unsupported `26.2`, `26.3-snapshot-1`, and `1.20.6` evidence remains
  unsupported evidence, not support.
