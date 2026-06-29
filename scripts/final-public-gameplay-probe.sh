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
CHAT_MESSAGE="${CRAFTLESS_FINAL_GAMEPLAY_CHAT_MESSAGE:-Craftless CL-07 public gameplay probe}"
MINECRAFT_VERSION="${CRAFTLESS_FINAL_GAMEPLAY_MINECRAFT_VERSION:-${CRAFTLESS_SMOKE_MINECRAFT_VERSION:-1.21.6}}"
API="http://127.0.0.1:$DAEMON_PORT"

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

"$CRAFTLESS_BIN" daemon start --port "$DAEMON_PORT" --workspace "$WORKSPACE" \
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
  --version "$MINECRAFT_VERSION" \
  --loader fabric \
  --loader-version 0.19.3 \
  --offline-name FinalPublic \
  > "$ARTIFACTS_DIR/clients-create.log" 2>&1

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
  > "$ARTIFACTS_DIR/clients-connect.log" 2>&1

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
  "navigation.plan",
  "navigation.follow",
  "entity.query",
  "entity.attack",
];
while (Date.now() < deadline) {
  const response = await fetch(`${api}/clients/${clientId}/openapi.json`);
  if (response.ok) {
    const text = await response.text();
    const openapi = JSON.parse(text);
    const actions = Array.isArray(openapi["x-craftless-actions"]) ? openapi["x-craftless-actions"] : [];
    const resources = Array.isArray(openapi["x-craftless-resources"]) ? openapi["x-craftless-resources"] : [];
    const byId = new Map(actions.map((action) => [action.id, action]));
    const missing = required.find((id) => !byId.has(id) || byId.get(id).availability !== "available");
    await fs.writeFile(path.join(artifactsDir, "client-openapi-connected.json"), `${text}\n`);
    await fs.writeFile(path.join(artifactsDir, "client-actions.json"), `${JSON.stringify(actions, null, 2)}\n`);
    await fs.writeFile(path.join(artifactsDir, "client-resources.json"), `${JSON.stringify(resources, null, 2)}\n`);
    if (!missing) {
      await fs.rm(path.join(artifactsDir, "missing-generic-primitive.txt"), { force: true });
      process.exit(0);
    }
    await fs.writeFile(path.join(artifactsDir, "missing-generic-primitive.txt"), `missing-generic-primitive:${missing}\n`);
  }
  await new Promise((resolve) => setTimeout(resolve, 500));
}
console.error("missing-generic-primitive:connected-openapi");
process.exit(1);
'

"$CRAFTLESS_BIN" clients "$CLIENT_ID" openapi --api "$API" > "$ARTIFACTS_DIR/client-openapi-cli.json"
"$CRAFTLESS_BIN" clients "$CLIENT_ID" actions --api "$API" > "$ARTIFACTS_DIR/client-actions-cli.json"
"$CRAFTLESS_BIN" clients "$CLIENT_ID" resources --api "$API" > "$ARTIFACTS_DIR/client-resources-cli.json"
"$CRAFTLESS_BIN" clients "$CLIENT_ID" events --api "$API" > "$ARTIFACTS_DIR/client-events-stream.sse"
"$CRAFTLESS_BIN" clients "$CLIENT_ID" query openapi --api "$API" > "$ARTIFACTS_DIR/client-rpc-openapi.json"
"$CRAFTLESS_BIN" clients "$CLIENT_ID" query actions --api "$API" > "$ARTIFACTS_DIR/client-rpc-actions.json"
"$CRAFTLESS_BIN" clients "$CLIENT_ID" query resources --api "$API" > "$ARTIFACTS_DIR/client-rpc-resources.json"

API="$API" CLIENT_ID="$CLIENT_ID" ARTIFACTS_DIR="$ARTIFACTS_DIR" CHAT_MESSAGE="$CHAT_MESSAGE" MINECRAFT_VERSION="$MINECRAFT_VERSION" mise exec -- bun --eval '
const fs = await import("node:fs/promises");
const path = await import("node:path");
const api = process.env.API;
const clientId = process.env.CLIENT_ID;
const artifactsDir = process.env.ARTIFACTS_DIR;
const chatMessage = process.env.CHAT_MESSAGE;
const minecraftVersion = process.env.MINECRAFT_VERSION;
const actionLogPath = path.join(artifactsDir, "public-agent-actions.jsonl");
const stateLogPath = path.join(artifactsDir, "public-agent-state.jsonl");

