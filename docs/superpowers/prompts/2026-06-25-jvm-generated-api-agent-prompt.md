# Agent Prompt: Craftwright JVM Rewrite With Generated Client API

You are taking over Craftwright after a design pivot. Work from the repository
root.

## Context To Read First

Read these files before planning or changing code:

1. `docs/superpowers/specs/2026-06-25-jvm-first-rewrite-design.md`
2. `docs/superpowers/specs/2026-06-25-generated-client-api-design.md`
3. `docs/superpowers/specs/2026-06-25-client-management-decisions.md`
4. `docs/superpowers/specs/2026-06-24-mcw-cli-design.md`

Treat older Go-first and HeadlessMC-wrapper plans as legacy reference only.
They are useful for CLI UX, JSON output discipline, test ergonomics, and prior
research, but they are not the target architecture.

If an older referenced plan is missing or stale, do not recreate it. Continue
from the three 2026-06-25 specs above.

## Product Goal

Build Craftwright into an open source automation platform for real Minecraft
Java clients.

The finished project should let CI jobs, test suites, scripts, and AI agents
launch real Minecraft clients, connect them to Minekube software such as Gate
or Connect, and control/observe them through a short CLI, generated OpenAPI,
typed SDKs, and Playwright/Vitest-style test integrations.

The project must not become Mineflayer. It must automate the real Minecraft
client process.

## Architecture Direction

Use a JVM-first rewrite.

- Kotlin is the default language for product code.
- Java is the default language for Fabric Mixins, accessor interfaces, and
  bytecode-sensitive Minecraft glue.
- TypeScript is the first external SDK and Playwright/Vitest integration
  surface.
- The existing Go implementation is legacy/prototype material unless a tiny
  compatibility wrapper is explicitly kept.
- HeadlessMC, HMC-Specifics, PrismLauncher, MC-Runtime-Test, Fabric, and
  NeoForge are research/reference projects, not automatically the public API.

## Current Evidence

The design pivot is backed by a real-client bridge PoC at:

`/tmp/craftwright-real-client-poc`

That PoC proved:

1. A local Paper 1.21.4 server can run in offline mode.
2. A real Minecraft Java client can launch through HeadlessMC.
3. Fabric 1.21.4 and HMC-Specifics 2.4.0 load inside the client.
4. A local HTTP API wrapper can drive the client.
5. The API can connect the client to the server.
6. The server sees `CwApiBot` join.
7. The API can send chat, capture rendered text, inspect/click UI, and move the
   player after the client is in-game.

Observed server evidence:

```text
CwApiBot joined the game
<CwApiBot> api action after reconnect
CwApiBot has the following entity data: [-5.5d, -60.0d, 10.914621337840606d]
```

This is a bridge PoC, not the final product driver. It used HMC-Specifics
commands under a Craftwright-shaped API. Treat it as proof that the
supervisor/API loop is viable, not as the final abstraction.

Important PoC findings:

- Minecraft usernames must be 16 characters or fewer.
- First-run screens and title screens can swallow simulated keyboard movement.
- HMC-Specifics `key` commands are useful for evidence but fragile as a product
  movement API.
- Structured movement, look direction, raycast, block/entity perception,
  inventory, screen, and interaction APIs belong in a Craftwright Fabric driver.

## First Build Target

The first build target is:

1. Create the Kotlin/JVM project skeleton.
2. Add the short CLI entrypoint.
3. Add a local daemon/API module with Craftwright-shaped routes.
4. Add a temporary HeadlessMC/HMC-Specifics bridge backend.
5. Keep public API names independent from HMC-Specifics command strings.
6. Add a real integration smoke test:
   - start a local offline Paper server;
   - launch one offline-mode real Minecraft Java client;
   - connect through the Craftwright API;
   - verify server join;
   - send chat through the Craftwright API;
   - move forward through the Craftwright API;
   - verify server position changed.
7. Document all bridge limitations.

The next major milestone after this skeleton is the real Fabric driver.

The Fabric driver target is:

