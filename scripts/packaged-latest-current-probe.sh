#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
CRAFTLESS_BIN="$ROOT/build/docker/craftless/bin/craftless"
ARTIFACTS_DIR="${CRAFTLESS_SMOKE_ARTIFACTS_DIR:-$ROOT/build/craftless-packaged-latest-current-probe/artifacts}"
WORKSPACE="${CRAFTLESS_PACKAGED_LATEST_WORKSPACE:-$ROOT/build/craftless-packaged-latest-current-probe/workspace}"
CLIENT_ID="${CRAFTLESS_PACKAGED_LATEST_CLIENT_ID:-latest-current}"
SERVER_PORT="${CRAFTLESS_SMOKE_SERVER_PORT:?CRAFTLESS_SMOKE_SERVER_PORT is required}"
DAEMON_PORT="${CRAFTLESS_PACKAGED_LATEST_DAEMON_PORT:-18084}"
TIMEOUT_MS="${CRAFTLESS_PACKAGED_LATEST_TIMEOUT_MS:-300000}"
API="http://127.0.0.1:$DAEMON_PORT"
GENERATED_ACTION_ID=""

mkdir -p "$ARTIFACTS_DIR" "$WORKSPACE"
test -x "$CRAFTLESS_BIN"
cp "$ROOT/build/docker/craftless/driver-mods.json" "$ARTIFACTS_DIR/packaged-driver-mods.json"

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
console.error(`timed out waiting for packaged daemon at ${api}`);
process.exit(1);
'

CRAFTLESS_HTTP_REQUEST_TIMEOUT_MS="$TIMEOUT_MS" \
  "$CRAFTLESS_BIN" clients create "$CLIENT_ID" \
  --api "$API" \
  --version latest-release \
  --loader fabric \
  --offline-name LatestCurrent \
  > "$ARTIFACTS_DIR/clients-create-latest-release.log" 2>&1

API="$API" CLIENT_ID="$CLIENT_ID" TIMEOUT_MS="$TIMEOUT_MS" mise exec -- bun --eval '
const api = process.env.API;
const clientId = process.env.CLIENT_ID;
const deadline = Date.now() + Number(process.env.TIMEOUT_MS);
while (Date.now() < deadline) {
  const response = await fetch(`${api}/events`);
  if (response.ok) {
    const events = await response.json();
    if (events.some((event) => event.type === "client.attached" && event.client === clientId)) {
      process.exit(0);
    }
  }
  await new Promise((resolve) => setTimeout(resolve, 500));
}
console.error(`timed out waiting for client.attached for ${clientId}`);
process.exit(1);
'

"$CRAFTLESS_BIN" clients "$CLIENT_ID" connect \
  --api "$API" \
  --host 127.0.0.1 \
  --port "$SERVER_PORT" \
  > "$ARTIFACTS_DIR/clients-connect-latest-release.log" 2>&1

API="$API" CLIENT_ID="$CLIENT_ID" ARTIFACTS_DIR="$ARTIFACTS_DIR" TIMEOUT_MS="$TIMEOUT_MS" mise exec -- bun --eval '
const fs = await import("node:fs/promises");
const path = await import("node:path");
const api = process.env.API;
const clientId = process.env.CLIENT_ID;
const artifactsDir = process.env.ARTIFACTS_DIR;
const deadline = Date.now() + Number(process.env.TIMEOUT_MS);
const required = new Set(["client", "player", "inventory", "world"]);
function resourceIds(openapi) {
  const direct = Array.isArray(openapi["x-craftless-resources"]) ? openapi["x-craftless-resources"] : [];
  return direct.map((resource) => resource.id).filter(Boolean);
}
while (Date.now() < deadline) {
  const response = await fetch(`${api}/clients/${clientId}/openapi.json`);
  if (response.ok) {
    const text = await response.text();
    const openapi = JSON.parse(text);
    const ids = new Set(resourceIds(openapi));
    if ([...required].every((id) => ids.has(id))) {
      await fs.writeFile(path.join(artifactsDir, "client-openapi-connected.json"), `${text}\n`);
      process.exit(0);
    }
  }
  await new Promise((resolve) => setTimeout(resolve, 500));
}
console.error(`timed out waiting for connected generated OpenAPI for ${clientId}`);
process.exit(1);
'

