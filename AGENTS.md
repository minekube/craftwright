# Craftless Agent Instructions

## Read First

This root file contains repository-wide rules. Also read the nearest
subdirectory `AGENTS.md` before editing files inside a module or docs tree.
Subproject instructions add detail for their scope; they do not override these
root rules unless they are more specific about that subproject.

## Product Direction

Craftless is automation infrastructure for real Minecraft Java clients,
headless or visible. The repository, JVM packages, Gradle coordinates, OpenAPI
metadata, Fabric entrypoints, and implementation docs use
`com.minekube.craftless`.

The durable product shape is intentionally thin: launch or attach to a real
Minecraft Java client, load a small in-client driver, and expose local APIs
whose machine contracts are OpenAPI documents generated from the running
system. Treat it like Browserless-style infrastructure for Minecraft clients,
not a large hand-written SDK with one static method per Minecraft action.

Do not grow a hand-coded static action surface for every player/world operation.
Use reflection/capability discovery in the running client, with OpenAPI as the
source of truth for available actions, objects, handles, and schemas.

The public domain is `minekube.com`.

## Public API Rules

There are two OpenAPI surfaces:

- `GET /openapi.json` is the stable supervisor/daemon kernel API. It describes
  lifecycle, client creation, events, and how to discover per-client specs.
- `GET /clients/{id}/openapi.json` is the live generated API for that specific
  Minecraft client instance. It reflects the running Minecraft version, loader,
  driver runtime, mappings, installed mods, registries, server/game features,
  permissions, and discovered actions/capabilities.

Do not assume all clients share one static action API. Generated clients,
agents, and `craftless` should fetch the instance spec for the target client and may
cache it only by a capability fingerprint that includes runtime/version/mod/
registry inputs.

Public names must be Craftless-owned. Do not expose HeadlessMC,
HMC-Specifics, Fabric/Yarn/intermediary names, raw Minecraft implementation
names, Minecraft console commands, mod package names, or launcher internals as
public API, CLI, SDK, or documentation contracts.

Use discovered/generated actions for player/world behavior such as movement,
look, raycast, interaction, inventory, and world/entity queries. Do not add
one-off static Kotlin methods, custom route enums, or static CLI commands for
each Minecraft action.

## Module Map

- `protocol/`: OpenAPI/action metadata, route catalog, serializable protocol
  DTOs, and API naming rules.
- `daemon/`: Ktor local supervisor API, client session lifecycle, per-client
  OpenAPI/action endpoints, and runtime driver wiring.
- `driver-api/`: stable JVM driver contract and fake implementation for tests.
- `driver-runtime/`: adapters from `DriverSession` to concrete backends,
  including temporary bridge adapters.
- `driver-fabric/`: Fabric/Loom driver module with internal version-aware
  bindings where practical.
- `bridge-hmc/`: evidence-only HeadlessMC/HMC-Specifics bridge code.
- `cli/`: adaptive `craftless` CLI core and runtime OpenAPI/action dispatch.
- `testkit/`: fake clients, fixtures, and test helpers.
- `playwright/`: Bun-powered helper tests and external integration fixtures.
- `docs/`: architecture, roadmap, and evidence docs.

## HTTP And API

- Prefer Ktor Server for local JVM HTTP/WebSocket surfaces.
- Prefer Ktor Client for Kotlin/JVM HTTP clients and tests.
- Do not add OkHttp, `com.sun.net.httpserver`, Java `HttpClient`, or
  hand-rolled HTTP clients for product code.
- Do not add custom HTTP method enums. Use framework-native types at framework
  boundaries or protocol strings such as `"GET"` and `"POST"` in metadata.
- Prefer resource-oriented API design in the style of AIP guidance: stable
  resources use standard methods, and non-CRUD operations use custom methods
  with colon syntax such as `POST /clients/{id}:run` or generated aliases such
  as `POST /clients/{id}/player:move`.
- Prefer `actions` for user-facing discovery. Internal code may still use
  capability terminology when it describes runtime support precisely.
- Keep OpenAPI authoritative for generated clients, agent tools, route
  discovery, adaptive CLI dispatch/help, and action/capability metadata.

## Driver Direction

Prefer one consolidated Fabric driver module with internal version-aware
bindings, reflection/mapping probes, and small Mixins/accessors. Do not add a
new public Gradle subproject for every Minecraft version unless there is clear
evidence that a separate loader/runtime artifact is required.

Version-specific code must stay behind stable Craftless driver/action
contracts. Per-client OpenAPI exposes the actions that actually work for the
running client instead of making module names or Minecraft versions part of the
public API.

The bridge backend is evidence infrastructure only. Do not present it as the
final automation driver.

## CLI Direction

The current CLI binary is `craftless`. The CLI should be adaptive rather than
a hand-maintained mirror of every route/action. Keep a small handwritten core for daemon startup,
configuration, auth, output modes, and generic dispatch. Build per-client
commands and help at runtime from `/openapi.json`,
`/clients/{id}/openapi.json`, and `/clients/{id}/actions`. Do not generate
Kotlin CLI source for every action.

## Tooling

- Use `mise` for pinned dependencies and commands.
- Use `mise exec -- gradle ...` for JVM work.
- Use Bun through mise for JavaScript-side helpers: `mise exec -- bun ...`.
- Do not use npm, npx, yarn, pnpm, or globally installed node tooling in repo
  workflows.

## Development Workflow

- Prefer test-first changes for behavior, bug fixes, and API changes.
- Keep edits scoped to the requested behavior and current module boundaries.
- Preserve user or parent-thread work in the tree. Do not revert unrelated dirty
  files.
- If the user asks to push, push directly to `main`; do not create a PR unless
  explicitly requested.
- Before claiming completion for a code change, run the narrow relevant tests
  and then `mise run ci` when practical.

## Project Completion Checklist

Use `docs/project-completion-checklist.md` as the project red line. Before
starting substantial work, read it and identify the next unchecked item that
moves Craftless toward completion.

After work changes project status, update the checklist with:

- status change;
- commit SHA or working-tree note;
- verification command run through `mise`;
- evidence artifact path, when applicable;
- remaining blocker or next action.

Do not mark an item complete from docs, fake-driver tests, bridge-only evidence,
or assumptions. Completion requires implementation plus verification evidence.
If real-client Fabric smoke has not passed, the project is not complete.

## Documentation

- Keep README and docs aligned with the current architecture.
- Preserve `docs/product-positioning.md` as the record of Craftless naming and
  Browserless/CDP analogy.
- Do not document removed TypeScript SDK or other inactive legacy surfaces as
  active implementation.
- When documenting future work, make clear what is implemented now versus what
  is still roadmap.