1. Load an in-client driver into one Fabric client.
2. Start or connect to a local structured protocol/API.
3. Generate `/openapi.json` from the running client/session.
4. Provide short useful endpoints such as `/version`, `/client`, `/player`,
   `/connection`, `/events`, `/player/move`, `/player/look`,
   `/player/raycast`, `/world/blocks/nearby`, and `/entities/nearby`.
5. Implement movement, look, raycast, and perception directly inside the
   client, not through simulated keypresses.

Do not count a protocol-only bot, Mineflayer, or a fake Java object as the real
milestone. Fakes are allowed for unit tests and route-generation tests, but the
milestone requires a real Minecraft client.

## Generated API Direction

OpenAPI is the canonical machine contract for the running client session.

Required shape:

- `GET /openapi.json`
- `GET /version`
- `GET /events`
- `GET /client`
- `GET /client/state`
- `GET /player`
- `GET /player/name`
- `POST /player/sendChat`
- `GET /connection`
- object fallback routes under `/o/{handle}`
- class metadata routes under `/c/{className}`

Near-term Craftwright-shaped control/perception routes:

- `POST /clients/{id}/connect`
- `POST /clients/{id}/stop`
- `POST /clients/{id}/actions/chat`
- `POST /clients/{id}/actions/move`
- `POST /clients/{id}/actions/jump`
- `POST /clients/{id}/actions/look`
- `GET /clients/{id}/player`
- `GET /clients/{id}/player/position`
- `GET /clients/{id}/perception/raycast`
- `GET /clients/{id}/world/blocks/nearby`
- `GET /clients/{id}/entities/nearby`
- `GET /clients/{id}/events`

Also design the supervisor/session API for reusable client setup:

- available Minecraft versions;
- available loader choices, initially Fabric-first;
- local server target selection;
- offline profile names;
- future auth profiles;
- cached client installations;
- created client sessions;
- connect/disconnect lifecycle;
- artifacts and event logs.

Prefer short paths for high-use API operations. Avoid repeating long prefixes
such as `/mc/client/...` when the local API is already scoped to one Minecraft
client session.

## Kotlin/JVM Stack Preference

Use current stable ecosystem versions at implementation time.

Preferred stack:

- Gradle Kotlin DSL.
- Kotlin for supervisor, protocol, generator, CLI, tests, and SDK-generation
  logic.
- Java for Mixins/accessors.
- Fabric Loom for the first in-client driver.
- Fabric Language Kotlin only if Kotlin must run inside the Fabric client.
- Ktor Server/OpenAPI if it is clean inside the Fabric/Minecraft classpath.
- Java `com.sun.net.httpserver.HttpServer` as an acceptable first driver-server
  fallback if Ktor causes classpath problems.
- OkHttp for JVM HTTP client tests and SDK/client code.
- `kotlinx.serialization` for JSON and OpenAPI/protocol models.
- Coroutines for lifecycle, event streams, and client-thread scheduling.
- Clikt and Mordant for the JVM `mcw` CLI.
- JUnit 5 plus Kotest for JVM tests.

## CLI Direction

Keep the CLI short and scriptable.

The canonical command should remain `mcw` unless the project is explicitly
renamed. Do not rename the project during Phase 1 unless there is a separate
decision.

CLI requirements:

- command tree designed before implementation;
- `--help` everywhere;
- stdout for primary data;
- stderr for progress/diagnostics;
- `--json` and `--jsonl` for machine output;
- clear exit codes;
- `--no-input` for CI;
- no hidden interactive prompts in CI mode.

Useful early commands:

- `mcw versions`
- `mcw profiles`
- `mcw clients create`
- `mcw clients list`
- `mcw clients connect`
- `mcw clients api`
- `mcw server start`
- `mcw test run`

## Implementation Task

Create or update a Superpowers implementation plan at:

`docs/superpowers/plans/2026-06-25-jvm-generated-api-foundation.md`

Then implement Phase 1 from that plan unless the repository state makes that
unsafe.

