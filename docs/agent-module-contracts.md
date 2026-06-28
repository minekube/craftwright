# Craftless Module Agent Contracts

This file holds durable module-local instructions that are too detailed for
the nearest `AGENTS.md`. Keep every `AGENTS.md` short and stable; update the
matching section here when a module's durable contract changes.

Do not put per-phase history, roadmap checkboxes, temporary tasks, or evidence
logs here. Those belong in `docs/superpowers/phase-index.md`,
`docs/project-completion-checklist.md`, specs, plans, and evidence files.

## Shared Module Rule

Module rules must reinforce the root contract: work on the discovery,
projection, invocation, streaming, resolver, and packaging system. Do not add
static gameplay catalogs, scenario shortcuts, per-version public APIs, or
duplicated action implementations to make one narrow case pass.

## `docs/`

`docs/` owns design notes, implementation plans, evidence records, and roadmap
material. Keep README and docs aligned with current architecture, and clearly
separate implemented behavior from roadmap.

Do not document removed TypeScript SDK or other inactive legacy surfaces as
active implementation. Use `minekube.com` and `com.minekube.craftless` for
public domain/package references.

Describe the bridge as evidence infrastructure only, not as a gameplay action
adapter. Gameplay examples must use the generated Fabric runtime graph/OpenAPI
path, not HMC bridge helpers.

Describe multi-version support as version-agnostic system work first:
manifests, Java/runtime selection, Fabric Loader/API resolution, driver mod
manifests, compatibility lanes, and runtime graph evidence. When documenting
latest/current or older-version progress, name the exact evidence gate:
preflight, compile, launch, attach, generated OpenAPI, generated
actions/resources, SSE, packaged distribution, or gameplay.

When adding a new phase, update `docs/superpowers/phase-index.md` and
`docs/project-completion-checklist.md`. Do not append phase history to root
`AGENTS.md` or grow module-local `AGENTS.md` files with per-phase status.

Verification: `git diff --check`.

## `daemon/`

`daemon/` owns the local supervisor/session API and wires clients to driver
sessions.

Use Ktor Server for HTTP routes and Ktor Client in tests. Do not add OkHttp,
Java `HttpClient`, `com.sun.net.httpserver`, or hand-rolled HTTP clients.

Keep the stable supervisor API separate from generated per-client API. Do not
add public static route families such as `/clients/{id}/player/sendChat`; use
action descriptors plus `POST /clients/{id}:run`, with generated aliases only
when described by OpenAPI.

Keep version-specific knowledge in resolver data and services: Mojang
manifests, Fabric loader/API resolution, Java runtime selection, driver mod
manifests, and compatibility probes. Do not branch daemon routes, session APIs,
action catalogs, or client-management behavior per Minecraft version.

The daemon must surface whether a prepared runtime session was replaced by an
attached in-client driver before generated gameplay OpenAPI/actions/resources
are claimed as working.

Verification: `mise exec -- gradle :daemon:test`.

## `protocol/`

`protocol/` owns Craftless's machine-readable API metadata and serializable
protocol DTOs.

Keep OpenAPI and action descriptors authoritative for agents, SDKs, adaptive
CLI dispatch/help, and tests. Keep HTTP verbs as protocol data strings such as
`"GET"` and `"POST"`; do not introduce a Craftless-owned HTTP method enum.

Public protocol names must be Craftless-owned. Do not expose Fabric, Yarn,
Minecraft implementation, HMC-Specifics, or launcher names in public metadata.

Keep protocol DTOs version-agnostic. Version-specific facts should be data in
runtime metadata, source evidence, fingerprints, availability reasons, graph
nodes, schemas, and descriptors, not new static enums or duplicated DTOs.

Avoid protocol shortcut concepts that encode one gameplay plan. The protocol
should make live affordances discoverable and invocable so agents can compose
tasks outside the product.

Verification: `mise exec -- gradle :protocol:test`.

## `driver-api/`

