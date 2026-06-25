# Craftwright

Real Minecraft Java client automation for tests, agents, and CI.

Craftwright is an early-stage project for launching and controlling real
Minecraft Java clients through a local API and short CLI. The goal is to make
Minecraft integration tests and AI/player agents run against real servers such
as Minekube Gate, Connect, Paper, Velocity, and vanilla-compatible servers.

Craftwright is not Mineflayer and is not a protocol-only bot. The project goal
is automation of the real Minecraft client process.

## Status

Craftwright is in a design-pivot phase.

The current checked-in Go implementation is prototype and legacy material. It
is still useful as reference for CLI shape, JSON output contracts, scenario
ergonomics, and daemon ideas, but it is no longer the target architecture.

The intended implementation direction is a Kotlin/JVM project with:

- a short scriptable CLI, currently `mcw` unless renamed separately;
- a local supervisor/API for client sessions;
- a temporary HeadlessMC/HMC-Specifics bridge backend for Phase 1 evidence;
- a real Fabric driver as the durable automation engine;
- generated OpenAPI for the running client/session;
- TypeScript SDK and Playwright/Vitest-style testing integrations.

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

- create the Kotlin/JVM Gradle project skeleton;
- keep or replace the legacy Go prototype intentionally;
- implement the CLI and local API surface;
- add a temporary HeadlessMC/HMC-Specifics bridge backend;
- add a real integration smoke test that launches a real client, joins a
  server, sends chat, moves forward, and verifies server-side position changed;
- keep public API names independent from HMC-Specifics command strings.

Phase 2:

- build the first `driver-fabric-*` module;
- move movement/look/perception from simulated keypresses to direct client
  APIs;
- expose `/openapi.json` from the running client/session;
- add structured endpoints such as `/player`, `/player/position`,
  `/player/move`, `/player/look`, `/player/raycast`,
  `/world/blocks/nearby`, `/entities/nearby`, `/screen`, and `/events`.

Later:

- TypeScript SDK;
- Playwright and Vitest fixtures;
- compatibility rows for older Minecraft versions;
- optional PrismLauncher import/adapter work.

## Design Docs

Current docs:

- `docs/superpowers/specs/2026-06-25-jvm-first-rewrite-design.md`
- `docs/superpowers/specs/2026-06-25-client-management-decisions.md`
- `docs/superpowers/specs/2026-06-25-generated-client-api-design.md`
- `docs/superpowers/prompts/2026-06-25-jvm-generated-api-agent-prompt.md`

Legacy reference:

- `docs/superpowers/specs/2026-06-24-craftwright-design.md`
- `docs/superpowers/specs/2026-06-24-mcw-cli-design.md`

## Development

Current legacy Go prototype:

```sh
go test ./... -count=1
```

The next implementation should add JVM/Kotlin verification commands alongside
or instead of this legacy command.
