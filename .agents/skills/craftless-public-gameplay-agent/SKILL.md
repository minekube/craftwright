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

## Client Lifecycle Discipline

`craftless clients create` launches a new daemon-managed real Minecraft Java
client process, and `POST /clients` has the same lifecycle effect. They are
not a selector, retry, or reuse operation. Before creating in an existing
daemon or workspace, list clients with `GET /clients` or
`craftless clients list --api "$CRAFTLESS"`, then inspect likely matches with
`GET /clients/{id}` or
`craftless clients <id> get --api "$CRAFTLESS"`.

Reuse a suitable existing client when possible. If a previous attempt is yours
or clearly abandoned, stop it first with `POST /clients/{id}:stop` or
`craftless clients <id> stop --api "$CRAFTLESS"`. Creating fresh timestamped
ids for retries leaves multiple Minecraft clients running. Use fresh unique
ids only for deliberate independent clients, such as a two-client co-play test.

## Tiny-Agent Bootstrap

Use the smallest lifecycle request that can create an API-first automation
client. Omit `profile` unless a specific offline name matters; Craftless
derives one from the client id. The default presentation requests no visible
window and materializes muted Minecraft sound options.

```sh
craftless clients create bot --version latest-release --loader fabric --api "$CRAFTLESS"
craftless clients bot connect --host localhost --port 25565 --api "$CRAFTLESS"
craftless clients bot openapi --api "$CRAFTLESS" > bot-openapi.json
```

For co-play, only ask Craftless to manage the human-facing client when a
visible Craftless-launched window is desired:

```sh
craftless clients create robin --version latest-release --loader fabric \
  --offline-name Robin --visible --audio default --api "$CRAFTLESS"
craftless clients robin connect --host localhost --port 25565 --api "$CRAFTLESS"
```

If the human joins with their own launcher, skip the visible Craftless client.
Coordinate through Minecraft chat and public Craftless events. Presentation
window flags are lifecycle hints, not gameplay authority; still fetch the bot
client's generated OpenAPI before choosing any gameplay action.

## Fresh State Gate

Before reporting status, diagnosing another agent, or claiming completion,
refresh live state in the same turn. Do not answer from old Codex transcript
state, old artifacts, old process ids, or another agent's earlier status
message without re-checking.

Check at least:

- daemon health with `GET /version`;
- client inventory with `GET /clients` and `GET /clients/{id}`;
- relevant client history with `GET /clients/{id}/events`;
- active streams when the claim depends on events, with
  `GET /clients/{id}/events:stream`;
- listener/process state for daemon and server ports with `lsof` and `ps`;
- server or client logs when the claim depends on join, disconnect, kicked,
  timeout, combat, pickup, or movement behavior.

Use fresh unique client ids only for deliberate independent clients. If old
test clients are still running, stop them through `POST /clients/{id}:stop` or
`craftless clients <id> stop --api "$CRAFTLESS"` when they are yours or clearly
abandoned. Report stale clients by exact id and current observed state. Do not
say a client is still joining, connected, or opening a window unless the fresh
API/process check proves it.

## Adaptive CLI Sequence

The CLI is an adaptive consumer of the same live metadata. Use it when shelling
out is easier than writing HTTP calls, but do not treat CLI aliases as a static
catalog.

Discover live actions and resources:

```sh
craftless clients <id> actions --api "$CRAFTLESS"
craftless clients <id> resources --api "$CRAFTLESS"
```

Invoke through the generic generated-action path:

```sh
craftless clients <id> run <action> --api "$CRAFTLESS" --arg key=value
```

Generated alias help may be used only after fetching the live OpenAPI. If a
CLI alias is unavailable, fall back to `craftless clients <id> run <action>`
rather than inventing a new static command.

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

## JSON-RPC And SSE

Use POST JSON-RPC-style control/query requests when the supervisor or client
API exposes them. Treat the POST response as an acknowledgement or query
result; use `GET /clients/{id}/events:stream` as the durable observation path
for correlated lifecycle, action, perception, and error events.

Subscribe before state-changing actions when possible. Keep enough SSE bytes or
decoded JSONL evidence to prove which generated action produced which observed
state change.

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
- `public-agent-state.jsonl`;
- final inventory/world/entity evidence.

Completion does not require a human Minecraft chat confirmation. Treat human
co-play as optional diagnostic observation; the final gate is Codex-verifiable
public API/CLI evidence without server-provisioned inventory or scenario
shortcuts.