The plan and implementation should get from the current repository to the first
real-client bridge-backed Craftwright API milestone. Include:

1. Repository migration plan from Go prototype to Gradle/Kotlin/JVM structure.
2. Test-first route/OpenAPI generator work using fake Minecraft objects.
3. Client/profile/version/session model.
4. Minimal local Minecraft server fixture strategy.
5. Temporary HeadlessMC/HMC-Specifics bridge backend.
6. Fabric driver spike plan.
7. In-client API server plan.
8. Real offline client launch plan.
9. Real client connect-to-server smoke test.
10. TypeScript SDK and Playwright/Vitest integration plan.
11. CLI command design and output contracts.
12. Risks, blockers, and research checkpoints.
13. Exact verification commands for every phase.

The first implementation may use the bridge backend as long as:

- public routes remain Craftwright-owned;
- HMC command strings do not leak into the public API;
- all bridge limitations are documented;
- the next milestone remains a Fabric driver with direct client APIs.

## Do Not Do Yet

- Do not build Prism integration in Phase 1.
- Do not use Mineflayer.
- Do not rename the project unless there is a separate explicit decision.
- Do not expose HeadlessMC or HMC-Specifics command names as the public UX.
- Do not claim robust player movement until a Fabric driver implements movement
  intent directly inside the client.

## Fabric Driver Next Milestone

After the bridge-backed skeleton works, build `driver-fabric-*`.

Minimum Fabric driver capabilities:

- emits `client.ready`;
- connects/disconnects through real Minecraft APIs;
- sends and observes chat;
- returns player position;
- moves the player forward and verifies position changed;
- jumps or sets jump intent;
- sets yaw/pitch and returns the updated look direction;
- returns raycast target or explicit no-target;
- returns bounded nearby-block and nearby-entity samples;
- exposes current screen and screen click;
- stops cleanly with logs and structured events.

## Research Rules

When blocked, research with source evidence instead of guessing.

Clone or inspect relevant open source projects in temporary directories when
needed, especially:

- HeadlessMC
- HMC-Specifics
- MC-Runtime-Test
- PrismLauncher
- Fabric examples and Fabric Loom docs
- NeoForge examples for future compatibility
- Playwright/Vitest fixture patterns

Do not cargo-cult any project. Extract the pattern, cite the file or behavior,
and decide whether it fits Craftwright.

## Proof Of Concept Expectations

Before claiming the bridge-backed Phase 1 milestone, prove all of this:

1. A local Minecraft server starts.
2. A real Minecraft Java client launches in offline mode.
3. The Craftwright local API starts.
4. API calls tell the real client to connect, chat, and move.
5. The server logs or query state show the player joined.
6. The server logs show chat.
7. Server position query shows movement changed position.
8. Artifacts are written for logs, events, OpenAPI/API description, and session
   metadata.

Before claiming the final Fabric-driver milestone, additionally prove:

1. The in-client Fabric driver starts.
2. Movement and look are implemented directly through Minecraft client APIs.
3. Raycast and nearby block/entity perception return structured data.
4. `/openapi.json` returns operations from that running session.

If any part is simulated, label it as simulated and keep it out of the real
milestone definition.

## Design Values

- Real Minecraft client first.
- Local and sandboxed before productized.
- Generated OpenAPI as the agent-readable contract.
- Short high-use routes plus reflective/object fallback.
- Strong CLI discipline from `clig.dev`.
- Test-first implementation.
- Evidence before completion claims.
- Compatibility with both latest Minecraft and older versions over time.

## Done Definition For This Agent Handoff

The handoff is complete when:

1. The implementation plan exists or is updated.
2. The plan references the current JVM, generated API, and client-management
   specs.
3. Old plans are explicitly treated as legacy.
4. Phase 1 skeleton exists or blockers are documented with source evidence.
5. The first phase has runnable verification commands.
6. The first real milestone cannot be confused with a fake client or protocol
   bot.
7. Open questions are written as research checkpoints, not blockers requiring
   immediate user feedback.
