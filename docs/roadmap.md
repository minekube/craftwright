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
- graph-projected handle metadata exposed through `x-craftless-handles` in
  per-client OpenAPI, currently covering inventory slot and entity handle
  families from the Fabric runtime graph;
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
- per-client OpenAPI responses expose the live runtime/action fingerprint in
  `x-craftless-runtime-fingerprint`, `X-Craftless-Runtime-Fingerprint`, and
  HTTP `ETag` metadata so adaptive consumers can revalidate cached specs by
  the running client's capability fingerprint; action and resource projection
  endpoints use the same revalidation metadata;
- protocol policy tests reject public action descriptors and route metadata
  that leak Fabric, Yarn, intermediary, raw Minecraft, bridge, or launcher
  namespace tokens;
- Fabric discovery rejects advertised available actions unless they have an
  execution binding, and allows unbound actions only as unavailable runtime
  probes with machine-readable reasons;
- Fabric/Loom driver scaffolding with current action evidence;
- Fabric-generated action descriptors for current chat/movement telemetry, player
  query/look, raycast, inventory query/equip, block break/interact, and
  world time query bindings.
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
  `screen.query`, `player.query`, `player.look`, `inventory.query`,
  `inventory.equip`, `world.time.query`, `world.block.break`, and
  `world.block.interact` through `POST /clients/{id}:run` after connection,
  provisions `minecraft:iron_sword` through the server fixture as setup,
  waits until live `inventory.query` observes `Iron Sword`, equips the
  discovered slot, writes client artifacts next to server artifacts, and
  verifies server-side join/item-provision/chat/disconnect evidence plus
  driver-side movement telemetry and gameplay result artifacts;
- repo-local Kotlin/JVM agent skills scoped to this codebase.

## Completion Definition

Craftless is not complete until the repository can prove all of the following:

- The seven Superpowers spec/plan pairs dated 2026-06-26 have been executed in
  order.
- The active checklist in `docs/project-completion-checklist.md` has no open
  items.
- Gameplay automation is exposed through a runtime capability graph and
  graph-generated per-client OpenAPI, not through hand-written static action
  APIs or descriptor/binding catalog growth.
- Fabric discovery uses reflection, mappings, registries, callbacks, screens,
  handlers, world/entity/inventory/client state, permissions, and installed
  mods as graph inputs.
- Generic invocation dispatches graph-projected operations through internal
  client-thread adapters.
- Server-Sent Events stream lifecycle, runtime, capability, and gameplay
  observations, with HTTP POST JSON-RPC-style control for invoke, subscribe,
  unsubscribe, and query.
- Ktor remains the only JVM HTTP/SSE/client stack in product code and tests.
- All tooling runs through `mise`; JavaScript helper work uses Bun only.
- README and active docs describe the current Craftless architecture without
  presenting removed TypeScript SDK or bridge details as product surfaces.
- `mise run lint`, `mise run architecture-check`, and `mise run ci` pass.
- A final real gameplay session captures evidence while Craftless joins a
  server, streams events, writes chat, observes inventory/world state,
  equips a tool, mines, builds or places blocks, and fixes issues found during
  play.
- Final completion is proven by Codex-verifiable artifacts from public API/CLI
  gameplay, not by a required human chat confirmation.

## Active Completion Sequence

Follow these specs and plans in order:

1. `docs/superpowers/specs/2026-06-26-01-truth-and-guardrails-design.md`
   with `docs/superpowers/plans/2026-06-26-01-truth-and-guardrails-plan.md`.
2. `docs/superpowers/specs/2026-06-26-02-runtime-capability-graph-design.md`
   with `docs/superpowers/plans/2026-06-26-02-runtime-capability-graph-plan.md`.
3. `docs/superpowers/specs/2026-06-26-03-fabric-discovery-probes-design.md`
   with `docs/superpowers/plans/2026-06-26-03-fabric-discovery-probes-plan.md`.
4. `docs/superpowers/specs/2026-06-26-04-projection-openapi-design.md`
   with `docs/superpowers/plans/2026-06-26-04-projection-openapi-plan.md`.
5. `docs/superpowers/specs/2026-06-26-05-generic-invocation-design.md`
   with `docs/superpowers/plans/2026-06-26-05-generic-invocation-plan.md`.
6. `docs/superpowers/specs/2026-06-26-06-sse-json-rpc-consumers-design.md`
   with `docs/superpowers/plans/2026-06-26-06-sse-json-rpc-consumers-plan.md`.
