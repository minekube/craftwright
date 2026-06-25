# Craftwright

Real Minecraft Java client automation for tests, agents, and CI.

Craftwright is an early-stage project for launching and controlling real
Minecraft Java clients through a local API and short CLI. The goal is to make
Minecraft integration tests and AI/player agents run against real servers such
as Minekube Gate, Connect, Paper, Velocity, and vanilla-compatible servers.

Craftwright is not Mineflayer and is not a protocol-only bot. The project goal
is automation of the real Minecraft client process.

## Status

Craftwright is a Kotlin/JVM-first project with one implementation direction:

- a short scriptable CLI, currently `mcw` unless renamed separately;
- a local supervisor/API for client sessions;
- a stable JVM `driver-api` contract with a fake implementation for daemon and
  route integration and runtime action metadata;
- a `driver-runtime` adapter layer that can run `DriverSession` over bridge or
  Fabric-style backends without changing daemon routes;
- a compiled `driver-fabric-1_21_6` Fabric/Loom module with client entrypoint,
  mod metadata, mixin config, and a gateway-backed runtime backend for
  client-thread connect, chat, command, stop, player state, and player position
  observation plus generated `player.move` and `player.chat` action
  invocation;
- a temporary HeadlessMC/HMC-Specifics bridge backend for Phase 1 evidence;
- a real Fabric driver implementation as the durable automation engine;
- stable kernel OpenAPI at `/openapi.json` plus per-client OpenAPI at
  `/clients/{id}/openapi.json` with Craftwright metadata and discovered
  action schemas;
- Playwright helper tests.

## Evidence

A throwaway real-client PoC was built under `/tmp/craftwright-real-client-poc`.
It proved the core loop:

1. start a local offline Paper 1.21.4 server;
2. launch a real Minecraft Java client through HeadlessMC;
3. load Fabric 1.21.4 and HMC-Specifics 2.4.0;
4. expose a local HTTP API wrapper;
5. connect the real client to the server;
6. send chat and move the player through API calls;
7. verify join, chat, and position change from the server.

Observed server evidence:

```text
CwApiBot joined the game
<CwApiBot> api action after reconnect
CwApiBot has the following entity data: [-5.5d, -60.0d, 10.914621337840606d]
```

This was a bridge PoC. It used HMC-Specifics commands behind a
Craftwright-shaped API. That is good enough to prove the launch/control loop,
but not good enough as the final product driver.

The final driver should be a Fabric mod that directly implements movement,
look direction, raycasts, nearby block/entity perception, inventory, screen
state, interactions, chat, and lifecycle events from inside the Minecraft
client.

## Roadmap

Phase 1:

- extend the Kotlin/JVM Gradle project skeleton;
- implement the CLI and local API surface;
- route daemon-created clients through an injectable driver runtime boundary;
- keep the first Fabric 1.21.6 module compiling under Loom;
- route Fabric connect, chat, command, stop, player state, player position
  observation, and generated `player.move`/`player.chat` action invocation
  through a real Minecraft client gateway;
- expose `/clients/{id}/openapi.json` with runtime metadata and discovered
  action schemas while avoiding static hand-written action route expansion;
- prefer AIP-style action invocation such as `POST /clients/{id}:run` and
  generated aliases such as `POST /clients/{id}/player:move`;
- accept action invocation arguments as typed JSON values such as
  `{"action":"player.move","args":{"forward":true,"ticks":20}}` and
  `{"action":"player.chat","args":{"message":"hello"}}`;
- add a temporary HeadlessMC/HMC-Specifics bridge backend;
- add a real integration smoke test that launches a real client, joins a
  server, sends chat, moves forward, and verifies server-side position changed;
- keep public API names independent from HMC-Specifics command strings.

Phase 2:

- expand generated actions for look/perception beyond the current
  `player.move` movement intent;
- move more runtime/version/mod/registry inputs into per-client OpenAPI
  fingerprints;
- expose stable roots such as `/player`, `/world`, `/screen`, and `/events`,
  with generated per-client action schemas for movement, look, raycast,
  inventory, world/entity queries, and screen interaction.

Later:

- expand Playwright and Vitest fixtures;
- compatibility rows for additional Minecraft versions;
- optional PrismLauncher import/adapter work.

## Design Docs

Current docs:

- `docs/superpowers/specs/2026-06-25-jvm-first-rewrite-design.md`
- `docs/superpowers/specs/2026-06-25-client-management-decisions.md`
- `docs/superpowers/specs/2026-06-25-generated-client-api-design.md`
- `docs/superpowers/specs/2026-06-25-driver-api-contract.md`
- `docs/superpowers/plans/2026-06-25-jvm-generated-api-foundation.md`
- `docs/bridge-limitations.md`
- `docs/agent-skills.md`

## Development

Install and run pinned tools through `mise`:

```sh
mise install
mise run ci
mise exec -- gradle test
```

Use Bun for Playwright helper tests:

```sh
mise exec -- bun test playwright
```
