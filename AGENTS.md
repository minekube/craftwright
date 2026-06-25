# Craftwright Agent Instructions

## Project Direction

Craftwright is a Kotlin/JVM-first real Minecraft Java client automation
platform. The durable product shape is an in-client Fabric driver plus local
APIs whose machine contracts are OpenAPI documents generated from the running
system.

Do not grow a hand-coded static action surface for every player/world operation.
The POC direction is reflection/capability discovery in the running client, with
OpenAPI as the source of truth for available actions, objects, handles, and
schemas.

There are two different OpenAPI surfaces:

- `GET /openapi.json` is the stable supervisor/daemon kernel API. It describes
  lifecycle, client creation, events, and how to discover per-client specs.
- `GET /clients/{id}/openapi.json` is the live generated API for that specific
  Minecraft client instance. It must reflect the running Minecraft version,
  loader, driver module, mappings, installed mods, registries, server/game
  features, permissions, and discovered capabilities.

Do not assume all clients share one static action API. Generated clients and
agents should fetch the instance spec for the target client and may cache it
only by a capability fingerprint that includes runtime/version/mod/registry
inputs.

Use small stable handwritten code only for the kernel:

- client/session lifecycle;
- `/openapi.json`;
- `/clients/{id}/openapi.json`;
- `/events`;
- root handles such as `/client`, `/player`, `/world`, and `/screen`;
- minimal transport, serialization, and launch plumbing.

Player actions such as movement, look, raycast, interaction, inventory, and
world/entity queries should be exposed through discovered/generated capabilities
instead of one-off static Kotlin methods or custom route enums.

## Architecture Rules

- Kotlin/JVM owns product logic: CLI, daemon, protocol, generated API metadata,
  launcher/supervisor, test fixtures, and runtime adapters.
- Java is appropriate for Fabric Mixins, accessors, and bytecode-sensitive
  Minecraft glue.
- Fabric version-specific code belongs in versioned driver modules such as
  `driver-fabric-1_21_6`.
- Public API names must be Craftwright-owned and must not expose HeadlessMC,
  HMC-Specifics, Minecraft console commands, or launcher implementation details.
- The bridge backend is evidence infrastructure only. Do not present it as the
  final automation driver.

## HTTP And API

- Prefer Ktor Server for local JVM HTTP/WebSocket surfaces.
- Prefer Ktor Client for Kotlin/JVM HTTP clients and tests.
- Do not add OkHttp, `com.sun.net.httpserver`, Java `HttpClient`, or
  hand-rolled HTTP clients for product code.
- Do not add custom HTTP method enums. Use framework-native types at framework
  boundaries or protocol strings such as `"GET"` and `"POST"` in metadata.
- Keep OpenAPI authoritative for generated clients, agent tools, route
  discovery, and capability metadata.
- The daemon kernel spec should expose discovery endpoints, not pretend that
  every Minecraft instance has the same player/world/mod API.
- Instance specs should include Craftwright metadata such as client id,
  Minecraft version, loader, loader version, driver version, mappings, installed
  mod fingerprint, registry fingerprint, server feature fingerprint, and
  capability schema version where available.

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

## Documentation

- Keep README and docs aligned with the current architecture.
- Do not document removed TypeScript SDK or other inactive legacy surfaces as
  active implementation.
- When documenting future work, make clear what is implemented now versus what
  is still roadmap.