await fs.writeFile(actionLogPath, "");
await fs.writeFile(stateLogPath, "");

function missing(id) {
  return new Error(`missing-generic-primitive:${id}`);
}

function jsonFingerprint(value) {
  return JSON.stringify(value);
}

function resultData(result) {
  return result?.result?.data ?? {};
}

function resultStatus(result) {
  return result?.result?.status;
}

function resultAccepted(result) {
  return resultStatus(result) === "ACCEPTED";
}

async function appendJsonl(file, value) {
  await fs.appendFile(file, `${JSON.stringify({ at: new Date().toISOString(), ...value })}\n`);
}

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
  return JSON.parse(text);
}

async function invoke(action, args = {}) {
  const payload = {
    jsonrpc: "2.0",
    id: `invoke:${action}:${Date.now()}`,
    method: "invoke",
    params: { action, args },
  };
  const body = await rpc(payload);
  await appendJsonl(actionLogPath, { action, args, result: body.result });
  return body;
}

async function queryState(action, label, args = {}) {
  const body = await invoke(action, args);
  if (!resultAccepted(body)) {
    throw new Error(`state query ${action} was not accepted`);
  }
  await appendJsonl(stateLogPath, { label, action, data: resultData(body) });
  return resultData(body);
}

function assertAccepted(action, body) {
  if (!resultAccepted(body)) {
    throw new Error(`${action} was not accepted: ${JSON.stringify(body)}`);
  }
}

function slots(inventory) {
  return Array.isArray(inventory?.slots) ? inventory.slots : [];
}

function firstOccupiedHotbarSlot(inventory) {
  return slots(inventory).find((slot) => slot.slot >= 0 && slot.slot <= 8 && slot.empty === false);
}

function point(position) {
  if (!position || typeof position !== "object") {
    return null;
  }
  const x = Number(position.x);
  const y = Number(position.y);
  const z = Number(position.z);
  return Number.isFinite(x) && Number.isFinite(y) && Number.isFinite(z) ? { x, y, z } : null;
}

function center(position) {
  const p = point(position);
  return p ? { x: p.x + 0.5, y: p.y + 0.5, z: p.z + 0.5 } : null;
}

function distance(left, right) {
  const a = point(left);
  const b = point(right);
  if (!a || !b) {
    return Number.POSITIVE_INFINITY;
  }
  return Math.hypot(a.x - b.x, a.y - b.y, a.z - b.z);
}

function navigationSucceeded(body) {
  const data = resultData(body);
  const state = String(data.state ?? "").toLowerCase();
  return resultAccepted(body) && state === "succeeded";
}

function planId(body) {
  const data = resultData(body);
  return data["plan-id"] ?? data.id ?? null;
}

async function navigateTo(position, radius) {
  const plan = await invoke("navigation.plan", {
    goal: {
      kind: "block",
      position,
      radius,
    },
  });
  const id = planId(plan);
  if (!id) {
    return "navigation.plan";
  }
  const follow = await invoke("navigation.follow", { plan: { id } });
  if (navigationSucceeded(follow)) {
    return null;
  }
  const player = await queryState("player.query", "player-after-navigation-check");
  if (distance(player.position, position) <= radius) {
    return null;
  }
  return "navigation.follow.succeeded";
}

function materialDropPosition(entities) {
  return entities
    .filter((entity) => entity?.position)
    .filter((entity) => String(entity.label ?? "").toLowerCase().includes("log"))
    .sort((left, right) => Number(left.distance ?? Number.POSITIVE_INFINITY) - Number(right.distance ?? Number.POSITIVE_INFINITY))[0]
    ?.position ?? null;
}

