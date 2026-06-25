# Generated Client API Design

Date: 2026-06-25

## Purpose

This spec defines Craftwright's generated local Minecraft client API direction.
It builds on the JVM-first rewrite decision and the ecosystem research that
favored a Fabric-first driver for latest Minecraft plus later Forge/NeoForge
drivers for compatibility testing.

The goal is not a public hosted product API. The first target is CI and agent
automation where each Minecraft client runs in a local process, sandbox, or
container controlled by the test job. The API can therefore be powerful and
reflective, as long as it is local, ephemeral, reproducible, and well described
by OpenAPI.

## Decision

Craftwright should expose a generated per-session OpenAPI surface from the
running Minecraft client driver.

- `GET /openapi.json` describes the stable supervisor/kernel API.
- `GET /clients/{id}/openapi.json` is the source of truth for agents,
  generated SDKs, generated CLIs, and test integrations for one running client.
- Public per-client operations are exposed as generated action/resource routes
  below `/clients/{id}` such as `POST /clients/{id}:run` and aliases like
  `POST /clients/{id}/player:move` when advertised by that client's OpenAPI.
- Deep or unknown objects may be represented by opaque handle identifiers in
  response schemas, but handle routes are roadmap work and must not be exposed
  as a static kernel surface.
- Class and mapping metadata may appear as OpenAPI extensions and schemas, but
  raw JVM or Minecraft class names should not become public route contracts.
- The API is generated from live driver-discovered actions/resources, runtime
  metadata, mappings, registries, installed mods, permissions, and server
  features.
- The first implementation may be unsafe by design, because it runs locally in
  an isolated CI/session context.
- A curated stable API can be layered on later, but the generated API is the
  primary spike direction.

## Kotlin And JVM Stack Decision

Craftwright should use a Kotlin-first JVM stack for the generated API
implementation, with Java fallbacks where Minecraft classpath or Mixin behavior
demands it.

### Baseline

- Use the latest stable Kotlin release at implementation time, not EAP, RC, or
  beta releases unless a Minecraft/Fabric requirement forces it.
- Use Gradle Kotlin DSL.
- Use Fabric Loom for the first real client driver.
- Use Fabric Language Kotlin when Kotlin code runs inside a Fabric client mod.
  Its Maven metadata showed `1.13.12+kotlin.2.4.0` during research, which means
  the Fabric Kotlin ecosystem is tracking modern Kotlin releases.
- Keep low-level Mixins and bytecode-sensitive accessors in Java by default.

### HTTP Server

Preferred server stack:

- Ktor Server for the driver API server if it works cleanly in the Fabric client
  classpath.
- Ktor OpenAPI support for serving and generating OpenAPI metadata where useful.
  Ktor 3.5.0 documentation describes runtime OpenAPI specification generation
  and serving OpenAPI docs.

Fallback server stack:

- If Ktor introduces Fabric/Minecraft classpath conflicts, isolate the Ktor
  server outside the client process or use a small purpose-built transport
  module for that driver spike.
- A small hand-rolled OpenAPI emitter is acceptable for the first spike if the
  generator is isolated and replaced before the public SDK depends on it.

Avoid making Netty the first in-client HTTP server dependency unless a spike
proves it does not conflict with Minecraft's own Netty usage.

### HTTP Client

Use Ktor Client for Kotlin/JVM clients and tests that call the generated local
API.

Reasons:

- keeps the Kotlin HTTP stack consistent with Ktor Server;
- supports coroutine-first request flows used by supervisor and SDK code;
- current Maven metadata showed `ktor-client-core-jvm` and `ktor-client-cio-jvm`
  `3.5.0`;
- avoids mixing multiple JVM HTTP client APIs into Craftwright product code.

Use Java's standard `HttpClient` only in tiny dependency-free PoCs.

### JSON And Protocol Models

Use `kotlinx.serialization` for generated API metadata, OpenAPI model output,
protocol DTOs, and artifact JSON/JSONL.

Current Maven metadata during research showed:

