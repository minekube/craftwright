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
- live resource projections exposed through `/clients/{id}/resources` and
  `x-craftless-resources`, derived from discovered action descriptors;
- a Craftless-owned instance file layout in client responses, covering instance
  root, game root, mods, config, saves, resource packs, and shader packs;
- cache preparation that resolves Minecraft metadata, the selected client jar,
  Minecraft version libraries, Java runtime files, native classifier libraries,
  Fabric loader profile libraries, and Minecraft asset objects into
  Craftless-owned workspace handles with launch classpath, native-path,
  Java-executable, and launch-argument handles;
- manifest-driven cache export and cleanup through the supervisor API and
  `craftless` CLI;
- an adaptive JVM `craftless` CLI using Ktor Client;
- a stable `DriverSession` contract with lifecycle primitives plus generic
  action discovery and invocation;
- driver and OpenAPI action descriptors now include provenance and availability
  metadata, including machine-readable reasons for unavailable probe-discovered
  operations;
- daemon generic and generated-alias action routes reject unavailable action
  descriptors before invoking the driver;
- generated action aliases are emitted from the same live action snapshot as
  the per-client OpenAPI action metadata, and OpenAPI generation rejects alias
  routes without matching action descriptors;
- adaptive CLI generic and generated-alias action dispatch uses the live
  `/clients/{id}/openapi.json` action descriptors for action existence,
  argument schemas, and generated help, while `/clients/{id}/actions` remains
  a descriptor projection;
- protocol policy tests reject public action descriptors and route metadata
  that leak Fabric, Yarn, intermediary, raw Minecraft, bridge, or launcher
  namespace tokens;
- Fabric discovery rejects advertised available actions unless they have an
  execution binding, and allows unbound actions only as unavailable runtime
  probes with machine-readable reasons;
- Fabric/Loom driver scaffolding with current action evidence;
- Fabric-generated action descriptors for current chat, movement, player
  query/look, raycast, inventory query/equip, and block-break bindings.
  Broader gameplay actions must come from real bindings or runtime discovery
  probes, not static placeholders;
- a minimal internal Fabric discovery projection that lists binding-backed
  actions and can represent runtime-probe unavailable actions without an
  execution binding;
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
  `mise -C <repo> exec -- gradle :driver-fabric:runClient`, whose in-client Fabric smoke
  controller starts a local daemon API backed by the Fabric driver, fetches
  per-client OpenAPI/action metadata and resource projections, connects to the
  smoke server, invokes generated `player.chat`, `player.move`,
  `player.query`, `player.look`, `inventory.query`, `inventory.equip`, and
  `world.block.break` through `POST /clients/{id}:run` after connection,
  provisions `minecraft:iron_sword` through the server fixture as setup,
  waits until live `inventory.query` observes `Iron Sword`, equips the
  discovered slot, writes client artifacts next to server artifacts, and
  verifies server-side join/item-provision/chat/disconnect evidence plus
  driver-side movement and gameplay result telemetry;
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
- `mise run architecture-check` covers the live OpenAPI/action architecture
  across protocol, daemon, CLI, Fabric driver, and Bun helper tests.

## Phase 1: Real-Client Proof

Goal: prove that Craftless can automate a real Minecraft Java client through
the durable Fabric direction.

- Keep the opt-in Fabric smoke green: the 2026-06-26 run launched
  `:driver-fabric:runClient`, joined the provisioned Minecraft `1.21.6`
  server, fetched generated OpenAPI/actions/resources through the in-client
  daemon API,
  invoked generated `player.chat`, `player.move`, `player.query`,
  `player.look`, `inventory.query`, `inventory.equip`, and
  `world.block.break` through `POST /clients/{id}:run`, captured server-side
  item-provision/join/chat/disconnect evidence, observed and equipped
  `Iron Sword` through live inventory metadata, and recorded driver-side
  movement plus gameplay result telemetry.
- Strengthen generated `player.move` proof from accepted driver telemetry to
  measured server-side position deltas or richer in-client position telemetry.
- Keep bridge evidence tests separate from Fabric smoke tests so the bridge
  cannot accidentally become the product path.

Verification gate:

```sh
mise exec -- gradle :driver-fabric:test :daemon:test
mise run ci
```

## Phase 2: Generated Action Surface

Goal: grow automation breadth without creating a static SDK-shaped action list
or static placeholder descriptors.

- Add Fabric runtime discovery providers that inspect the running client,
  player, world, interaction manager, inventory, screen, registries, mods,
  permissions, and server features.
- Current discovery has connected-client bindings with disconnected-client
  unavailable probe metadata for `player.query`, `player.look`,
  `player.raycast`, `inventory.query`, `inventory.equip`, and
  `world.block.break`.
- Current resource projection groups discovered action ids into live resources
  such as `player`, `inventory`, and `world.block`, and includes the action
  descriptor schemas that produced each resource. Richer object handles,
  registry/server-feature resources, and event relationships are still roadmap.
- Project discovered runtime affordances into Craftless-owned actions,
  resources, handles, schemas, availability metadata, and events.
- Add real execution bindings before treating an action as supported.
- Continue enforcing unavailable operations as probe-discovered metadata with
  machine-readable reasons; add concrete probes beyond the current scaffold.
- Add typed argument schemas and result schemas where the current OpenAPI model
  needs more than primitive request arguments.
- Add runtime fingerprints that include Minecraft version, loader, mappings,
  installed mods, registries, server features, permissions, action schema
  versions, action provenance, and action availability.
- Extend daemon validation for mismatched result schemas before dispatch.
- Keep generated aliases derived from OpenAPI metadata only.

Verification gate:

```sh
mise run architecture-check
mise exec -- gradle :protocol:test :driver-api:test :daemon:test :cli:test
mise run ci
```

## Phase 3: Adaptive CLI And Client Generation

Goal: make `craftless` and future generated clients consume live specs instead
of mirroring the API by hand.

- Cache per-client OpenAPI only by runtime/action fingerprint.
- Render dynamic CLI help from `/clients/{id}/openapi.json`, using
  `/clients/{id}/actions` only as a descriptor projection/availability view.
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
- Do not add non-Ktor HTTP clients or servers, or custom HTTP method enums.
- Do not expose HeadlessMC, HMC-Specifics, Fabric/Yarn/intermediary, raw
  Minecraft implementation, or launcher internals as public contracts.
- Do not create a separate public Gradle module per Minecraft version without
  evidence that one consolidated Fabric driver cannot support the needed
  version-aware bindings.