7. `docs/superpowers/specs/2026-06-26-07-final-gameplay-completion-design.md`
   with `docs/superpowers/plans/2026-06-26-07-final-gameplay-completion-plan.md`.

## Phase 1: Real-Client Proof

Goal: prove that Craftless can automate a real Minecraft Java client through
the durable Fabric direction.

- Keep the opt-in Fabric smoke green: the 2026-06-26 run launched
  `:driver-fabric:runClient`, joined the provisioned Minecraft `1.21.6`
  server, fetched generated OpenAPI/actions/resources through the in-client
  daemon API, invoked generated `player.chat`, `player.move`, `screen.query`,
  `world.time.query`, `player.query`, `player.look`, `inventory.query`,
  `inventory.equip`, `world.block.break`, and `world.block.interact` through
  `POST /clients/{id}:run`, captured server-side
  item-provision/join/chat/disconnect evidence, observed and equipped
  `Iron Sword` through live inventory metadata, and recorded driver-side
  movement before-position telemetry plus gameplay result artifacts.
- Strengthen generated `player.move` proof further with measured server-side
  position deltas.
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
- Current discovery is composed from internal runtime probes. It uses a
  client-thread capability snapshot for `player.query`, `player.look`,
  `player.raycast`, `inventory.query`, `inventory.equip`,
  `world.block.break`, `world.block.interact`, and `world.time.query`, then
  projects either available bindings or unavailable probe metadata with
  machine-readable reasons. It also includes gateway-discovered
  `screen.query`; duplicate probe output is rejected before descriptor
  projection.
- Current resource projection groups discovered action ids into live resources
  such as `player`, `inventory`, `world.block`, and `world.time`, and includes
  resource-level availability reasons plus the action descriptor schemas that
  produced each resource. Graph-projected handle metadata now exposes current
  inventory slot and entity handle families; richer registry/server-feature
  resources and object-specific handle schemas are still roadmap.
- Fabric runtime metadata now uses a provider boundary. In the real Fabric
  backend, loader version, driver version, installed-mod fingerprint, and
  selected runtime registry fingerprint come from Fabric Loader and Minecraft
  registry state instead of static placeholders. Server-feature fingerprint
  inputs now include gateway-derived connection/server/feature-set state from
  the client thread; richer registry and server-feature resources remain
  roadmap.
- Project discovered runtime affordances into Craftless-owned actions,
  resources, handles, schemas, availability metadata, and events.
- Add real execution bindings before treating an action as supported.
- Continue enforcing unavailable operations as probe-discovered metadata with
  machine-readable reasons; add concrete probes beyond the current scaffold.
- Add typed argument schemas and result schemas where the current OpenAPI model
  needs more than primitive request arguments.
- Continue expanding runtime fingerprints so they include Minecraft version,
  mappings, richer registry/server-feature resources, permissions, action
  schema versions, action provenance, and action availability with the same
  rigor now used for Fabric Loader, installed-mod, selected registry, and
  gateway-derived server-feature metadata.
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

- Cache per-client OpenAPI only by runtime/action fingerprint. The daemon now
  emits HTTP `ETag` revalidation metadata for the live per-client spec;
  action/resource projections share the same validator;
  the Bun helper revalidates process-local and optional durable cached live
  specs with `If-None-Match`; `craftless clients <id> openapi --openapi-cache <dir>`,
  `craftless clients <id> actions --openapi-cache <dir>`, `craftless clients
  <id> resources --openapi-cache <dir>`, `craftless clients <id> tools
  --openapi-cache <dir>`, `craftless clients <id> run <action>
  --openapi-cache <dir>`, and generated action aliases with `--openapi-cache
  <dir>` now persist the live per-client OpenAPI body plus ETag and revalidate
  it across CLI invocations.
- Render dynamic CLI help from `/clients/{id}/openapi.json`, using
  `/clients/{id}/actions` only as a descriptor projection/availability view.
  Generated action alias help and generated resource help now use live
  per-client OpenAPI metadata.
- Export agent-tool manifests from `/clients/{id}/openapi.json` through
  `craftless clients <id> tools`, including the runtime fingerprint, generated
  alias route, availability, and action argument schema for each live action.
- Keep static CLI commands limited to daemon lifecycle, client lifecycle,
  discovery, agent-tool export, generic action invocation, auth/config, and
  output modes.
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
