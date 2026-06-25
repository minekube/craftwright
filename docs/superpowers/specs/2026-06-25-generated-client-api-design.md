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

- `/openapi.json` is the source of truth for agents, generated SDKs, generated
  CLIs, and test integrations.
- Common roots use short paths such as `/client`, `/player`, `/world`,
  `/screen`, `/inventory`, `/connection`, `/chat`, `/input`, and `/render`.
- Deep or unknown objects use object handles under `/o/{handle}`.
- Class metadata uses `/c/{className}`.
- The API is generated from live JVM classes, methods, fields, getter chains,
  root objects, mappings, and driver-discovered capabilities.
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

Use Clikt for the JVM `mcw` CLI and Mordant for terminal output.

Current Maven metadata during research showed:

- `clikt-jvm` `5.1.0`;
- `mordant-jvm` `3.0.2`.

The CLI must still follow the existing CLI UX contract: stdout for primary data,
stderr for diagnostics/progress, stable `--json`/`--jsonl` output, explicit
exit codes, and non-interactive `--no-input` behavior.

### Testing

Use JUnit 5 plus Kotest for Kotlin tests.

Current Maven metadata during research showed `kotest-runner-junit5-jvm`
`6.2.1`.

Test layers:

- pure unit tests for route generation and OpenAPI generation;
- fake-client tests for handles, events, JSON, and token behavior;
- Ktor Client tests for JVM clients and local API routes;
- opt-in Fabric real-client smoke tests for generated routes like
  `GET /player/name` and `POST /player/sendChat`.

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
tests, agents, generated CLI, or generated SDK call the API
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
- `/openapi.json`, `/client`, `/player`, `/connection`, `/events`, `/o/*`, and
  `/c/*` belong to one running client session.

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
- nested paths such as `/mc/client/player/sendChat`;
- OpenAPI generation from route metadata;
- complex return values as handles;
- selective method exposure.

Result: generated pretty routes are better than a single generic
`/reflect/invoke` endpoint, but the `/mc/client` prefix is unnecessary for a
single client-process API.

### CI Client API PoC

Location: `/tmp/craftwright-ci-api-poc`

Validated:

- short root paths: `/client`, `/player`;
- generated method route: `POST /player/sendChat`;
- generated getter route: `GET /player/name`;
- `/version`;
- `/events`;
- `/openapi.json`;
- object fallback routes under `/o/{handle}`;
- class metadata routes under `/c/{className}`;
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

- stable roots such as `/player`, `/world`, `/screen`, and `/events`;
- discovered capability routes and schemas for movement, jump, look, raycast,
  inventory, world/entity queries, and screen interaction;
- Craftwright-owned metadata for capability versioning, runtime fingerprints,
  mappings, registries, mods, permissions, and server feature inputs.

The bridge PoC found that simulated keys are fragile because first-run screens,
title screens, focus, and client state can swallow input. Direct in-client APIs
should set movement intent and look direction on the client tick and expose
structured perception data rather than parsing rendered text or server logs.

## API Shape

Required top-level endpoints:

```text
GET /openapi.json
GET /version
GET /events
```

Generated short roots:

```text
GET  /client
GET  /client/state
GET  /player
GET  /player/name
POST /player/sendChat
GET  /world
GET  /screen
GET  /inventory
GET  /connection
POST /chat
POST /input/key
GET  /render
```

Object fallback:

```text
GET  /o/{handle}
GET  /o/{handle}/fields
GET  /o/{handle}/field/{field}
POST /o/{handle}/field/{field}
GET  /o/{handle}/methods
POST /o/{handle}/method/{method}
```

Class metadata:

```text
GET /c/{className}
GET /c/{className}/fields
GET /c/{className}/methods
```

The exact generated path set is per Minecraft version, loader, driver module,
and live client state. The stable contract is that `/openapi.json` describes the
available operations for the current session.

## Route Generation Rules

The generator should require minimal hand annotation.

Root aliases are registered by the driver:

```text
client      -> Minecraft client singleton
player      -> current local player when present
world       -> current client world when present
screen      -> current screen when present
inventory   -> current player inventory when present
connection  -> current client connection when present
```

Path synthesis:

- `getX()` becomes `/x` for reads.
- `isX()` becomes `/x` for boolean reads.
- public/readable field `x` becomes `/x`.
- ordinary method `foo(...)` becomes `/foo`.
- nested object roots can expand into additional generated paths.
- primitive, string, enum, list, map, and simple DTO values return JSON values.
- complex Minecraft/JVM objects return object handles.

HTTP method inference:

- zero-argument getters and fields use `GET`;
- methods with arguments use `POST`;
- field writes use `POST`;
- ambiguous methods use `POST`.

The implementation can start conservative in route expansion and increase
coverage as real Minecraft tests show which paths are useful.

## Object Handles

Complex objects should not be deeply serialized by default. Return handles:

```json
{
  "kind": "handle",
  "id": "o_481",
  "class": "net.minecraft.client.player.LocalPlayer",
  "path": "/player"
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
  "x-craftwright-java-class": "net.minecraft.client.player.LocalPlayer",
  "x-craftwright-java-method": "sendChat",
  "x-craftwright-thread": "client",
  "x-craftwright-return": "value",
  "x-craftwright-source": "method"
}
```

For huge generated APIs, clients should filter the OpenAPI document locally.
Agents and SDK generators can use tags, operation IDs, and extensions to find
the relevant operations.

The daemon-level client management routes must also appear in the route catalog
while the fake local API exists:

- `POST /clients`
- `POST /clients/{id}/connection/connect`
- `POST /clients/{id}/player/sendChat`
- `GET /clients/{id}/player`
- `POST /clients/{id}/stop`
- `GET /clients/{id}/events`

These are session-management routes, not HMC-Specifics command strings. The
Fabric driver should implement the same public contract when fake state is
replaced by real client control.

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
  behavior, versioned driver modules, and runtime command bridging.

## First Real Spike

The next implementation spike should be a Fabric 26.2 client mod:

- starts local API server on `127.0.0.1`;
- requires a random token;
- registers root aliases for client, player, screen, world, and connection;
- generates `/openapi.json`;
- exposes `/version`;
- exposes `/events`;
- generates short routes for discovered getters, fields, and methods;
- exposes object handle fallback under `/o/{handle}`;
- exposes class metadata under `/c/{className}`;
- schedules calls on the Minecraft client thread;
- supports `GET /player/name` and `POST /player/sendChat` against the real
  client as the first proof.

## Done Definition

This design is ready for implementation planning when:

- the JVM-first rewrite remains the parent architecture;
- the first generated API target is Fabric 26.2;
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
