# Public Agent Gameplay Design

## Problem

The internal survival task harness found real gaps, but it is the wrong durable
completion boundary if it becomes the only way to finish gameplay. Craftless is
supposed to expose a clean generated API and event stream that external agents,
CLI tools, and generated clients can compose.

Final gameplay must therefore prove that an agent outside the Fabric driver can
fetch the live per-client OpenAPI/actions/resources, subscribe to events, and
complete an ordinary survival scenario without new scenario-specific public
Kotlin actions.

## Goals

- Provide an external public-agent gameplay runner that uses HTTP/SSE and the
  generated per-client OpenAPI/action descriptors.
- Provide agent-facing repository skills/docs that explain how to discover,
  invoke, stream, cache, and validate Craftless actions.
- Expose missing generic primitives through runtime graph projection, not
  hard-coded scenario routes.
- Keep final artifacts machine-readable enough to prove what happened:
  discovery, action calls, event stream, inventory changes, block changes,
  entity observations, and final loot/build evidence.

## Non-Goals

- Do not make `task.survival.*` a durable public API.
- Do not add `find.tree`, `craft.sword`, `kill.cow`, or similar scenario
  shortcuts.
- Do not grant items, teleport the player, or use server commands as
  completion evidence.
- Do not require a generated Kotlin SDK or TypeScript SDK for the final proof.

## Required Public-Agent Flow

1. Start or attach a real Minecraft Java client through the supervisor API.
2. Fetch `GET /openapi.json`.
3. Fetch `GET /clients/{id}/openapi.json`, `/clients/{id}/actions`, and
   resource/handle/event projections.
4. Subscribe to `/clients/{id}/events:stream`.
5. Invoke generic discovered actions through `POST /clients/{id}:run`.
6. Maintain local state from query results and SSE events.
7. Compose survival behavior:
   - observe player/world/entity/inventory state;
   - locate reachable material handles or block targets;
   - navigate using generated navigation actions;
   - mine using generated block/action primitives;
   - craft/equip using generated inventory/screen primitives;
   - locate a passive entity;
   - navigate/aim/attack through generic primitives;
   - verify loot or other world-state evidence through queries/events.
8. Write artifacts and hold the session for Robin's chat confirmation.

## Generic Primitive Requirements

The runner may only use Craftless-owned generated actions. If the runner needs
an operation that does not exist, the implementation must improve the runtime
graph/projection/adapter layer. Examples of acceptable generic primitives:

- `entity.query` with live handles, positions, categories, and alive state.
- `world.block.query` or equivalent resource projection for nearby blocks.
- `navigation.plan`, `navigation.follow`, and progress events with completion.
- `player.look` and `player.raycast`.
- `world.block.break` by handle/position or raycast target.
- `inventory.query`, `inventory.equip`, and generic screen/crafting actions.
- SSE events for action results, inventory changes, entity changes, and task
  progress where available.

## Agent Skill Requirements

Repository agent skills should teach agents:

- always fetch the live per-client OpenAPI before gameplay;
- cache specs only by runtime fingerprint;
- prefer handles and schemas from the current client;
- listen to SSE while acting;
- validate success from state changes, not accepted action calls;
- never rely on server-provisioned inventory or scenario shortcuts;
- report blockers as missing generic primitives.

## Completion Evidence

Completion requires all of:

- `client-openapi-connected.json`;
- `client-actions-connected.json`;
- event stream capture;
- public-agent action log;
- public-agent state log;
- inventory evidence showing legitimately obtained materials/tool/loot;
- server evidence showing chat and Robin confirmation;
- passing `mise run lint`, `mise run architecture-check`, and `mise run ci`.