- `kotlinx-serialization-json-jvm` `1.11.0`;
- `kotlinx-coroutines-core-jvm` `1.11.0`.

Use coroutines for:

- client-thread scheduling boundaries;
- API request timeouts;
- lifecycle supervision;
- event streams;
- daemon transports.

### CLI

Use Clikt for the JVM `mcw` CLI and Mordant for terminal output. The CLI should
be adaptive at runtime, not generated Kotlin source. Keep a small handwritten
core for daemon startup, configuration, authentication, output modes, and
generic dispatch; load kernel and per-client OpenAPI plus action descriptors to
dispatch action aliases at runtime. Generated alias help should be built from
the same metadata as follow-up work.

Current Maven metadata during research showed:

- `clikt-jvm` `5.1.0`;
- `mordant-jvm` `3.0.2`.

The CLI must still follow the existing CLI UX contract: stdout for primary data,
stderr for diagnostics/progress, stable `--json`/`--jsonl` output, explicit
exit codes, and non-interactive `--no-input` behavior.

Dynamic command examples:

```text
mcw clients alice actions
mcw clients alice run player.move --arg forward=true --arg ticks=20
mcw clients alice player move --forward --ticks 20
mcw clients alice player chat "hello"
mcw clients alice player move --help  # roadmap
```

The generic `run` command is the stable fallback. Pretty aliases are resolved
from `/clients/{id}/actions` at runtime; their future `--help` output should
come from `/clients/{id}/openapi.json` and `/clients/{id}/actions`, with cached
metadata keyed by the client's action
fingerprint and a refresh path for changed clients.

### Testing

Use JUnit 5 plus Kotest for Kotlin tests.

Current Maven metadata during research showed `kotest-runner-junit5-jvm`
`6.2.1`.

Test layers:

- pure unit tests for route generation and OpenAPI generation;
- fake-client tests for handles, events, JSON, and token behavior;
- Ktor Client tests for JVM clients and local API routes;
- opt-in Fabric real-client smoke tests for generated actions/resources such as
  `POST /clients/{id}:run` with `player.chat` or `player.move`.

### Java Fallbacks

Java remains a first-class fallback for:

- Mixins;
- accessor interfaces;
- low-level version-specific bridge classes;
- a dependency-light API server inside the Minecraft process if Ktor or Kotlin
  runtime dependencies cause loader/classpath problems;
- reflection code that benefits from exact Java signatures.

If the first Fabric spike hits classpath problems, keep the driver runtime Java
and move Kotlin to the supervisor, code generator, CLI, and tests. The generated
API design does not depend on Kotlin being loaded inside Minecraft.

## Operating Model

The intended runtime model is:

```text
CI job or local agent starts sandbox/container
Craftwright launches one real Minecraft Java client
in-client driver starts local API server on 127.0.0.1
driver generates /openapi.json for that exact version/session
tests, agents, adaptive CLI, or generated SDK call the API
events and artifacts are recorded
process/container is destroyed
```

This is not designed as a multi-tenant internet-facing API. Basic guardrails
still apply because they are cheap and prevent accidental cross-process access:

- bind to `127.0.0.1` by default;
- require a random per-launch token;
- write artifacts for every session;
- schedule Minecraft state access on the client thread;
- destroy processes and handles after the run.

## Client Management Boundary

The generated session API is not the whole client-management API. Craftwright
should keep persistent setup state separate from live client control:

- versions, loaders, profiles, instances, mods, Java runtimes, and caches belong
  to the supervisor/client-manager API;
- stable routes such as `/openapi.json`, `/clients`, `/clients/{id}`,
  `/clients/{id}/openapi.json`, `/clients/{id}/actions`, and
  `/clients/{id}/events` belong to the supervisor/kernel API;
- generated action/resource routes below `/clients/{id}` belong to one running
  client session and are available only when that client's OpenAPI document
  advertises them.

