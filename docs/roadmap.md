# Craftless Roadmap

This roadmap tracks the path from the current Kotlin/JVM foundation to a
production-ready real Minecraft Java client automation platform. It describes
active product direction, not historical migration work.

## Current Baseline

Craftless currently has:

- a Kotlin/JVM Gradle project using `com.minekube.craftless`;
- pinned tool execution through `mise`;
- a Ktor daemon with stable supervisor OpenAPI at `/openapi.json`;
- generated per-client OpenAPI at `/clients/{id}/openapi.json`;
- descriptor-driven actions exposed through `/clients/{id}/actions`,
  `POST /clients/{id}:run`, and generated aliases;
- a Craftless-owned instance file layout in client responses, covering instance
  root, game root, mods, config, saves, resource packs, and shader packs;
- an adaptive JVM `craftless` CLI using Ktor Client;
- a stable `DriverSession` contract with lifecycle primitives plus generic
  action discovery and invocation;
- Fabric/Loom driver scaffolding with current action evidence;
- bridge code treated as evidence infrastructure only;
- a testkit local server layout that can launch a supplied Minecraft server jar
  with accepted EULA, collect short-lived process output, and import recognized
  server log lines into JSONL evidence artifacts for join, chat, movement, and
  disconnect assertions;
- a Ktor Client based testkit provisioner that resolves the Mojang launcher
  version manifest and downloads a requested Minecraft server jar into the
  fixture artifacts directory;
- an opt-in `:testkit:localMinecraftServerSmoke` task that provisions and starts
  a local Minecraft server, waits for startup, can keep it running around a
  caller-supplied smoke action or configured command, sends `stop`, and records
  server log/evidence artifacts without running during default tests;
- an opt-in `:driver-fabric:fabricClientSmoke` entrypoint and smoke plan behind
  `CRAFTLESS_FABRIC_CLIENT_SMOKE`; when enabled it runs the testkit server
  lifecycle and a bounded client command, defaulting to
  `mise exec -- gradle :driver-fabric:runClient`, whose in-client Fabric smoke
  controller starts a local daemon API backed by the Fabric driver, fetches
  per-client OpenAPI/action metadata, connects to the smoke server, invokes
  generated `player.chat` through `POST /clients/{id}:run` after connection,
  writes client artifacts next to server artifacts, and verifies server-side
  join, chat, and disconnect evidence;
- repo-local Kotlin/JVM agent skills scoped to this codebase.

## Completion Definition

Craftless is not complete until the repository can prove all of the following:

- Real Fabric client smoke launches a Minecraft Java client, joins a local test
  server, invokes at least one generated action, and verifies the server-side
  effect.
- Gameplay automation is exposed through discovered/generated actions and
  resources, not through hand-written static action APIs.
- Per-client OpenAPI is authoritative for generated clients, adaptive CLI help,
  agent tooling, and runtime action invocation.
- Ktor remains the only JVM HTTP server/client stack in product code and tests.
- All tooling runs through `mise`; JavaScript helper work uses Bun only.
- README and active docs describe the current Craftless architecture without
  presenting removed TypeScript SDK or bridge details as product surfaces.
- CI covers protocol policy, driver contract, daemon HTTP behavior, CLI
  dispatch, Fabric module compilation, and Bun-powered helper tests.

## Phase 1: Real-Client Proof

Goal: prove that Craftless can automate a real Minecraft Java client through
the durable Fabric direction.

- Keep the opt-in Fabric smoke green: the 2026-06-25 run launched
  `:driver-fabric:runClient`, joined the provisioned Minecraft `1.21.6`
  server, fetched generated OpenAPI/actions through the in-client daemon API,
  invoked generated `player.chat` through `POST /clients/{id}:run`, and
  captured server-side join, chat, and disconnect evidence.
- Invoke generated `player.move` and assert movement evidence from server-side
  position deltas or in-client driver telemetry.
- Keep bridge evidence tests separate from Fabric smoke tests so the bridge
  cannot accidentally become the product path.

Verification gate:

```sh
mise exec -- gradle :driver-fabric:test :daemon:test
mise run ci
```

## Phase 2: Generated Action Surface

Goal: grow automation breadth without creating a static SDK-shaped action list.

- Add discovered action descriptors for look, raycast, block interaction,
  entity interaction, inventory query, screen query, and screen click.
- Add typed argument schemas and result schemas where the current OpenAPI model
  needs more than primitive request arguments.
- Add runtime fingerprints that include Minecraft version, loader, mappings,
  installed mods, registries, server features, permissions, and action schema
  versions.
- Add daemon validation that rejects unavailable actions and mismatched
  argument/result schemas before dispatch.
- Keep generated aliases derived from OpenAPI metadata only.

Verification gate:

```sh
mise exec -- gradle :protocol:test :driver-api:test :daemon:test :cli:test
mise run ci
```

## Phase 3: Adaptive CLI And Client Generation

Goal: make `craftless` and future generated clients consume live specs instead
of mirroring the API by hand.

- Cache per-client OpenAPI only by runtime/action fingerprint.
- Render dynamic CLI help from `/clients/{id}/openapi.json` and
  `/clients/{id}/actions`.
- Keep static CLI commands limited to daemon lifecycle, client lifecycle,
  discovery, generic action invocation, auth/config, and output modes.
- Add an OpenAPI compatibility fixture that proves generated aliases, generic
  action invocation, and schema metadata stay in sync.
- Defer any TypeScript SDK until the generated API contract is strong enough
  and explicitly approved as a product surface.

Verification gate:

```sh
mise exec -- gradle :cli:test :protocol:test
mise exec -- bun test playwright
mise run ci
```

## Phase 4: Driver Hardening

Goal: make the Fabric driver robust across real client states.

- Keep version-specific Minecraft and mapping details inside `driver-fabric`.
- Use client-thread-safe gateways for all Minecraft client access.
- Add structured errors for title screen, disconnected, screen-open, missing
  permission, unsupported action, and server-feature mismatch states.
- Add event streams for lifecycle, chat, movement, interactions, inventory,
  perception, and driver errors.
- Expand version support only after the `1.21.6` path has smoke evidence.

Verification gate:

```sh
mise exec -- gradle :driver-fabric:test :driver-runtime:test :daemon:test
mise run ci
```

## Phase 5: Packaging And Operations

Goal: make Craftless usable as local and CI infrastructure.

- Package the CLI, daemon, Fabric driver artifact, and test fixtures with
  consistent Craftless metadata.
- Add Docker or CI runtime documentation only after the real-client smoke is
  automated.
- Expand configuration for profiles, client files, logs, auth mode, and runtime
  directories.
- Keep client file management launcher-neutral. Prism Launcher source findings
  are captured in `docs/client-file-management.md` as design input only; Prism
  remains an optional future adapter, not a required runtime dependency.
- Add observability for daemon requests, client lifecycle, driver action
  latency, and failure categories.
- Publish examples that create a client, fetch its OpenAPI, invoke generated
  actions, and inspect events.

Verification gate:

```sh
mise run ci
```

## Non-Goals For Now

- Do not reintroduce the removed TypeScript SDK as an active product surface.
- Do not add OkHttp, Java `HttpClient`, `com.sun.net.httpserver`, or custom HTTP
  method enums.
- Do not expose HeadlessMC, HMC-Specifics, Fabric/Yarn/intermediary, raw
  Minecraft implementation, or launcher internals as public contracts.
- Do not create a separate public Gradle module per Minecraft version without
  evidence that one consolidated Fabric driver cannot support the needed
  version-aware bindings.