"$CRAFTLESS_BIN" clients "$CLIENT_ID" openapi --api "$API" > "$ARTIFACTS_DIR/client-openapi-cli.json"
"$CRAFTLESS_BIN" clients "$CLIENT_ID" actions --api "$API" > "$ARTIFACTS_DIR/client-actions.json"
"$CRAFTLESS_BIN" clients "$CLIENT_ID" resources --api "$API" > "$ARTIFACTS_DIR/client-resources.json"
"$CRAFTLESS_BIN" clients "$CLIENT_ID" events --api "$API" > "$ARTIFACTS_DIR/client-events-stream.sse"
"$CRAFTLESS_BIN" clients "$CLIENT_ID" query openapi --api "$API" > "$ARTIFACTS_DIR/client-rpc-openapi.json"
"$CRAFTLESS_BIN" clients "$CLIENT_ID" query actions --api "$API" > "$ARTIFACTS_DIR/client-rpc-actions.json"
"$CRAFTLESS_BIN" clients "$CLIENT_ID" query resources --api "$API" > "$ARTIFACTS_DIR/client-rpc-resources.json"

GENERATED_ACTION_ID="$(
  API="$API" CLIENT_ID="$CLIENT_ID" ARTIFACTS_DIR="$ARTIFACTS_DIR" TIMEOUT_MS="$TIMEOUT_MS" mise exec -- bun --eval '
  const fs = await import("node:fs/promises");
  const path = await import("node:path");
  const api = process.env.API;
  const clientId = process.env.CLIENT_ID;
  const artifactsDir = process.env.ARTIFACTS_DIR;
  const deadline = Date.now() + Number(process.env.TIMEOUT_MS);
  function requiredArgumentNames(action) {
    const args = action.args && typeof action.args === "object" ? action.args : {};
    return Object.entries(args)
      .filter(([, schema]) => schema && schema.required === true)
      .map(([name]) => name);
  }
  while (Date.now() < deadline) {
    const response = await fetch(`${api}/clients/${clientId}/openapi.json`);
    if (response.ok) {
      const text = await response.text();
      const openapi = JSON.parse(text);
      const actions = Array.isArray(openapi["x-craftless-actions"]) ? openapi["x-craftless-actions"] : [];
      const selected = actions.find((action) => action.availability === "available" && requiredArgumentNames(action).length === 0);
      if (selected) {
        await fs.writeFile(path.join(artifactsDir, "client-generated-action-selected.json"), `${JSON.stringify(selected, null, 2)}\n`);
        process.stdout.write(selected.id);
        process.exit(0);
      }
      await fs.writeFile(
        path.join(artifactsDir, "client-generated-action-unavailable.json"),
        `${JSON.stringify(actions.map((action) => ({
          id: action.id,
          availability: action.availability,
          availabilityReason: action.availabilityReason,
          requiredArguments: requiredArgumentNames(action),
        })), null, 2)}\n`,
      );
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  console.error(`timed out waiting for an available generated no-argument action for ${clientId}`);
  process.exit(1);
  '
)"

API="$API" CLIENT_ID="$CLIENT_ID" GENERATED_ACTION_ID="$GENERATED_ACTION_ID" ARTIFACTS_DIR="$ARTIFACTS_DIR" mise exec -- bun --eval '
const fs = await import("node:fs/promises");
const path = await import("node:path");
const api = process.env.API;
const clientId = process.env.CLIENT_ID;
const actionId = process.env.GENERATED_ACTION_ID;
const artifactsDir = process.env.ARTIFACTS_DIR;
const payload = {
  jsonrpc: "2.0",
  id: `invoke-generated:${actionId}`,
  method: "invoke",
  params: { action: actionId, args: {} },
};
const response = await fetch(`${api}/clients/${clientId}:rpc`, {
  method: "POST",
  headers: { "content-type": "application/json" },
  body: JSON.stringify(payload),
});
const text = await response.text();
await fs.writeFile(path.join(artifactsDir, "client-rpc-invoke-generated.json"), `${text}\n`);
if (!response.ok) {
  throw new Error(text);
}
const body = JSON.parse(text);
if (body.result?.action !== actionId || body.result?.status !== "ACCEPTED") {
  throw new Error(`generated action ${actionId} did not return ACCEPTED: ${text}`);
}
'

"$CRAFTLESS_BIN" clients "$CLIENT_ID" run "$GENERATED_ACTION_ID" \
  --api "$API" \
  > "$ARTIFACTS_DIR/client-cli-invoke-generated.log" 2>&1

API="$API" CLIENT_ID="$CLIENT_ID" ARTIFACTS_DIR="$ARTIFACTS_DIR" mise exec -- bun --eval '
const fs = await import("node:fs/promises");
const path = await import("node:path");
const api = process.env.API;
const clientId = process.env.CLIENT_ID;
const artifactsDir = process.env.ARTIFACTS_DIR;
async function rpc(payload) {
  const response = await fetch(`${api}/clients/${clientId}:rpc`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(payload),
  });
  const text = await response.text();
  if (!response.ok) {
    throw new Error(text);
  }
  return text;
}
const subscribe = await rpc({
  jsonrpc: "2.0",
  id: "subscribe-client-connected",
  method: "subscribe",
  params: { filter: { types: ["client.connected"] } },
});
await fs.writeFile(path.join(artifactsDir, "client-rpc-subscribe.json"), `${subscribe}\n`);
const subscriptionId = JSON.parse(subscribe).result.subscriptionId;
const stream = await fetch(`${api}/clients/${clientId}/events:stream?subscriptionId=${encodeURIComponent(subscriptionId)}`);
await fs.writeFile(path.join(artifactsDir, "client-events-subscription-stream.sse"), `${await stream.text()}\n`);
const subscriptions = await rpc({ jsonrpc: "2.0", id: "query-subscriptions", method: "query", params: { target: "subscriptions" } });
await fs.writeFile(path.join(artifactsDir, "client-rpc-subscriptions.json"), `${subscriptions}\n`);
const unsubscribe = await rpc({ jsonrpc: "2.0", id: "unsubscribe-client-connected", method: "unsubscribe", params: { subscriptionId } });
await fs.writeFile(path.join(artifactsDir, "client-rpc-unsubscribe.json"), `${unsubscribe}\n`);
const after = await rpc({ jsonrpc: "2.0", id: "query-subscriptions-after", method: "query", params: { target: "subscriptions" } });
await fs.writeFile(path.join(artifactsDir, "client-rpc-subscriptions-after-unsubscribe.json"), `${after}\n`);
const actions = JSON.parse(await fs.readFile(path.join(artifactsDir, "client-actions.json"), "utf8"));
const resources = JSON.parse(await fs.readFile(path.join(artifactsDir, "client-resources.json"), "utf8"));
const openapi = JSON.parse(await fs.readFile(path.join(artifactsDir, "client-openapi-connected.json"), "utf8"));
const selectedAction = JSON.parse(await fs.readFile(path.join(artifactsDir, "client-generated-action-selected.json"), "utf8"));
const summary = {
  status: "connected",
  api,
  clientId,
  minecraftVersion: "latest-release",
  concreteLatestVersion: "26.2",
  generatedInvocationAction: selectedAction.id,
  actionCount: actions.length,
  resourceIds: resources.map((resource) => resource.id),
  openapiActionCount: Array.isArray(openapi["x-craftless-actions"]) ? openapi["x-craftless-actions"].length : 0,
  subscriptionId,
};
await fs.writeFile(path.join(artifactsDir, "packaged-probe-summary.json"), `${JSON.stringify(summary, null, 2)}\n`);
'