The client-management decision is documented in
`docs/superpowers/specs/2026-06-25-client-management-decisions.md`. PrismLauncher
is a strong reference for instance and component modelling and may become an
optional adapter later, but it should not be required for the generated API or
the first headless CI path.

## Proof Of Concept Results

Three throwaway Java PoCs and one real-client bridge PoC were created under
`/tmp` during research.

### Raw Reflection PoC

Location: `/tmp/craftwright-reflect-poc`

Validated:

- local JVM HTTP server;
- `/openapi.json`;
- stable facade endpoints;
- `/reflect/invoke`;
- root object handle;
- object handles for complex return values;
- client-thread scheduling;
- token/policy shape for unsafe methods.

Result: raw reflection is useful as an escape hatch, but it is not the best
primary agent UX.

### Generated Route PoC

Location: `/tmp/craftwright-generated-api-poc`

Validated:

- generated paths from root objects and getter/method chains;
- generated action paths such as `/clients/{id}:run`;
- OpenAPI generation from route metadata;
- complex return values as handles;
- selective method exposure.

Result: generated pretty routes are better than a single generic
`/reflect/invoke` endpoint, but the `/mc/client` prefix is unnecessary for a
single client-process API.

### CI Client API PoC

Location: `/tmp/craftwright-ci-api-poc`

Validated:

- historical short root paths in the throwaway PoC;
- generated action invocation route shape now represented as
  `POST /clients/{id}:run`;
- generated getter/action routes only when discovered in the running client;
- `/version`;
- `/events`;
- `/openapi.json`;
- opaque handle and class metadata concepts as implementation evidence, not
  active public kernel routes;
- OpenAPI vendor extensions such as Java class/method and client-thread
  metadata;
- random token check;
- simulated client-thread scheduling.

Verification command:

```sh
javac -d /tmp/craftwright-ci-api-poc/out $(find /tmp/craftwright-ci-api-poc/src /tmp/craftwright-ci-api-poc/test -name '*.java') && java -cp /tmp/craftwright-ci-api-poc/out poc.CiApiPoCTest
```

The command exited with code `0`.

Result: the recommended shape is feasible enough to move into a real Fabric
driver spike.

### Real Client Bridge PoC

Location: `/tmp/craftwright-real-client-poc`

Validated:

- Paper 1.21.4 server in offline mode;
- real Minecraft Java client launched through HeadlessMC;
- Fabric 1.21.4 client with HMC-Specifics 2.4.0;
- local HTTP API wrapper over the running client process;
- API-triggered client connect to `127.0.0.1:25567`;
- API-triggered chat;
- API-triggered rendered text capture;
- API-triggered UI dump and first-run `Continue` button click;
- API-triggered keyboard movement.

Observed server evidence:

```text
CwApiBot joined the game
<CwApiBot> api action after reconnect
CwApiBot has the following entity data: [-5.5d, -60.0d, 10.914621337840606d]
```

Result: the Craftwright-shaped API loop is viable against a real Minecraft
client. The implementation is still only a bridge because it sends
HMC-Specifics console commands underneath. This is acceptable evidence for the
launcher/supervisor loop, but it should not become the product driver.

The product driver should implement these actions directly inside a Fabric mod
and expose them through the generated per-client OpenAPI surface:

- discovered action/resource routes and schemas below `/clients/{id}` for
  movement, jump, look, raycast, inventory, world/entity queries, and screen
  interaction;
- generated aliases such as `POST /clients/{id}/player:move` only when the
  running driver advertises that action;
- Craftwright-owned metadata for action schema versioning, runtime fingerprints,
  mappings, registries, mods, permissions, and server feature inputs.

The bridge PoC found that simulated keys are fragile because first-run screens,
title screens, focus, and client state can swallow input. Direct in-client APIs
should set movement intent and look direction on the client tick and expose
structured perception data rather than parsing rendered text or server logs.

## API Shape

Required supervisor/kernel endpoints:

