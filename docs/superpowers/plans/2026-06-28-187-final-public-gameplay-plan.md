# Final Public Gameplay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove honest survival gameplay through Craftless public generated API/CLI only.

**Architecture:** Add a rerunnable final gameplay probe that acts like an external user: package Craftless, start a real server/client smoke environment, start the packaged supervisor, create/connect the client through public CLI/API, discover generated OpenAPI, and invoke only generated operations. The probe records public state before and after each action and fails with explicit missing-primitive evidence instead of adding scenario shortcuts.

**Tech Stack:** Kotlin/JVM Craftless CLI and daemon, Fabric client smoke harness for real server lifecycle, Bash orchestration, Bun for JSON/OpenAPI parsing and HTTP/SSE calls, mise tasks.

---

## Files

- Create: `scripts/final-public-gameplay-probe.sh`
- Modify: `.mise.toml`
- Modify: `playwright/src/distribution.test.ts`
- Create: `docs/superpowers/evidence/2026-06-28-final-public-gameplay.md`
- Modify: `docs/project-completion-checklist.md`
- Modify: `docs/superpowers/phase-index.md`

## Task 1: Guard The Public Gameplay Probe Surface

**Files:**
- Modify: `playwright/src/distribution.test.ts`

- [ ] **Step 1: Add failing distribution guard**

Add a test to the `distribution surface` suite:

```ts
test("final public gameplay probe uses generated public surfaces only", () => {
  const mise = read(".mise.toml");
  const script = read("scripts/final-public-gameplay-probe.sh");

  expect(mise).toContain("[tasks.final-public-gameplay-probe]");
  expect(mise).toContain("$PWD/scripts/final-public-gameplay-probe.sh");
  expect(script).toContain("GET /clients/{id}/openapi.json authority");
  expect(script).toContain("missing-generic-primitive:");
  expect(script).toContain("player.chat");
  expect(script).toContain("inventory.query");
  expect(script).toContain("world.block.break");
  expect(script).toContain("recipe.craft");
  expect(script).toContain("entity.attack");
  expect(script).not.toContain("task.survival");
  expect(script).not.toContain("kill.cow");
  expect(script).not.toContain("find.tree");
  expect(script).not.toContain("craft.sword");
  expect(script).not.toContain("/give");
});
```

- [ ] **Step 2: Verify red**

Run:

```sh
mise exec -- bun test playwright/src/distribution.test.ts
```

Expected: fail because `scripts/final-public-gameplay-probe.sh` and the mise
task do not exist yet.

## Task 2: Add The Final Gameplay Probe Script

**Files:**
- Create: `scripts/final-public-gameplay-probe.sh`

- [ ] **Step 1: Create script header and environment contract**

Create `scripts/final-public-gameplay-probe.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

# GET /clients/{id}/openapi.json authority: this probe discovers gameplay from
# the live connected client OpenAPI and refuses static scenario shortcuts.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
CRAFTLESS_BIN="$ROOT/build/docker/craftless/bin/craftless"
ARTIFACTS_DIR="${CRAFTLESS_SMOKE_ARTIFACTS_DIR:-$ROOT/driver-fabric/build/craftless-final-gameplay/artifacts}"
WORKSPACE="${CRAFTLESS_FINAL_GAMEPLAY_WORKSPACE:-$ROOT/driver-fabric/build/craftless-final-gameplay/workspace}"
CLIENT_ID="${CRAFTLESS_FINAL_GAMEPLAY_CLIENT_ID:-final-public-gameplay}"
SERVER_PORT="${CRAFTLESS_SMOKE_SERVER_PORT:?CRAFTLESS_SMOKE_SERVER_PORT is required}"
DAEMON_PORT="${CRAFTLESS_FINAL_GAMEPLAY_DAEMON_PORT:-18087}"
TIMEOUT_MS="${CRAFTLESS_FINAL_GAMEPLAY_TIMEOUT_MS:-900000}"
API="http://127.0.0.1:$DAEMON_PORT"

mkdir -p "$ARTIFACTS_DIR" "$WORKSPACE"
test -x "$CRAFTLESS_BIN"
```

