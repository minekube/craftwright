# Craftless Agent Instructions

## Read First

This file is the repository-wide contract. Also read the nearest
subdirectory `AGENTS.md` before editing a module or docs directory.

Craftless uses `com.minekube.craftless` for JVM packages, Gradle coordinates,
OpenAPI metadata, Fabric entrypoints, and implementation docs. The public
domain is `minekube.com`.

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
Do not add static placeholder action descriptors. A public action/resource may
appear only when it comes from a real binding or from a runtime discovery probe
that inspected the running client and produced explicit availability metadata.

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

## API Layers

Keep these layers separate. Do not flatten them into one static API:

1. Supervisor/client-management API: stable handwritten lifecycle, setup,
   artifacts, events, client creation, client lookup, connection/stop, and
   per-client spec discovery. This layer owns versions, loaders, profiles,
   instances, mods, Java runtimes, caches, and files.
2. Live per-client generated API: generated from one running client. Gameplay
   actions, resources, aliases, handles, schemas, availability, and runtime
   fingerprints belong here.
3. Descriptor projections: `/clients/{id}/actions` and future resource indexes
   are convenience projections of the per-client OpenAPI, not an independent
   source of truth.
4. Adaptive consumers: the `craftless` CLI, agents, and generated clients fetch
   the supervisor spec plus the live client spec/descriptors at runtime. They
   must not hard-code gameplay command catalogs.
5. Internal driver API: stable JVM lifecycle/event/runtime metadata plus
   `actions()` and `invoke(...)`. Do not add static driver methods such as
   `sendChat()`, `player()`, `inventory()`, `raycast()`, or one method per
   gameplay action.
6. Fabric runtime discovery/projection: internal probes inspect
   Fabric/Minecraft/mod/client state and project it into Craftless-owned
   descriptors. Raw implementation names are inputs, not public contracts.
7. Fabric execution bindings: internal client-thread implementations for
   advertised executable actions.

The source design is
`docs/superpowers/specs/2026-06-25-generated-client-api-design.md`, especially
the client-management boundary, route generation rules, and OpenAPI
requirements sections.

## HTTP And CLI

- Use Ktor Server for local JVM HTTP/WebSocket surfaces.
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

## Driver Rules

- Prefer one consolidated `driver-fabric` module with internal version-aware
  bindings, reflection/mapping probes, and small Mixins/accessors.
- Keep Minecraft calls on the client thread.
- Version-specific code stays behind stable Craftless driver/action contracts.
- Per-client OpenAPI exposes real executable bindings and runtime-discovered
  resources/actions for the running client. Unavailable actions may be exposed
  only when a runtime probe discovered the operation and records why it is not
  currently executable.
- Module names and Minecraft versions are not public API.
- The bridge backend is evidence infrastructure only. Do not present it as the
  final automation driver.

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

## Completion Source Of Truth

Use `docs/project-completion-checklist.md` as the active project checklist.
Update it when project status changes.

Do not mark Craftless complete while the current Fabric driver is still a
small hand-written binding list. Completion requires runtime discovery,
generated per-client OpenAPI, executable bindings or probe-backed availability
for advertised actions/resources, and real-client evidence.

## Documentation

- Keep README and docs aligned with current architecture.
- Preserve `docs/product-positioning.md` as the Craftless naming and
  Browserless/CDP analogy record.
- Keep historical plans under `docs/superpowers/` as traceability, not current
  product truth.
- Make implemented state versus roadmap explicit.