```text
GET /openapi.json
GET /version
GET /events
GET /clients
POST /clients
GET /clients/{id}
POST /clients/{id}:connect
POST /clients/{id}:stop
GET /clients/{id}/openapi.json
GET /clients/{id}/actions
POST /clients/{id}:run
GET /clients/{id}/events
```

Generated per-client aliases:

```text
POST /clients/{id}/player:move
POST /clients/{id}/player:chat
POST /clients/{id}/inventory:select
GET  /clients/{id}/world:query
```

The exact generated path set is per Minecraft version, loader, driver runtime,
active bindings, permissions, server features, and live client state. The
stable contract is that `/clients/{id}/openapi.json` describes the available
operations for the current session.

## Route Generation Rules

The generator should require minimal hand annotation.

Action/resource namespaces are registered by the driver:

```text
player      -> local player actions/resources when present
world       -> client world queries when present
screen      -> current screen actions/resources when present
inventory   -> current inventory actions/resources when present
connection  -> lifecycle/connection actions when present
```

Path synthesis:

- action `namespace.name` becomes `/clients/{id}/namespace:name`.
- generic action invocation remains available through `/clients/{id}:run`.
- resource reads must be explicitly advertised by OpenAPI before they are
  callable; do not infer public routes from arbitrary JVM getters or fields.
- nested resources can expand into additional generated paths only when the
  driver advertises them as Craftwright-owned resources.
- primitive, string, enum, list, map, and simple DTO values return JSON values.
- complex Minecraft/JVM objects return opaque handles or DTO summaries.

HTTP method inference:

- resource reads use `GET`;
- actions and custom methods use `POST`;
- ambiguous driver operations are not exposed until given an explicit
  Craftwright-owned action/resource descriptor.

The implementation can start conservative in route expansion and increase
coverage as real Minecraft tests show which paths are useful.

## Object Handles

Complex objects should not be deeply serialized by default. Return handles:

```json
{
  "kind": "handle",
  "id": "o_481",
  "class": "net.minecraft.client.player.LocalPlayer",
  "resource": "player"
}
```

Handles allow agents to continue exploring without requiring the API to flatten
the entire Minecraft object graph.

The handle registry is session-local. Handles are invalid after the client
stops.

## OpenAPI Requirements

`/openapi.json` is the canonical catalog and machine contract. Do not introduce
a separate required searchable catalog unless evidence shows OpenAPI is
insufficient.

Each operation should include:

- stable `operationId`;
- tag based on root/path/class;
- Java class and method/field source;
- thread metadata;
- result shape metadata;
- path source metadata.

Example vendor extensions:

```json
{
  "x-craftwright-action": "player.chat",
  "x-craftwright-thread": "client",
  "x-craftwright-return": "value",
  "x-craftwright-source": "action"
}
```

For huge generated APIs, clients should filter the OpenAPI document locally.
Agents, SDK generators, and the adaptive CLI can use tags, operation IDs, action
descriptors, and extensions to find the relevant operations. Human-facing CLI
help should prefer action descriptor summaries, descriptions, examples, and arg
schemas, with raw OpenAPI as the fallback machine contract.

The daemon-level client management routes must also appear in the route catalog
while the fake local API exists:

- `POST /clients`
- `GET /clients/{id}/openapi.json`
- `GET /clients/{id}/actions`
- `POST /clients/{id}:run`
- generated aliases such as `POST /clients/{id}/player:move` and
  `POST /clients/{id}/player:chat`
- `POST /clients/{id}:stop`
- `GET /clients/{id}/events`

These are session-management and discovery routes, not HMC-Specifics command
strings or raw Fabric/Yarn class paths. Per-client AIP-style aliases are
generated from discovered action descriptors with Craftwright-owned
`resource.action` ids.

## Version Endpoint

`GET /version` must describe the exact runtime:

```json
{
  "minecraft": "26.2",
  "loader": "fabric",
  "loaderVersion": "0.19.3",
  "driver": "craftwright-driver-fabric",
  "driverVersion": "0.0.1",
  "java": "25",
  "mappings": "mojang",
  "openapiGeneratedAt": "..."
}
```