- [ ] **Step 2: Add cleanup and daemon startup**

Append:

```bash
DAEMON_PID=""

cleanup() {
  set +e
  if [ -n "$DAEMON_PID" ]; then
    "$CRAFTLESS_BIN" clients "$CLIENT_ID" stop --api "$API" > "$ARTIFACTS_DIR/client-stop.log" 2>&1
    kill "$DAEMON_PID" >/dev/null 2>&1
    wait "$DAEMON_PID" >/dev/null 2>&1
  fi
}
trap cleanup EXIT

"$CRAFTLESS_BIN" server start --port "$DAEMON_PORT" --workspace "$WORKSPACE" \
  > "$ARTIFACTS_DIR/packaged-daemon.log" 2>&1 &
DAEMON_PID="$!"
```

- [ ] **Step 3: Add Bun helper that waits for supervisor OpenAPI**

Append:

```bash
API="$API" ARTIFACTS_DIR="$ARTIFACTS_DIR" TIMEOUT_MS="$TIMEOUT_MS" mise exec -- bun --eval '
const fs = await import("node:fs/promises");
const path = await import("node:path");
const api = process.env.API;
const artifactsDir = process.env.ARTIFACTS_DIR;
const deadline = Date.now() + Number(process.env.TIMEOUT_MS);
while (Date.now() < deadline) {
  try {
    const response = await fetch(`${api}/openapi.json`);
    if (response.ok) {
      await fs.writeFile(path.join(artifactsDir, "supervisor-openapi.json"), `${await response.text()}\n`);
      process.exit(0);
    }
  } catch (_) {}
  await new Promise((resolve) => setTimeout(resolve, 500));
}
console.error(`timed out waiting for supervisor at ${api}`);
process.exit(1);
'
```

- [ ] **Step 4: Create and connect the client through packaged CLI**

Append:

```bash
CRAFTLESS_HTTP_REQUEST_TIMEOUT_MS="$TIMEOUT_MS" \
  "$CRAFTLESS_BIN" clients create "$CLIENT_ID" \
  --api "$API" \
  --version 1.20.6 \
  --loader fabric \
  --loader-version 0.19.3 \
  --offline-name FinalPublic \
  > "$ARTIFACTS_DIR/clients-create.log" 2>&1

"$CRAFTLESS_BIN" clients "$CLIENT_ID" connect \
  --api "$API" \
  --host 127.0.0.1 \
  --port "$SERVER_PORT" \
  > "$ARTIFACTS_DIR/clients-connect.log" 2>&1
```

- [ ] **Step 5: Add generated OpenAPI capture and primitive preflight**

Append a Bun block that waits for connected OpenAPI, writes
`client-openapi-connected.json`, `client-actions.json`, `client-resources.json`,
and exits with `missing-generic-primitive:<id>` if any required non-`task.*`
operation is missing or unavailable:

```bash
API="$API" CLIENT_ID="$CLIENT_ID" ARTIFACTS_DIR="$ARTIFACTS_DIR" TIMEOUT_MS="$TIMEOUT_MS" mise exec -- bun --eval '
const fs = await import("node:fs/promises");
const path = await import("node:path");
const api = process.env.API;
const clientId = process.env.CLIENT_ID;
const artifactsDir = process.env.ARTIFACTS_DIR;
const deadline = Date.now() + Number(process.env.TIMEOUT_MS);
const required = [
  "player.chat",
  "player.query",
  "player.raycast",
  "inventory.query",
  "inventory.equip",
  "recipe.query",
  "recipe.craft",
  "world.block.query",
  "world.block.break",
  "world.block.interact",
  "entity.query",
  "entity.attack",
];
while (Date.now() < deadline) {
  const response = await fetch(`${api}/clients/${clientId}/openapi.json`);
  if (response.ok) {
    const text = await response.text();
    const openapi = JSON.parse(text);
    const actions = Array.isArray(openapi["x-craftless-actions"]) ? openapi["x-craftless-actions"] : [];
    const byId = new Map(actions.map((action) => [action.id, action]));
    const missing = required.find((id) => !byId.has(id) || byId.get(id).availability !== "available");
    await fs.writeFile(path.join(artifactsDir, "client-openapi-connected.json"), `${text}\n`);
    await fs.writeFile(path.join(artifactsDir, "client-actions.json"), `${JSON.stringify(actions, null, 2)}\n`);
    const resources = Array.isArray(openapi["x-craftless-resources"]) ? openapi["x-craftless-resources"] : [];
    await fs.writeFile(path.join(artifactsDir, "client-resources.json"), `${JSON.stringify(resources, null, 2)}\n`);
    if (!missing) process.exit(0);
    await fs.writeFile(path.join(artifactsDir, "missing-generic-primitive.txt"), `missing-generic-primitive:${missing}\n`);
  }
  await new Promise((resolve) => setTimeout(resolve, 500));
}
console.error("missing-generic-primitive:connected-openapi");
process.exit(1);
'
```

## Task 3: Implement Public Gameplay Sequence

**Files:**
- Modify: `scripts/final-public-gameplay-probe.sh`

- [ ] **Step 1: Add public JSON-RPC invocation helper and state log**

Append a Bun block that defines `invoke(action, args)`, writes every request
and response to `public-agent-actions.jsonl`, and writes state snapshots to
`public-agent-state.jsonl`.

- [ ] **Step 2: Chat and initial state**

Invoke:

```json
{"action":"player.chat","args":{"message":"Craftless CL-07 public gameplay probe starting"}}
```

Then invoke `player.query`, `inventory.query`, `world.block.query` with
`{"radius":5,"limit":32}`, and `entity.query` with `{"radius":16,"limit":32}`.
Write all results to `public-agent-state.jsonl`.

- [ ] **Step 3: Break a queried block and prove pickup or block state change**

Select the first non-air block target returned by `world.block.query` that has
a handle or position object. Invoke:

```json
{"action":"world.block.break","args":{"target":{"handle":"<handle-from-query>"},"ticks":80}}
```

If the query returns position but no handle, invoke:

```json
{"action":"world.block.break","args":{"target":{"position":{"x":0,"y":0,"z":0}},"ticks":80}}
```

using the discovered position. Then re-run `inventory.query` and
`world.block.query`. Pass this step only if inventory count changes, a picked-up
item appears, or the target block state changes in public query output.

- [ ] **Step 4: Craft and equip an item**

Invoke `recipe.query`:

```json
{"action":"recipe.query","args":{"craftable":true,"limit":16}}
```

Select the first craftable recipe handle from the result. Invoke:

```json
{"action":"recipe.craft","args":{"target":{"handle":"<recipe-handle>"},"count":1}}
```

Run `inventory.query`, select a slot containing the crafted item, then invoke:

```json
{"action":"inventory.equip","args":{"slot":0}}
```

using the discovered slot. Pass this step only if inventory or selected-slot
state proves the craft/equip result.

- [ ] **Step 5: Interact with or attack an entity**

From `entity.query`, select a non-player living entity with a handle. Invoke:

```json
{"action":"entity.attack","args":{"target":{"handle":"<entity-handle>"},"max-distance":4.5}}
```

If no entity is within range, write
`missing-generic-primitive:navigation-or-entity-target` and fail rather than
teleporting, spawning, or using a server command. Pass this step only if the
attack result or subsequent `entity.query` proves hit/death/state change.

- [ ] **Step 6: Capture stream and server artifacts**

Capture:

```sh
"$CRAFTLESS_BIN" clients "$CLIENT_ID" events --api "$API" > "$ARTIFACTS_DIR/client-events-stream.sse"
"$CRAFTLESS_BIN" clients "$CLIENT_ID" query actions --api "$API" > "$ARTIFACTS_DIR/client-rpc-actions.json"
"$CRAFTLESS_BIN" clients "$CLIENT_ID" query resources --api "$API" > "$ARTIFACTS_DIR/client-rpc-resources.json"
```

Write `final-gameplay-summary.json` with boolean fields for:

- `chatVerified`
- `stateObserved`
- `blockChangedOrItemPickedUp`
- `craftedAndEquipped`
- `entityInteracted`
- `usedTaskAction`
- `usedServerProvisioning`

The script exits `0` only when all required booleans are true and both
forbidden booleans are false.

## Task 4: Add Mise Task And Run Probe

**Files:**
- Modify: `.mise.toml`

- [ ] **Step 1: Add task**

Add:

```toml
[tasks.final-public-gameplay-probe]
description = "Run final honest public gameplay through generated Craftless API/CLI only"
run = [
    "mise run package-cli",
    "rm -rf driver-fabric/build/craftless-final-gameplay",
    "CRAFTLESS_LOCAL_SERVER_SMOKE=1 CRAFTLESS_LOCAL_SERVER_SMOKE_ROOT=build/craftless-final-gameplay CRAFTLESS_SMOKE_MINECRAFT_VERSION=1.20.6 CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS=900000 CRAFTLESS_FINAL_GAMEPLAY_TIMEOUT_MS=900000 CRAFTLESS_SMOKE_ACTION_COMMAND_JSON=\"[\\\"$PWD/scripts/final-public-gameplay-probe.sh\\\"]\" mise exec -- gradle :driver-fabric:fabricClientSmoke",
]
```

- [ ] **Step 2: Verify guard green**

Run:

```sh
mise exec -- bun test playwright/src/distribution.test.ts
```

Expected: pass.

- [ ] **Step 3: Run final gameplay probe**

Run:

```sh
mise run final-public-gameplay-probe
```

Expected: exit `0` only if the probe writes
`driver-fabric/build/craftless-final-gameplay/artifacts/final-gameplay-summary.json`
with all required proof fields satisfied.

## Task 5: Evidence, Checklist, Commit

**Files:**
- Create: `docs/superpowers/evidence/2026-06-28-final-public-gameplay.md`
- Modify: `docs/project-completion-checklist.md`
- Modify: `docs/superpowers/phase-index.md`

- [ ] **Step 1: Write evidence**

Record:

- command output for `mise run final-public-gameplay-probe`;
- path to `final-gameplay-summary.json`;
- connected OpenAPI/action/resource artifact paths;
- public action JSONL path;
- state JSONL path;
- SSE/subscription artifact path;
- server log path;
- any missing primitive file if the run fails.

- [ ] **Step 2: Close CL-07 only if evidence proves every requirement**

Update CL-07 to `[x]` only when:

- no `task.*` action appears in `public-agent-actions.jsonl`;
- no server command or provisioning shortcut appears in the script or logs;
- `final-gameplay-summary.json` has all required proof booleans satisfied.

- [ ] **Step 3: Verification**

Run:

```sh
mise exec -- bun test playwright/src/distribution.test.ts
git diff --check
```

- [ ] **Step 4: Commit and push**

Run:

```sh
git add .mise.toml scripts/final-public-gameplay-probe.sh playwright/src/distribution.test.ts docs/project-completion-checklist.md docs/superpowers/phase-index.md docs/superpowers/evidence/2026-06-28-final-public-gameplay.md docs/superpowers/specs/2026-06-28-187-final-public-gameplay-design.md docs/superpowers/plans/2026-06-28-187-final-public-gameplay-plan.md
git commit -m "test: add final public gameplay gate"
git push origin main
```

## Self-Review

- Spec coverage: every CL-07 requirement maps to a probe artifact or summary
  field.
- Placeholder scan: no TBD/fill-in/later placeholders.
- Type consistency: action ids and argument names match the connected older
  lane generated action schemas inspected before writing this plan.