`driver-api/` owns the stable JVM contract between daemon/runtime code and any
in-client automation implementation.

Keep the public driver contract small and descriptor-driven: runtime metadata,
action discovery, generic action invocation, events, session state, and
lifecycle.

Do not grow one stable Kotlin method per Minecraft action. Chat, raycast,
inventory, block, entity, recipe, navigation, and similar affordances must
arrive through discovered actions, resources, handles, and schemas.

Keep the contract Minecraft-version-neutral. Version-specific divergence
belongs in driver implementations and compatibility lanes as data, metadata,
availability, graph evidence, or backend adapter behavior.

Verification: `mise exec -- gradle :driver-api:test`.

## `driver-runtime/`

`driver-runtime/` adapts the stable `driver-api` contract to concrete backends.

Keep runtime adapters version-neutral. Minecraft/Fabric version divergence
should arrive as runtime metadata, capability graph nodes, compatibility lane
decisions, or backend-specific adapters, not duplicated session mechanics.

Keep bridge details internal. Public results, events, actions, and errors must
stay Craftless-owned. The bridge backend is evidence infrastructure only.

Runtime should route stable lifecycle calls, expose the graph, and dispatch
generic invocations. Do not compensate for a weak backend by adding
runtime-side static gameplay branches.

Verification: `mise exec -- gradle :driver-runtime:test`.

## `driver-fabric/`

`driver-fabric/` owns the Fabric/Loom in-client driver module.

Keep the durable design version-agnostic: shared discovery, projection,
invocation, event, and transport code is the default. Add per-version code only
where Minecraft, Fabric API, mappings, or bytecode signatures actually diverge
and a shared reflection/compatibility shim is not practical.

Do not expose Fabric, Yarn, intermediary, or Minecraft implementation names as
public action IDs, routes, CLI commands, or docs. Keep Minecraft calls on the
client thread.

Work on generic discovery/projection/invocation first. Do not add a new public
gameplay action by hand-writing one descriptor plus one binding, and do not
register static placeholder descriptors for future gameplay actions.

When version-specific code is unavoidable, isolate only the diverging adapter,
accessor, mixin, or provider behind a lane boundary. Keep action/resource
naming, schemas, invocation dispatch, Ktor loopback, self-attach, and OpenAPI
projection shared.

Shared Fabric Loader identity, installed-mod fingerprints, runtime metadata
snapshots, deterministic fingerprint helpers, protocol-level graph
composition, and shared non-gameplay graph fragments belong in
`driver-fabric-discovery`.

Verification: `mise exec -- gradle :driver-fabric:test`.

## `driver-fabric-discovery/`

`driver-fabric-discovery/` owns shared Fabric Loader/runtime discovery code
that is reusable across current, older, latest/current, and future Fabric
driver lanes.

Keep this module free of Yarn, intermediary, official-mapping, and Minecraft
game-class calls. Lane modules may pass lane-specific mappings fingerprints,
registry probes, server-feature probes, and execution adapters into shared
metadata helpers.

Do not add gameplay actions, action descriptors, scenario shortcuts, CLI
behavior, public route families, or version-specific public APIs here.

Shared runtime graph projection may compose protocol-level resource,
operation, handle, and event nodes passed in by lanes, but this module must not
discover Minecraft game classes or mint gameplay action catalogs itself.

Verification: `mise exec -- gradle :driver-fabric-discovery:test`.

## `driver-fabric-official/`

`driver-fabric-official/` is an internal latest/current Fabric lane build
boundary for Minecraft 26.x official/unobfuscated mappings.

Treat this module as a compatibility lane and probe boundary. The durable
implementation belongs in shared Fabric attach/runtime/discovery/projection
modules by default; official-lane code should exist only for proven
official-mapping, Fabric API, loader, Minecraft, or bytecode-signature
divergence.

