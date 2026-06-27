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

The active product-completion sequence is the spec/plan pairs dated 2026-06-26
and 2026-06-27 under `docs/superpowers/specs/` and
`docs/superpowers/plans/`. Follow them in order:

1. truth and guardrails;
2. runtime capability graph;
3. Fabric discovery probes;
4. projection and OpenAPI;
5. generic invocation;
6. SSE, JSON-RPC, and adaptive consumers;
7. final gameplay completion.
8. honest survival navigation correction.
9. pathfinder-backed execution.
10. diagnostic survival task execution.
11. public-agent final gameplay.
12. runtime block resource query.
13. public-agent material navigation.
14. public-agent material collection.
15. public-agent material exploration.
16. targetable generic block break.
17. public-agent action timeout blockers.
18. public-agent material equip.
19. sustained generic block break.
20. public-agent material pickup.
21. public-agent drop perception.
22. bounded material exploration.
23. runtime entity attack.
24. targetable block interact.
25. distribution usability.
26. version-agnostic driver architecture.
27. Java runtime resolution.
28. generic recipe and crafting.
29. legacy survival task API removal.
30. bounded attack exploration.
31. material count evidence.
32. material reach evidence.
33. combat reach fallback.
34. incremental public-agent artifacts.
35. final confirmation timeout artifact.
36. legacy survival task namespace guard.
37. scenario shortcut action guard.
38. combat miss retry.
39. Fabric library replacement.
40. rule-selected native libraries.
41. launch argument placeholders.
42. standard asset object layout.
43. client logging config.
44. asset index id.
45. descriptor-derived graph schemas.
46. compiled Fabric lane metadata.
47. compiled Fabric resource metadata.
48. stable Fabric entrypoint boundary.
49. README current status alignment.
50. latest release lane evidence.
51. Fabric bootstrap selection boundary.

Do not implement a later phase before its spec and plan are written and the
earlier phases are either complete or explicitly carried as active blockers in
`docs/project-completion-checklist.md`.

