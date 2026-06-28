# Craftless Agent Operating Contract

This file holds the durable implementation guardrails that are too detailed
for root `AGENTS.md`. Keep root `AGENTS.md` short and stable; update this file
when the durable project contract changes.

Do not put per-phase history, roadmap checkboxes, temporary tasks, or
completion evidence here. Those belong in `docs/superpowers/phase-index.md`,
`docs/project-completion-checklist.md`, specs, plans, and evidence files.

## Product Shape

Craftless is Browserless-style automation infrastructure for real Minecraft
Java clients, headless or visible.

The durable shape is thin:

1. Launch or attach to a real Minecraft Java client.
2. Load a small in-client driver.
3. Discover the live client/runtime capability graph.
4. Expose local APIs whose machine contracts are generated OpenAPI documents.

Do not turn Craftless into a hand-written SDK with one static method, route, or
CLI command per Minecraft action.

Work on the system that discovers, projects, invokes, and streams Minecraft
runtime affordances; do not work inside the system by adding one more bespoke
gameplay action.

Do not add static placeholder action descriptors, and do not add new
hand-written public gameplay action descriptors merely because they have a real
binding. A public gameplay action/resource should come from the runtime
capability graph generated from reflection, mappings, registries, callbacks,
screens, handlers, world/entity/inventory state, permissions, and installed
mods. Existing hand-written gameplay bindings are transitional bootstrap and
evidence code, not the durable API shape and not completion evidence by
themselves.

## Public API Rules

Craftless has two OpenAPI surfaces:

- `GET /openapi.json`: stable supervisor API for lifecycle, client creation,
  events, and per-client spec discovery.
- `GET /clients/{id}/openapi.json`: generated live API for one client
  instance. It reflects that client's Minecraft version, loader, driver
  runtime, mappings, installed mods, registries, server/game features,
  permissions, and discovered actions/resources.

Public names must be Craftless-owned. Never expose these as public API, CLI,
SDK, README, or docs contracts:

- HeadlessMC or HMC-Specifics names;
- Fabric/Yarn/intermediary names;
- raw Minecraft implementation names;
- Minecraft console commands;
- mod package names;
- launcher internals.

Fabric/Minecraft internals are allowed as implementation inputs. The public
output must be a Craftless projection: actions, resources, handles, schemas,
availability, and events that agents can use through OpenAPI.

Use `actions` for user-facing discovery. Internal code may use `capability`
only when it describes runtime support precisely.

Generated aliases such as `POST /clients/{id}/player:move` are derived from
the running client's OpenAPI/action descriptors. Do not create static gameplay
route families in Kotlin, CLI source, README examples, or tests.

Do not add new public action ids such as `world.dimension.query` by writing a
descriptor and binding pair directly. First add or improve the generic
discovery/projection system that would discover that affordance from the live
runtime, then let the generated per-client OpenAPI expose the projected result.

Version breadth is a system property, not a set of copied driver trees. Shared
runtime discovery, projection, invocation, attach transport, OpenAPI
generation, artifact resolution, Java selection, and cache layout are the
default for every Minecraft/Fabric version. Add per-version code only after
proving an actual Minecraft, Fabric API, mapping, loader, or bytecode-signature
divergence, and then isolate only the diverging adapter/accessor/provider
behind a lane boundary.

Do not turn 1.20.x, 1.21.x, latest-release, 26.x, or any future version into
separate public APIs, route families, CLI command trees, session types, action
catalogs, or copied gameplay implementations.

## API Layers

Keep these layers separate. Do not flatten them into one static API:

1. Supervisor/client-management API: stable handwritten lifecycle, setup,
   artifacts, events, client creation, client lookup, connection/stop, and
   per-client spec discovery. This layer owns versions, loaders, profiles,
   instances, mods, Java runtimes, caches, and files.
2. Live per-client generated API: generated from one running client. Gameplay
   actions, resources, aliases, handles, schemas, availability, and runtime
   fingerprints belong here.
3. Descriptor projections: `/clients/{id}/actions` and resource indexes are
   convenience projections of the per-client OpenAPI, not an independent source
   of truth.
4. Adaptive consumers: the `craftless` CLI, agents, and generated clients fetch
   the supervisor spec plus the live client spec/descriptors at runtime. They
   must not hard-code gameplay command catalogs.
5. Internal driver API: stable JVM lifecycle/event/runtime metadata plus
   `actions()` and `invoke(...)`. Do not add static driver methods such as
   `sendChat()`, `player()`, `inventory()`, `raycast()`, or one method per
   gameplay action.
6. Runtime capability graph: internal discovery collects Fabric/Minecraft/mod
   classes, methods, registries, callbacks, screens, handlers, resources,
   handles, permissions, and live client state into a generic graph. This graph
   is the input to OpenAPI generation.
7. Fabric runtime discovery/projection: internal probes inspect
   Fabric/Minecraft/mod/client state and project graph nodes into
   Craftless-owned descriptors. Raw implementation names are inputs, not public
   contracts.
8. Fabric execution adapters: internal client-thread implementations invoke
   projected graph affordances. These adapters must not become the public API
   catalog.