Do not clone the Yarn/remap `driver-fabric` gameplay bindings into this module,
and do not make this module depend on the Yarn/remap lane. Extract shared
attach, transport, discovery/projection/invocation contracts, and generic
runtime graph plumbing into common modules.

Do not package this module as a supported driver lane or add it to
`driver-mods.json` until launch, self-attach, generated OpenAPI/actions,
resources, SSE, and public API/CLI gameplay evidence pass.

Verification: `mise run fabric-lane-check-latest-official`.

## `driver-fabric-attach/`

`driver-fabric-attach/` owns version-neutral Fabric self-attach and Ktor
loopback transport shared by all Fabric driver lanes.

Keep this module free of Minecraft, Fabric API, Yarn, intermediary, and
official-mapping implementation calls. Do not add gameplay bindings, action
descriptors, runtime graph operation catalogs, per-version route trees,
scenario shortcuts, or CLI behavior here.

Use Ktor Client and Ktor Server only for HTTP transport. Keep route shapes tied
to the stable driver session contract: snapshot, connect, actions, runtime
metadata, runtime graph, generic invoke, stop, and events.

If a Fabric lane needs version-specific behavior, keep that behavior in the
lane adapter and pass a stable `DriverSession` into this module.

Verification: `mise exec -- gradle :driver-fabric-attach:test`.

## `cli/`

`cli/` owns the JVM `craftless` command-line interface.

Use Clikt for the JVM CLI, Mordant for terminal output, and Ktor Client for
daemon/API calls.

Keep action commands adaptive. Static commands may cover daemon startup,
config, auth, lifecycle, discovery, output modes, and generic action
invocation. Gameplay commands/help/aliases must come from
`/openapi.json`, `/clients/{id}/openapi.json`, and `/clients/{id}/actions`.

Do not add static CLI command trees for Minecraft versions, loaders, Fabric
lanes, survival scenarios, material recipes, combat flows, or
Minecraft-version workarounds.

Verification: `mise exec -- gradle :cli:test`.

## `testkit/`

`testkit/` owns fake clients, fake servers, fixtures, and integration helpers.

Fakes should exercise the same action descriptor and generic invocation paths
as real drivers. Fake driver sessions belong here, not in product
driver/runtime modules.

Keep tests deterministic and offline by default. Do not hide product behavior
in test-only shortcuts that bypass public protocol or driver contracts.

Version compatibility fixtures should validate resolver, lane, metadata, and
attach behavior through public contracts. Do not fake success by hard-coding
gameplay catalogs or bypassing generated OpenAPI/action/resource discovery.

Test helpers may compose public gameplay scenarios for verification, but they
must do it as an outside user or agent through OpenAPI/actions/resources,
JSON-RPC/SSE, and adaptive CLI/API calls.

Verification: `mise exec -- gradle :testkit:test`.

## `bridge-hmc/`

`bridge-hmc/` is temporary evidence infrastructure for launching and
controlling real clients before the Fabric driver is complete.

Never expose HeadlessMC or HMC-Specifics command strings as public API names,
JSON fields, CLI verbs, SDK methods, or docs contracts.

Do not use bridge behavior to justify product API shape, version support, or
gameplay completion. If a bridge smoke discovers a useful primitive, move the
product work into the Fabric runtime graph, generated OpenAPI, and generic
invocation path.

Keep real-client smoke tests opt-in and guarded by environment variables.
Default tests must not download Minecraft/server artifacts or launch a real
client.

Verification: `mise exec -- gradle :bridge-hmc:test`.

## `playwright/`

`playwright/` contains external helper tests and fixtures.

Use Bun through mise: `mise exec -- bun ...`. Do not use npm, npx, yarn, pnpm,
or globally installed node.

Do not reintroduce a TypeScript SDK as an active product surface unless the
project direction changes explicitly.

Helpers should speak the daemon/OpenAPI/action API directly. Do not parse human
CLI output, add TypeScript-side static gameplay catalogs, or become a hidden
SDK.

Verification: `mise exec -- bun test playwright`.