The Phase 8 correction exists because the first live gameplay gate exposed
that a provisioned iron sword is not honest completion evidence. Final
completion must not depend on server-side item provisioning, pre-seeded
inventory, manual movement for Craftless, or static shortcut actions such as
`kill.cow`, `find.tree`, or `craft.sword`.
The Phase 10 diagnostic harness exposed more missing primitives and is not the
durable completion boundary. Phase 11 is the corrected final completion path.
Phase 12 resolves the first public-agent blocker by adding generic block
resource perception through the runtime graph and graph adapter path, not by
adding scenario-specific survival actions.
Phase 13 composes public block perception with navigation through the generated
API as an external agent policy. It still must not add `find.tree`,
`mine.log`, or any other scenario-specific product action.
Phase 14 composes public navigation with look/raycast/block-break/inventory
verification. It must treat accepted break actions as insufficient until
public state proves inventory or block changes.
Phase 15 adds bounded generic exploration when the local material query is
empty. It must use generated player, navigation, and block-query actions only;
it must not introduce a survival macro or product actions such as `find.tree`,
`mine.log`, `collect.wood`, `craft.sword`, or `kill.cow`.
Phase 16 lets `world.block.break` target Craftless-owned block handles or
positions discovered from `world.block.query`. It must keep the generic action
id and must not add log/mining/survival-specific public actions.
Phase 17 makes public-agent generated-action request failures explicit
blockers with artifacts. It must not retry non-idempotent actions by default
because a timeout may leave action outcome ambiguous.
Phase 18 composes collected public inventory evidence with generic
`inventory.equip` and follow-up inventory verification. It must not add
material-specific equip actions, crafting shortcuts, combat shortcuts, or a
survival macro.
Phase 19 corrects `world.block.break` so it can drive bounded generic breaking
progress and report observed block-state change. It must keep the generic
action id and must not add material-specific mining actions.
Phase 20 composes public material pickup by choosing reachable queried targets
and moving through generic navigation before inventory verification. It must
not add pickup or collection shortcut actions.
Phase 21 uses public `entity.query` perception to find dropped material
entities and navigate to their positions before inventory verification. It must
not add pickup or collection shortcut actions.
Phase 22 makes public-agent material exploration use smaller overlapping
generated-navigation steps. It must not increase product API breadth or add
search shortcuts such as `find.tree`.
Phase 23 adds generic `entity.attack` through the runtime graph and generated
per-client action path. It must consume public entity handles from
`entity.query` and must not add `kill.cow`, combat macros, or survival
scenario actions.
Phase 24 makes `world.block.interact` targetable with Craftless-owned block
handles/positions and state-change evidence. It must not add `build.house`,
`place.log`, or other building scenario shortcuts.
Phase 25 adds distribution surfaces: release workflow, install script, runtime
Docker image, reusable GitHub Action, and README quickstarts. Docker must copy
the already-built Craftless CLI distribution and must not compile the project
inside the image. Do not add Homebrew in this phase.
Phase 26 prevents the Fabric driver from hardening around one Minecraft
version. Version-specific code belongs behind internal runtime/provider
facades, compatibility matrix evidence, and private probe metadata. Do not add
new version support by expanding public action catalogs, public route families,
or scenario shortcuts.
Phase 27 makes Java runtime selection an explicit supervisor/runtime concern.
Minecraft version metadata determines Java requirements, and Craftless selects
or prepares a validated compatible runtime through configured, managed, mise,
or system providers. `mise` remains mandatory for repository tooling but must
not be the only product runtime provider. Do not paper over Java-version
failures with ad hoc environment overrides or by silently using the repository
build JVM.
Phase 28 adds generic recipe discovery and crafting through the runtime graph.
It must expose Craftless-owned recipe handles and generic actions such as
`recipe.query` and `recipe.craft` only when discovered from the live client
recipe, inventory, screen, handler, and permission state. It must not add
`craft.sword`, `craft.planks`, `craft.table`, `make.weapon`, or a survival
macro. Until a real live recipe probe and executor exist, recipe graph nodes
must remain unavailable with machine-readable reasons instead of returning
placeholder recipes or placeholder crafting success.
Phase 29 removes the diagnostic `task.survival.*` product path. The final
survival acceptance scenario must be composed outside the driver through the
generated public API, adaptive CLI, SSE events, and agent skills. Do not keep
`task.survival.honest-cow-hunt`, survival resource handles, cow-hunt task
executors, or smoke-run invocations as active product behavior.
Phase 30 makes public-agent combat search continue through bounded generated
navigation and `entity.query` rings when the first waypoint ring finds only
dropped items or non-evidence aquatic entities. It must stay external agent
policy and must not add `find.cow`, `kill.cow`, `hunt.animal`, aquatic combat
shortcuts, survival macros, or any new product gameplay action.
Phase 31 makes repeated public material collection require an increased
`inventory.query` material count over the count known before the current break.
It must stay external agent policy and must not add `collect.wood`, `mine.log`,
recipe-material shortcuts, survival macros, or any new product gameplay action.
Phase 32 makes public-agent material collection verify public player position
after generated navigation before sending `world.block.break`. It must stay
external agent policy, must not trust `navigation.follow` success when
`player.query` proves the target is still outside break reach, and must not add
reach shortcuts, mining shortcuts, survival macros, or any new product gameplay
action.
Phase 33 makes public-agent combat use a bounded generic `player.move` nudge
when generated navigation reports success but public `entity.query` and
`player.query` still prove the target is outside generated attack reach. It
must stay external agent policy, must re-query public state before
`entity.attack`, and must not add `find.cow`, `kill.cow`, combat shortcuts,
survival macros, or any new product gameplay action.
Phase 34 makes public-agent gameplay artifacts incremental so long generated
actions leave evidence before the runner exits. It must stay evidence plumbing,
must keep generated action invocation through `POST /clients/{id}:run`, and
must not add pathfinder-specific public API, survival macros, or any new
product gameplay action.
Phase 35 makes the final Robin-confirmation hold outcome explicit when no
matching Minecraft chat confirmation arrives before the configured deadline.
It must write evidence only, must not mark Craftless complete, must not fail a
successful public-agent gameplay run by itself, and must not bypass the
required Robin chat confirmation.
Phase 36 rejects removed `task.survival.*` scenario ids at the protocol task
request and progress-event boundaries. It must keep generic future task ids
valid as metadata, must not add a generic task executor, and must not
reintroduce survival task macros as valid public API.
Phase 37 rejects known scenario shortcut action ids such as `find.tree`,
`mine.log`, `craft.sword`, and `kill.cow` at the shared public action-id
boundary. It must preserve generic runtime primitives such as `recipe.craft`,
`inventory.equip`, `world.block.break`, and `entity.attack`, and must not turn
the shortcut blocklist into a public gameplay catalog.
Phase 38 makes the external public-agent combat loop recover when generated
`entity.attack` reports `hit=false` during a bounded combat attempt. It must
refresh public `entity.query` evidence, re-focus through generated navigation
and optional `player.move`, retry only within the configured evidence budget,
and must not add `kill.cow`, combat macros, scenario shortcuts, or any new
product gameplay action.
Phase 39 keeps Fabric launch preparation version-aware by allowing Fabric
loader-profile libraries to replace duplicate Minecraft libraries with the same
Maven group and artifact. It must affect cache preparation and launch
classpath construction only, must preserve non-duplicate Minecraft libraries,
and must not encode one Minecraft version, one Fabric loader version, or any
public gameplay behavior into daemon logic.
Phase 40 keeps cache preparation compatible with newer Minecraft metadata that
publishes platform-native libraries as rule-selected artifact libraries. It
must honor Mojang library rules, keep native artifacts out of the Java
classpath, extract selected natives into native directories, and must not
hard-code one operating system, architecture, Minecraft version, or launcher
classpath.
Phase 41 keeps prepared client process launches compatible with Mojang launch
argument placeholders that depend on the selected profile and instance files.
It must resolve launch-time placeholders in the supervisor/client-runtime
layer, omit optional empty quick-play flags, and must not add gameplay API
breadth, Fabric action descriptors, or static CLI gameplay behavior.
Phase 42 keeps supervisor-prepared Minecraft assets compatible with the
standard client asset resolver by storing asset objects at
`assets/objects/<first-two-hash-chars>/<hash>`. It must derive paths from the
Mojang asset index hash, validate hashes before using them as handles, and
must not add public gameplay API, Fabric action descriptors, or custom asset
serving routes.
Phase 43 keeps supervisor-prepared launches compatible with Mojang client
logging metadata by caching `logging.client.file` and appending the resolved
`logging.client.argument` to prepared JVM launch arguments. It must validate
logging file ids before deriving handles, stay in supervisor cache/launch
metadata, and must not add public gameplay API, Fabric action descriptors,
custom logging APIs, or static CLI gameplay behavior.
Phase 44 keeps supervisor-prepared Minecraft asset indexes compatible with
Mojang version metadata by using `assetIndex.id` for prepared asset index
handles and `${assets_index_name}` launch variable resolution. It must validate
asset index ids before deriving handles, stay in supervisor cache/launch
metadata, and must not add public gameplay API, Fabric action descriptors,
custom asset serving APIs, version-specific hard-coded asset ids, or static CLI
gameplay behavior.
Phase 45 makes Fabric runtime graph operation schemas derive from existing
discovered Craftless action descriptors when descriptors are already available.
It must reduce duplicate schema metadata in graph projection, preserve
specialized runtime-only schemas, and must not add public gameplay actions,
generated route families, CLI gameplay catalogs, Fabric descriptor/binding
pairs, or scenario shortcuts.
Phase 46 centralizes Kotlin-side metadata for the current compiled Fabric lane
so runtime matrix, provider selection, and smoke/final gameplay plans do not
drift independently. It must not parameterize Loom compilation, claim new
Minecraft version support, add public version-specific APIs, or add gameplay
actions.
Phase 47 expands Fabric resource metadata from Gradle build-time compiled-lane
values so the mod descriptor stays aligned with the verified compiled lane. It
must not change the compiled versions, claim new Minecraft version support,
parameterize Loom for arbitrary user-selected versions, add public
version-specific APIs, or add gameplay actions.
Phase 48 keeps Fabric mod metadata pointed at a stable Craftless entrypoint
while current compiled-lane startup stays behind an internal versioned
bootstrap boundary. It must leave bytecode-sensitive mixins/accessors
version-scoped, must not claim new Minecraft version support, and must not add
public version-specific APIs or gameplay actions.
Phase 49 keeps README examples and status aligned with current implementation
and final evidence. It must not present legacy diagnostic provisioning,
removed SDK surfaces, bridge paths, or scenario tasks as active product
behavior, and must not add gameplay actions.
Phase 50 keeps latest-release compatibility evidence honest. A real latest
Minecraft release such as `26.2` must be represented as a real runtime input
with `UNSUPPORTED` and a machine-readable reason when no compatible Fabric
client lane exists; it must not be described as simulated support, must not
claim new version support, and must not add gameplay actions.
Phase 51 keeps the stable Fabric entrypoint behind a non-versioned internal
bootstrap selector. The entrypoint must not import a version-scoped bootstrap
directly; current compiled-lane startup remains registered behind the selector.
It must not add a new compiled lane, claim new Minecraft version support, add
public version-specific APIs, or add gameplay actions.

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
reproducible from the public generated API without adding new scenario-specific
Kotlin actions.

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
- Event names, resource ids, action ids, handles, and schemas emitted on streams
  must be Craftless-owned projections from the live runtime capability graph.
  Do not stream raw Fabric/Yarn/intermediary names or Minecraft implementation
  names as public contracts.

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
small hand-written binding list or while new gameplay breadth depends on adding
more hand-written descriptor/binding pairs. Completion requires a generic
runtime capability graph, reflection/mapping/registry/callback/screen/handler
discovery, generated per-client OpenAPI from that graph, executable adapters or
probe-backed availability for advertised actions/resources, SSE event
streaming for live observations, honest survival gameplay evidence without
server-provisioned items, and Robin's explicit Minecraft chat confirmation
during the final gameplay session.

## Documentation

- Keep README and docs aligned with current architecture.
- Preserve `docs/product-positioning.md` as the Craftless naming and
  Browserless/CDP analogy record.
- Keep historical plans under `docs/superpowers/` as traceability, not current
  product truth.
- Make implemented state versus roadmap explicit.
