---
name: craftless-public-gameplay-agent
description: Use when an agent must control a Craftless Minecraft client through the public generated API, adaptive CLI, or SSE/JSON-RPC streams to perform gameplay without hard-coded scenario shortcuts.
---

# Craftless Public Gameplay Agent

## Core Rule

Act like an external user of Craftless. Do not call Fabric internals, driver
internals, server commands, or diagnostic scenario tasks as the main solution.
Use the live generated Craftless API for the target client.

Forbidden as final gameplay proof:

- `task.survival.*`
- `kill.cow`
- `find.tree`
- `craft.sword`
- server-provisioned inventory
- manual operator movement for Craftless

## Discovery Sequence

1. Fetch `GET /openapi.json`.
2. Create/connect or select the target client.
3. Fetch `GET /clients/{id}/openapi.json`.
4. Fetch `GET /clients/{id}/actions`.
5. Fetch resource/handle/event projections when available.
6. Cache specs only by runtime fingerprint, including Minecraft version,
   loader, mods, mappings, registries, permissions, and action schema
   fingerprint.
7. Subscribe to `GET /clients/{id}/events:stream` before acting.

## Invocation Sequence

Invoke actions through `POST /clients/{id}:run` using the action ids, argument
schemas, handles, and result schemas discovered from the current client.

Prefer generic primitives:

- `entity.query`
- `inventory.query`
- `navigation.plan`
- `navigation.follow`
- `player.look`
- `player.raycast`
- `world.block.break`
- `world.block.interact`
- screen and inventory actions discovered from the live client

If a needed primitive is missing, report
`missing-generic-primitive:<action-or-resource>` and stop adding scenario
shortcuts. The fix belongs in runtime graph discovery, projection, invocation
adapters, event streaming, CLI, or docs.

## Navigation Shape

Do not guess action schemas from action names. Read the generated OpenAPI
schema for the current client before invoking `navigation.plan`.

For the current public block-position navigation shape, send a block goal:

```json
{
  "action": "navigation.plan",
  "arguments": {
    "goal": {
      "kind": "block",
      "position": { "x": 12, "y": 64, "z": -8 },
      "radius": 3.0
    }
  }
}
```

Use integer block coordinates for block goals. After `navigation.follow`, query
public player/entity/block state before assuming the client reached the target.

## Live Co-Play

When a human joins the server, communicate through Minecraft chat and public
Craftless events. Do not use local OS speech or desktop automation as part of
gameplay coordination.

Treat stop commands as commands, not substrings. Stop following only on a clear
standalone message such as `stop`, `stopp`, `halt`, or `stop following`.
Messages like "follow me until I say stop" are instructions to continue.

For follow behavior:

1. Re-query the target player with `entity.query`.
2. Re-query the controlled player with `player.query`.
3. Navigate or move only when distance exceeds the chosen threshold.
4. Keep emitting chat/status through generated public actions.
5. Stop only after a clear stop command or a failed public evidence check.

## Validation

Accepted action calls are not proof of success. Verify progress from state:

- inventory changes after mining, pickup, crafting, or combat;
- entity alive/dead state and loot after combat;
- block state changes after mining/placing;
- player position/rotation after navigation and look actions;
- correlated SSE events for invoked actions.

Write artifacts for final gameplay:

- connected OpenAPI and action/resource projections;
- SSE stream capture;
- public-agent action log;
- public-agent state log;
- final inventory/world/entity evidence.

Completion does not require a human Minecraft chat confirmation. Treat human
co-play as optional diagnostic observation; the final gate is Codex-verifiable
public API/CLI evidence without server-provisioned inventory or scenario
shortcuts.