function verticalDelta(entity, origin) {
  const entityPosition = point(entity?.position);
  const originPosition = point(origin);
  if (!entityPosition || !originPosition) {
    return Number.POSITIVE_INFINITY;
  }
  return Math.abs(entityPosition.y - originPosition.y);
}

function selectBlock(blocks) {
  return blocks
    .filter((block) => block?.handle && block.replaceable !== true && block.category !== "air")
    .map((block) => ({
      ...block,
      exposedFaces: Array.isArray(block.faces)
        ? block.faces.filter((face) => face?.replaceable === true && face?.["occupied-by-player"] !== true).length
        : 0,
    }))
    .sort((left, right) => {
      if (left.exposedFaces !== right.exposedFaces) {
        return right.exposedFaces - left.exposedFaces;
      }
      return Number(left.distance) - Number(right.distance);
    })[0];
}

function selectEntity(entities, origin) {
  return entities
    .filter((entity) => entity?.handle && entity?.position && entity.alive !== false)
    .filter((entity) => entity.category !== "object")
    .map((entity) => ({ ...entity, verticalDelta: verticalDelta(entity, origin) }))
    .filter((entity) => entity.verticalDelta <= 12)
    .sort((left, right) => {
      if (left.verticalDelta !== right.verticalDelta) {
        return left.verticalDelta - right.verticalDelta;
      }
      return Number(left.distance) - Number(right.distance);
    })[0];
}

function selectAttackableEntity(entities) {
  return entities
    .filter((entity) => entity?.handle && entity.alive !== false)
    .filter((entity) => entity.category !== "object")
    .filter((entity) => Number(entity.distance) <= 4.5)
    .sort((left, right) => Number(left.distance) - Number(right.distance))[0];
}

function selectCraftableRecipe(recipes) {
  const craftable = recipes.filter((candidate) => candidate?.handle && candidate.craftable === true);
  const craftingRecipe = craftable.find((candidate) => String(candidate.kind ?? "").includes("crafting"));
  return craftingRecipe ?? craftable[0];
}

async function subscribe() {
  const response = await rpc({
    jsonrpc: "2.0",
    id: "subscribe-final-public-gameplay",
    method: "subscribe",
    params: {
      filter: {
        types: ["client.connected", "client.event", "action.result", "player.chat"],
      },
    },
  });
  await fs.writeFile(path.join(artifactsDir, "client-rpc-subscribe.json"), `${JSON.stringify(response, null, 2)}\n`);
  return response.result?.subscriptionId;
}

async function captureSubscription(subscriptionId) {
  if (!subscriptionId) {
    return;
  }
  const stream = await fetch(`${api}/clients/${clientId}/events:stream?subscriptionId=${encodeURIComponent(subscriptionId)}`);
  await fs.writeFile(path.join(artifactsDir, "client-events-subscription-stream.sse"), `${await stream.text()}\n`);
  const subscriptions = await rpc({
    jsonrpc: "2.0",
    id: "query-subscriptions",
    method: "query",
    params: { target: "subscriptions" },
  });
  await fs.writeFile(path.join(artifactsDir, "client-rpc-subscriptions.json"), `${JSON.stringify(subscriptions, null, 2)}\n`);
  const unsubscribe = await rpc({
    jsonrpc: "2.0",
    id: "unsubscribe-final-public-gameplay",
    method: "unsubscribe",
    params: { subscriptionId },
  });
  await fs.writeFile(path.join(artifactsDir, "client-rpc-unsubscribe.json"), `${JSON.stringify(unsubscribe, null, 2)}\n`);
}

const openapi = JSON.parse(await fs.readFile(path.join(artifactsDir, "client-openapi-connected.json"), "utf8"));
const actions = Array.isArray(openapi["x-craftless-actions"]) ? openapi["x-craftless-actions"] : [];
if (actions.some((action) => typeof action.id === "string" && action.id.startsWith("task."))) {
  throw new Error("static scenario action appeared in final public gameplay OpenAPI");
}

const subscriptionId = await subscribe();
const beforePlayer = await queryState("player.query", "player-before");
const beforeInventory = await queryState("inventory.query", "inventory-before");