Tests should record this response with artifacts.

## Events And Artifacts

Even if API calls are the primary interface, Craftwright still needs event and
artifact capture for CI debugging.

Each session should write:

```text
openapi.json
calls.jsonl
events.jsonl
stdout.log
stderr.log
crash-reports/
version.json
```

`GET /events` can expose recent session events for simple clients. Long-running
consumers may later use WebSocket or Server-Sent Events.

## Ecosystem Implications

Latest Minecraft support should start Fabric-first:

- Fabric API has current releases for 26.x and fast update velocity.
- Fabric's client-side Mixin model is the simplest path for the first generated
  API driver.
- MixinExtras is a useful helper for more expressive Mixins.
- Minecraft 26.x reducing obfuscation makes generated API discovery more
  realistic than in older eras.

Version compatibility should be a separate track:

- Forge remains important for old community versions and compatibility testing.
- Additional Fabric compatibility rows may help for some older client targets,
  but they should not be the first compatibility bet.
- NeoForge should be added for modern Forge-like environments after Fabric proves
  the generated API model.
- HeadlessMC/HMC-Specifics remain valuable references for headless CI, launcher
  behavior, runtime command bridging, and version support evidence.

Prefer one consolidated Fabric driver with internal version-aware bindings,
reflection/mapping probes, and support checks. Per-client OpenAPI is the public
truth for what a running client supports, so Minecraft versions should not
force separate public API surfaces or one Gradle subproject per version unless
the build/runtime classpath requires it.

## First Real Spike

The next implementation spike should be a Fabric client mod for the current
target Minecraft version:

- starts local API server on `127.0.0.1`;
- requires a random token;
- registers action/resource descriptors for client, player, screen, world, and
  connection behavior;
- generates `/openapi.json`;
- exposes `/version`;
- exposes `/events`;
- generates per-client action aliases and resource routes only from advertised
  descriptors;
- represents object handles and class metadata through schemas/extensions until
  a deliberate handle-resource design lands;
- schedules calls on the Minecraft client thread;
- supports `POST /clients/{id}:run` actions such as `player.chat` and
  `player.move` against the real client as the first proof.

## Done Definition

This design is ready for implementation planning when:

- the JVM-first rewrite remains the parent architecture;
- the first generated API target is Fabric on the current target Minecraft
  version;
- OpenAPI is accepted as the primary generated machine contract;
- route generation does not require annotating thousands of Minecraft methods;
- object handles are accepted as the way to represent complex live JVM objects;
- CI/local sandbox operation is accepted as the initial safety model.

## References

- JVM-first rewrite:
  `docs/superpowers/specs/2026-06-25-jvm-first-rewrite-design.md`
- Client management decisions:
  `docs/superpowers/specs/2026-06-25-client-management-decisions.md`
- Kotlin releases:
  https://kotlinlang.org/docs/releases.html
- Ktor OpenAPI generation:
  https://ktor.io/docs/openapi-spec-generation.html
- Ktor OpenAPI serving:
  https://ktor.io/docs/server-openapi.html
- Ktor Client:
  https://ktor.io/docs/client-create-new-application.html
- kotlinx.serialization:
  https://github.com/Kotlin/kotlinx.serialization
- Clikt:
  https://github.com/ajalt/clikt
- Mordant:
  https://github.com/ajalt/mordant
- Kotest:
  https://github.com/kotest/kotest
- Fabric Language Kotlin:
  https://modrinth.com/mod/fabric-language-kotlin
- Fabric API:
  https://github.com/FabricMC/fabric-api
- Fabric Loader:
  https://github.com/FabricMC/fabric-loader
- MixinExtras:
  https://github.com/LlamaLad7/MixinExtras
- HeadlessMC:
  https://github.com/headlesshq/headlessmc
- HMC-Specifics:
  https://github.com/headlesshq/hmc-specifics
- MC-Runtime-Test:
  https://github.com/headlesshq/mc-runtime-test