The source design is
`docs/superpowers/specs/2026-06-25-generated-client-api-design.md`, especially
the client-management boundary, route generation rules, and OpenAPI
requirements sections.

## Acceptance Scenarios Are Not Product APIs

Survival gameplay such as "collect wood, craft a weapon, find a cow, kill it,
and show loot" is an acceptance scenario. It is useful only when it proves that
an external agent can succeed through the live generated OpenAPI, generic
actions, handles, SSE/JSON-RPC events, adaptive CLI, and agent documentation.

Do not grow `task.survival.*` or similar hard-coded scenario logic into the
durable public API. If a scenario needs a missing primitive, improve the
generic runtime graph, generated schema/handle metadata, invocation adapter,
event stream, CLI, docs, or agent skill that would let a normal agent compose
the behavior itself. Internal scenario harnesses may exist temporarily as
evidence, but they are not completion evidence unless the same result is
reproducible from the public generated API without adding new
scenario-specific Kotlin actions.

## HTTP And CLI

- Use Ktor Server for local JVM HTTP, SSE, and only-if-needed WebSocket
  surfaces.
- Use Ktor Client for Kotlin/JVM HTTP clients and tests.
- Do not add OkHttp, `com.sun.net.httpserver`, Java `HttpClient`, or
  hand-rolled HTTP clients in product code.
- Do not add custom HTTP method enums. Use framework-native types at framework
  boundaries or protocol strings such as `"GET"` and `"POST"` in metadata.
- Prefer AIP-style resource routes and custom methods such as
  `POST /clients/{id}:run`.
- The CLI binary is `craftless`.
- Keep the CLI adaptive: small static core for daemon startup, config, auth,
  output, lifecycle, discovery, and generic dispatch. Load gameplay commands
  and help from `/openapi.json`, `/clients/{id}/openapi.json`, and
  `/clients/{id}/actions`.

## Live Events And RPC

- Prefer Server-Sent Events for server-to-client live streams because Craftless
  primarily streams lifecycle, runtime, capability, and game/perception events
  outward to agents and clients.
- Use HTTP `POST` JSON-RPC-style requests for client-to-server control such as
  invoke, subscribe, unsubscribe, and query. The POST response may acknowledge
  accepted work; correlated results and notifications may arrive on the SSE
  stream.
- JSON-RPC payload shape is useful, but SSE is the preferred transport for
  one-way server event delivery. Do not default to WebSocket unless a feature
  genuinely needs bidirectional low-latency interactive control that cannot be
  modeled as HTTP POST plus SSE.
- Event filtering should exist on both sides: server-side subscription filters
  to avoid flooding clients, and client-side filters for local agent logic.
- Event names, resource ids, action ids, handles, and schemas emitted on
  streams must be Craftless-owned projections from the live runtime capability
  graph. Do not stream raw Fabric/Yarn/intermediary names or Minecraft
  implementation names as public contracts.

## Driver Rules

- Prefer one consolidated `driver-fabric` module with internal version-aware
  bindings, reflection/mapping probes, and small Mixins/accessors.
- Keep Minecraft calls on the client thread.
- Version-specific code stays behind stable Craftless driver/action contracts.
- Per-client OpenAPI exposes runtime-discovered resources/actions for the
  running client. Executability is provided by internal adapters behind the
  projected capability graph. Unavailable actions may be exposed only when a
  runtime probe discovered the operation and records why it is not currently
  executable.
- Do not grow `driver-fabric` by adding one hand-written public gameplay
  descriptor/binding pair after another. When an affordance is missing, improve
  the generic reflection/mapping/registry/callback/screen/handler discovery
  and projection system first.
- Module names and Minecraft versions are not public API.
- The bridge backend is evidence infrastructure only. Do not present it as the
  final automation driver, and do not route gameplay through it.

## Module Map

- `protocol/`: OpenAPI/action metadata, route catalog, serializable protocol
  DTOs, and API naming rules.
- `daemon/`: Ktor supervisor API, client lifecycle, per-client OpenAPI/action
  endpoints, and runtime driver wiring.
- `driver-api/`: stable JVM driver contract and driver-facing DTOs.
- `driver-runtime/`: adapters from `DriverSession` to concrete backends.
- `driver-fabric/`: Fabric/Loom driver module.
- `bridge-hmc/`: evidence-only bridge code.
- `cli/`: adaptive `craftless` CLI.
- `testkit/`: fake clients, fake sessions, fixtures, and test helpers.
- `playwright/`: Bun-powered helper tests and external fixtures.
- `docs/`: architecture, roadmap, evidence, and project checklist.

## Tooling

- Use `mise` for pinned dependencies and commands.
- Use `mise exec -- gradle ...` for JVM work.
- Use Bun through mise: `mise exec -- bun ...`.
- Do not use npm, npx, yarn, pnpm, or globally installed Node tooling in repo
  workflows.

## Workflow

- Prefer test-first changes for behavior, bug fixes, and API changes.
- Keep edits scoped to the requested behavior and current module boundaries.
- Preserve unrelated dirty files; do not revert user work.
- If asked to push, push directly to `main`; do not create a PR unless asked.
- Before claiming a code change is complete, run focused tests and then
  `mise run ci` when practical.
- For docs-only edits, run at least `git diff --check`.