const chat = await invoke("player.chat", { message: chatMessage });
assertAccepted("player.chat", chat);

let blockQuery = await invoke("world.block.query", { radius: 64, limit: 64, category: "log" });
assertAccepted("world.block.query", blockQuery);
let block = selectBlock(resultData(blockQuery).blocks ?? []);
if (!block) {
  blockQuery = await invoke("world.block.query", { radius: 32, limit: 64, category: "collectable" });
  assertAccepted("world.block.query", blockQuery);
  block = selectBlock(resultData(blockQuery).blocks ?? []);
}
if (!block) {
  throw missing("world.block.query.target");
}

await appendJsonl(stateLogPath, { label: "selected-block", block });
const blockPosition = block.position;
if (!blockPosition) {
  throw missing("world.block.query.position");
}
const materialNavigationBlocker = await navigateTo(blockPosition, 2.25);
if (materialNavigationBlocker) {
  throw missing(materialNavigationBlocker);
}
const blockBreak = await invoke("world.block.break", {
  target: { handle: block.handle },
  "max-distance": 6,
  ticks: 240,
});
assertAccepted("world.block.break", blockBreak);
if (resultData(blockBreak).changed !== true) {
  throw missing("world.block.break.changed");
}

let afterBreakInventory = null;
let inventoryChangedAfterBreak = false;
let pickupBlocker = null;
for (let attempt = 1; attempt <= 8; attempt += 1) {
  const entityDropQuery = await invoke("entity.query", { radius: 16, limit: 32 });
  assertAccepted("entity.query", entityDropQuery);
  const dropPosition = materialDropPosition(resultData(entityDropQuery).entities ?? []);
  if (dropPosition) {
    const dropNavigationBlocker = await navigateTo(dropPosition, 1.0);
    pickupBlocker = dropNavigationBlocker;
  } else {
    pickupBlocker = await navigateTo(blockPosition, 1.25);
  }
  await new Promise((resolve) => setTimeout(resolve, 500));
  afterBreakInventory = await queryState("inventory.query", `inventory-after-block-break-${attempt}`);
  inventoryChangedAfterBreak = jsonFingerprint(beforeInventory) !== jsonFingerprint(afterBreakInventory);
  if (inventoryChangedAfterBreak) {
    break;
  }
}
if (!inventoryChangedAfterBreak) {
  throw missing(pickupBlocker ?? "inventory.query.changed-after-world-action");
}

let recipe = null;
for (let attempt = 1; attempt <= 12; attempt += 1) {
  const recipeQuery = await invoke("recipe.query", { craftable: true, limit: 32 });
  assertAccepted("recipe.query", recipeQuery);
  const recipes = resultData(recipeQuery).recipes ?? [];
  await appendJsonl(stateLogPath, {
    label: "recipe-query-after-material",
    attempt,
    count: resultData(recipeQuery).count ?? recipes.length,
  });
  recipe = selectCraftableRecipe(recipes);
  if (recipe) {
    break;
  }
  await new Promise((resolve) => setTimeout(resolve, 500));
}
if (!recipe) {
  throw missing("recipe.query.craftable");
}
await appendJsonl(stateLogPath, { label: "selected-recipe", recipe });

const craft = await invoke("recipe.craft", { target: { handle: recipe.handle }, count: 1 });
assertAccepted("recipe.craft", craft);
const craftData = resultData(craft);
if (craftData.accepted !== true || (craftData.changed !== true && Number(craftData["crafted-count"] ?? 0) <= 0)) {
  throw missing("recipe.craft.changed");
}

const afterCraftInventory = await queryState("inventory.query", "inventory-after-craft");
const equipSlot = firstOccupiedHotbarSlot(afterCraftInventory);
if (!equipSlot) {
  throw missing("inventory.query.hotbar-item");
}
const equip = await invoke("inventory.equip", { slot: equipSlot.slot });
assertAccepted("inventory.equip", equip);
const afterEquipPlayer = await queryState("player.query", "player-after-equip");
if (afterEquipPlayer["selected-slot"] !== equipSlot.slot) {
  throw missing("inventory.equip.selected-slot");
}

const interact = await invoke("world.block.interact", {
  target: { handle: block.handle },
  "max-distance": 6,
  side: "up",
});
assertAccepted("world.block.interact", interact);

const entitySearchPlayer = await queryState("player.query", "entity-search-player");
let entity = null;
let sawEntityTarget = false;
let entityNavigationBlocker = null;
for (let attempt = 1; attempt <= 4 && !entity; attempt += 1) {
  const searchPlayer =
    attempt === 1 ? entitySearchPlayer : await queryState("player.query", `entity-search-player-${attempt}`);
  const entityQuery = await invoke("entity.query", { radius: attempt === 1 ? 64 : 16, limit: 32 });
  assertAccepted("entity.query", entityQuery);
  const discoveredEntity = selectEntity(resultData(entityQuery).entities ?? [], searchPlayer.position);
  await appendJsonl(stateLogPath, {
    label: "entity-query-target-attempt",
    attempt,
    count: resultData(entityQuery).count ?? 0,
    selected: discoveredEntity
      ? {
          handle: discoveredEntity.handle,
          category: discoveredEntity.category,
          distance: discoveredEntity.distance,
          position: discoveredEntity.position,
        }
      : null,
  });
  if (!discoveredEntity?.position) {
    await new Promise((resolve) => setTimeout(resolve, 500));
    continue;
  }
  sawEntityTarget = true;
  entityNavigationBlocker = await navigateTo(discoveredEntity.position, 1.5);

  const nearEntityQuery = await invoke("entity.query", { radius: 8, limit: 32 });
  assertAccepted("entity.query", nearEntityQuery);
  entity = selectAttackableEntity(resultData(nearEntityQuery).entities ?? []);
  if (entity) {
    break;
  }

  const nextEntity = selectEntity(resultData(nearEntityQuery).entities ?? [], discoveredEntity.position);
  if (nextEntity?.position) {
    entityNavigationBlocker = await navigateTo(nextEntity.position, 1.25);
    const attackRangeQuery = await invoke("entity.query", { radius: 5, limit: 32 });
    assertAccepted("entity.query", attackRangeQuery);
    entity = selectAttackableEntity(resultData(attackRangeQuery).entities ?? []);
  }
  await new Promise((resolve) => setTimeout(resolve, 500));
}
if (!sawEntityTarget) {
  throw missing("entity.query.target");
}
if (!entity) {
  if (entityNavigationBlocker) {
    throw missing(entityNavigationBlocker);
  }
  throw missing("entity.query.attackable-target");
}
await appendJsonl(stateLogPath, { label: "selected-entity", entity });
const attack = await invoke("entity.attack", { target: { handle: entity.handle }, "max-distance": 4.5 });
assertAccepted("entity.attack", attack);
if (resultData(attack).hit !== true) {
  throw missing("entity.attack.hit");
}

const finalInventory = await queryState("inventory.query", "inventory-final");
const finalPlayer = await queryState("player.query", "player-final");
await captureSubscription(subscriptionId);

const summary = {
  status: "passed",
  api,
  clientId,
  minecraftVersion,
  openapiActionCount: actions.length,
  chatVerified: resultAccepted(chat),
  stateObserved: Boolean(beforePlayer && beforeInventory && finalPlayer && finalInventory),
  blockChangedOrItemPickedUp: resultData(blockBreak).changed === true && inventoryChangedAfterBreak,
  craftedAndEquipped: craftData.accepted === true && afterEquipPlayer["selected-slot"] === equipSlot.slot,
  entityInteracted: resultData(attack).hit === true,
  usedTaskAction: false,
  usedServerProvisioning: false,
  selectedBlock: block.handle,
  selectedRecipe: recipe.handle,
  equippedSlot: equipSlot.slot,
  selectedEntity: entity.handle,
};
await fs.writeFile(path.join(artifactsDir, "final-gameplay-summary.json"), `${JSON.stringify(summary, null, 2)}\n`);
'
