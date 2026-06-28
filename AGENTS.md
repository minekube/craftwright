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

The active product-completion sequence is the numbered spec/plan pairs under
`docs/superpowers/specs/` and `docs/superpowers/plans/`. Follow them in order:

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
52. stable Fabric version boundary guard.
53. matrix-authoritative Fabric provider selection.
54. public-agent timeout boundary.
55. public-agent pickup convergence.
56. final gameplay timeout budget.
57. final gameplay child environment isolation.
58. public-agent blocked outcome propagation.
59. pathfinder interaction goal.
60. final gameplay join handoff.
61. local server action environment boundary.
62. final gameplay activity hold.
63. public-agent partial recipe material.
64. public-agent live co-play guidance.
65. Codex evidence completion gate.
66. representative older release lane evidence.
67. final gameplay Codex evidence default.
68. full Codex evidence gate refresh.
69. README and roadmap evidence alignment.
70. public-agent operational workflow guidance.
71. system Java PATH discovery.
72. generated actions help.
73. asset object integrity resume.
74. metadata binary checksums.
75. post-cache-integrity evidence refresh.
76. completion audit and binding exit.
77. graph-owned Fabric actions.
78. graph-native Fabric schemas.
79. graph-owned Fabric invoke dispatch.
80. action discovery deletion.
81. HMC bridge gameplay removal.
82. README public entrypoint overhaul.
83. Fabric binding descriptor removal.
84. bootstrap operation definition isolation.
85. binding operation id source ownership.
86. Fabric adapter key source ownership.
87. backend operation id source ownership.
88. binding adapter key derivation removal.
89. navigation operation id source ownership.
90. smoke bootstrap action id source ownership.
91. version support completion gate.
92. build-generated compiled lane metadata.
93. static unsupported version lane removal.
94. Fabric API cache resolution.
95. launch mod materialization.
96. Craftless driver mod launch artifact.
97. packaged driver mod distribution.
98. driver attach proxy.

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
successful public-agent gameplay run by itself, and must preserve the
diagnostic confirmation/timeout artifact semantics.
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
Phase 52 makes the selector boundary enforceable across the stable top-level
Fabric production package. `FabricBootstrapSelector.kt` is the only stable
Fabric production file that may import version-scoped implementation packages;
other stable Fabric files must depend on the selector or stable runtime
contracts. It must not add a new compiled lane, claim new Minecraft version
support, add public version-specific APIs, or add gameplay actions.
Phase 53 makes Fabric runtime provider selection obey the compatibility matrix
before provider-reported support. A provider must not activate an unsupported
or missing matrix lane, and its id must match the supported lane provider id.
It must not add a new compiled lane, claim new Minecraft version support, add
public version-specific APIs, or add gameplay actions.
Phase 54 keeps final gameplay generated-action request failures inside the
public-agent helper evidence path. Public-agent request timeouts must resolve
before the Fabric smoke controller times out the helper process so blocker
artifacts are written. It must not add public gameplay actions, generated route
families, CLI gameplay catalogs, Fabric descriptor/binding pairs, scenario
shortcuts, new compiled lanes, public version-specific APIs, or new Minecraft
support claims.
Phase 55 improves external public-agent pickup convergence for visible dropped
items, including elevated drops that require generated `player.move` with
jump. It composes existing generated `entity.query`, `player.query`,
`player.look`, `player.move`, and `inventory.query` only. It must not add
pickup or collection shortcut actions, public gameplay actions, generated route
families, CLI gameplay catalogs, Fabric descriptor/binding pairs, scenario
shortcuts, new compiled lanes, public version-specific APIs, or new Minecraft
support claims.
Phase 56 separates final gameplay timeout budgets so the outer local-smoke
process cannot kill the client before the post-gameplay human confirmation
hold can write `final-gameplay-confirmation-timeout.json`. It must keep
per-action generated HTTP request timeout, public-agent helper process timeout,
human confirmation hold, and outer process timeout distinct. It must not add
gameplay shortcuts, public gameplay actions, generated route families, CLI
gameplay catalogs, Fabric descriptor/binding pairs, scenario shortcuts, new
compiled lanes, public version-specific APIs, or new Minecraft support claims.
Phase 57 isolates Fabric smoke child command environments so public-agent and
ready-notification subprocesses cannot inherit local-server/final-gameplay
owner flags and accidentally start another server or clear shared evidence
artifacts. It must preserve explicit child-specific environment variables such
as public-agent base URL, client id, artifact directory, request timeout, and
ready notification context. It must not add gameplay shortcuts, public gameplay
actions, generated route families, CLI gameplay catalogs, Fabric
descriptor/binding pairs, scenario shortcuts, new compiled lanes, public
version-specific APIs, or new Minecraft support claims.
Phase 58 makes a process-external public-agent `BLOCKED` outcome fail the
Fabric final-gameplay controller before it writes `final-gameplay-ready.json`
or enters the Robin confirmation hold. It must parse already-written
public-agent artifact evidence, preserve the successful ready path, and must
not add gameplay shortcuts, public gameplay actions, generated route families,
CLI gameplay catalogs, Fabric descriptor/binding pairs, scenario shortcuts, new
compiled lanes, public version-specific APIs, or new Minecraft support claims.
Phase 59 makes the private Fabric pathfinder adapter prefer an
interaction-reachable block goal when a runtime pathfinder exposes one. It must
keep the public action surface as generic `navigation.plan` and
`navigation.follow`, keep backend names private, preserve exact-block fallback,
and must not add gameplay shortcuts, public gameplay actions, generated route
families, CLI gameplay catalogs, Fabric descriptor/binding pairs, scenario
shortcuts, new compiled lanes, public version-specific APIs, or new Minecraft
support claims.
Phase 60 makes the final gameplay ready handoff explicit by writing
machine-readable and human-readable join/confirmation artifacts when the held
session is ready for optional human co-play. It must not change generated
public APIs, treat timeout as success, add gameplay
shortcuts, public gameplay actions, generated route families, CLI gameplay
catalogs, Fabric descriptor/binding pairs, scenario shortcuts, new compiled
lanes, public version-specific APIs, or new Minecraft support claims.
Phase 61 corrects the local-server smoke action-command process boundary. It
must strip outer local-server lifecycle ownership and recursive action-command
variables, preserve final-gameplay/Fabric client smoke/public-agent child
variables for the configured client action command, and must not add gameplay
shortcuts, public gameplay actions, generated route families, CLI gameplay
catalogs, Fabric descriptor/binding pairs, scenario shortcuts, new compiled
lanes, public version-specific APIs, or new Minecraft support claims.
Phase 62 keeps the final gameplay diagnostic hold alive during active Minecraft
chat play. It may extend the held API session after observed chat activity,
but it must not treat activity or timeout as completion, and must not add gameplay
shortcuts, public gameplay actions, generated route families, CLI gameplay
catalogs, Fabric descriptor/binding pairs, scenario shortcuts, new compiled
lanes, public version-specific APIs, or new Minecraft support claims.
Phase 63 lets the external public-agent runner continue from partial public
material evidence into generic recipe discovery/crafting when the live client
exposes `recipe.query` and `recipe.craft`. It must stay external agent policy,
must require public inventory evidence before continuing, and must not add
material-specific actions, gameplay shortcuts, generated route families, CLI
gameplay catalogs, Fabric descriptor/binding pairs, scenario shortcuts, new
compiled lanes, public version-specific APIs, or new Minecraft support claims.
Phase 64 records live co-play guidance for external public agents after the
held final gameplay run exposed an invalid first `navigation.plan` body and
overbroad stop-word handling. It must stay in repo-local agent guidance and
project docs only. Agents must read generated OpenAPI schemas, use the
generated block-goal shape for `navigation.plan`, verify movement with public
state, treat only clear standalone chat messages as stop commands, and use
Minecraft chat/Craftless events instead of local OS speech once a human is in
the server. It must not add gameplay shortcuts, public gameplay actions,
generated route families, CLI gameplay catalogs, Fabric descriptor/binding
pairs, scenario shortcuts, new compiled lanes, public version-specific APIs,
or new Minecraft support claims.
Phase 65 replaces the old human chat completion gate with a Codex-verifiable
evidence gate. Completion now requires current CI, distribution smoke checks,
multi-version compatibility probes including the 26.x/latest lane and
representative older releases, and final honest survival gameplay
driven through public OpenAPI/CLI/SSE only. Human co-play remains optional
diagnostic evidence and must not be required for goal completion. This phase
must not add gameplay shortcuts, public gameplay actions, generated route
families, CLI gameplay catalogs, Fabric descriptor/binding pairs, scenario
shortcuts, new unsupported Minecraft claims, or server-provisioned final
gameplay evidence.
Phase 66 keeps representative older-release compatibility evidence honest. A
real older Minecraft release such as `1.20.6` must be represented as a real
runtime input with its Java requirement and `UNSUPPORTED`/machine-readable
reason when no compatible Fabric client lane exists. It must not be described
as broad version support, must not add a new compiled lane, and must not add
public gameplay actions, generated route families, CLI gameplay catalogs,
Fabric descriptor/binding pairs, scenario shortcuts, or public version-specific
APIs.
Phase 67 makes Codex-verifiable evidence the default final gameplay behavior
in code, not only docs. The final gameplay Gradle task must not inject a
default human confirmation phrase, reminder loop, or OS voice prompt. Chat
confirmation may remain only as an explicit opt-in diagnostic path through
environment variables. This phase must not add public gameplay actions,
generated route families, CLI gameplay catalogs, Fabric descriptor/binding
pairs, scenario shortcuts, new compiled lanes, public version-specific APIs,
or new Minecraft support claims.
Phase 68 refreshes the concrete Codex evidence gate after the Phase 67 default
change. It records distribution, installer, Docker, compatibility, CI, and
final public gameplay evidence. It must not add product behavior, must not
weaken any generated-API invariant, and must not treat unsupported version
lanes as supported runtime breadth.
Phase 69 aligns public README and roadmap text with current Phase 68 evidence.
It must describe verified no-confirmation final gameplay and unsupported
latest/older compatibility lanes accurately, must not present legacy
provisioned-item diagnostics as product proof, and must not add product
behavior, public gameplay actions, generated route families, CLI gameplay
catalogs, Fabric descriptor/binding pairs, scenario shortcuts, new compiled
lanes, public version-specific APIs, or new Minecraft support claims.
Phase 70 strengthens the repo-local public gameplay agent skill with
operational generated API, adaptive CLI, POST JSON-RPC-style control, SSE, and
artifact guidance. It must not add product behavior, public gameplay actions,
generated route families, CLI gameplay catalogs, Fabric descriptor/binding
pairs, scenario shortcuts, new compiled lanes, public version-specific APIs,
or new Minecraft support claims.
Phase 71 improves supervisor/runtime Java resolution by letting the system
provider discover `java`/`java.exe` from `PATH` and validate candidates through
the existing bounded `ProcessBuilder` validator. Repository tooling still runs
through `mise`, and this phase must not add public gameplay actions, generated
route families, CLI gameplay catalogs, Fabric descriptor/binding pairs,
scenario shortcuts, new compiled lanes, public version-specific APIs, or new
Minecraft support claims.
Phase 72 improves adaptive CLI usability by making `clients <id> actions
--help` render action commands, argument flags, and route evidence from the
live per-client OpenAPI document. It must preserve JSON output for
`clients <id> actions`, must not add static gameplay command catalogs, and must
not add public gameplay actions, generated route families, Fabric
descriptor/binding pairs, scenario shortcuts, new compiled lanes, public
version-specific APIs, or new Minecraft support claims.
Phase 73 improves latest-version cache resumability by carrying Minecraft
asset-object SHA-1 evidence into cache artifacts and re-fetching corrupt cached
asset objects instead of blindly reusing any existing file. It must not add
public gameplay actions, generated route families, CLI gameplay catalogs,
Fabric descriptor/binding pairs, scenario shortcuts, new compiled lanes, public
version-specific APIs, or new Minecraft support claims.
Phase 74 extends cache integrity to upstream metadata-backed binary downloads:
Minecraft client jars, Minecraft libraries and native classifiers, Java
runtime raw files, and Fabric profile artifact downloads when SHA-1 metadata is
present. It must not add public gameplay actions, generated route families,
CLI gameplay catalogs, Fabric descriptor/binding pairs, scenario shortcuts,
new compiled lanes, public version-specific APIs, or new Minecraft support
claims.
Phase 75 refreshes the full Codex evidence gate after the Phase 73 and Phase
74 cache-integrity changes. It records current distribution, installer, Docker,
live Mojang manifest, compatibility matrix/probe, CI, and final public
gameplay evidence from the current code. It must not add product behavior,
public gameplay actions, generated route families, CLI gameplay catalogs,
Fabric descriptor/binding pairs, scenario shortcuts, new compiled lanes,
public version-specific APIs, or new Minecraft support claims.
Phase 76 audits the current completion state after Phase 75 and keeps the
remaining binding-exit work explicit. It may mark current distribution,
transport, tooling, compatibility-evidence, and final public gameplay gates as
verified, but it must keep the overall goal active while public gameplay
breadth still depends on the transitional hand-written Fabric action allowlist
or while latest/older lanes are only explicit unsupported evidence. It must not
add product behavior, public gameplay actions, generated route families, CLI
gameplay catalogs, Fabric descriptor/binding pairs, scenario shortcuts, new
compiled lanes, public version-specific APIs, or new Minecraft support claims.
Phase 77 makes Fabric's public `actions()` descriptors graph-owned by
projecting from `RuntimeCapabilityGraph.operations` instead of exposing
`FabricActionBinding` descriptors directly. Transitional Fabric bindings may
remain as private execution adapters during this phase, but they must not own
the public action descriptor source. This phase does not complete the broader
binding exit while schemas and future gameplay breadth still depend on
hand-maintained bootstrap code. It must not add public gameplay actions,
generated route families, CLI gameplay catalogs, Fabric descriptor/binding
pairs, scenario shortcuts, new compiled lanes, public version-specific APIs,
or new Minecraft support claims.
Phase 78 makes Fabric runtime graph operation schemas graph-native by removing
`FabricActionBinding` maps from `FabricCapabilityProbeContext` and describing
the current bootstrap operation arguments/results as `RuntimeSchema` in the
graph discovery layer. Transitional Fabric bindings may remain as private
execution adapters, but graph schema construction must not read binding
descriptors or bootstrap public action descriptors. This phase still does not
complete the broader binding exit while future gameplay breadth depends on
hand-maintained bootstrap code instead of generic runtime discovery. It must
not add public gameplay actions, generated route families, CLI gameplay
catalogs, Fabric descriptor/binding pairs, scenario shortcuts, new compiled
lanes, public version-specific APIs, or new Minecraft support claims.
Phase 79 makes Fabric's legacy `invoke(...)` compatibility path graph-owned by
looking up `RuntimeCapabilityGraph.operations`, enforcing graph availability,
and dispatching through private `DriverOperationAdapters`. The Fabric backend
must not accept or call `FabricActionDiscovery` for public-compatible
dispatch. Transitional Fabric bindings may remain as private adapter
implementations. Phase 80 deletes the old standalone discovery code. This
phase still does not complete the broader binding exit while
future gameplay breadth depends on hand-maintained bootstrap code instead of
generic runtime discovery. It must not add public gameplay actions, generated
route families, CLI gameplay catalogs, Fabric descriptor/binding pairs,
scenario shortcuts, new compiled lanes, public version-specific APIs, or new
Minecraft support claims.
Phase 80 deletes the stale standalone `FabricActionDiscovery` layer entirely.
Fabric public-compatible actions, availability, schemas, and invocation must
remain owned by `RuntimeCapabilityGraph` discovery/projection and private
operation adapters. The shared live-client capability snapshot belongs to the
capability-probe graph layer, not to an action-descriptor discovery layer.
This phase still does not complete the broader binding exit while future
gameplay breadth depends on hand-maintained bootstrap code instead of generic
runtime discovery. It must not add public gameplay actions, generated route
families, CLI gameplay catalogs, Fabric descriptor/binding pairs, scenario
shortcuts, new compiled lanes, public version-specific APIs, or new Minecraft
support claims.
Phase 81 removes stale gameplay affordances from the temporary HMC bridge.
The HMC bridge is lifecycle/launch evidence only: connect, stop, and runtime
metadata. It must not publish `player.chat`, `player.move`, or any other
gameplay descriptor, invocation branch, helper method, smoke-plan step, CLI
path, route, or README contract. Gameplay must come from the Fabric runtime
capability graph and generated per-client OpenAPI path. This phase must not add
public gameplay actions, generated route families, CLI gameplay catalogs,
Fabric descriptor/binding pairs, scenario shortcuts, new compiled lanes, public
version-specific APIs, or new Minecraft support claims.
Phase 82 keeps README as a clean public entrypoint after recent evidence and
bridge cleanup. README must lead with install/run/use status, generated
per-client OpenAPI, runtime capability graph ownership, lifecycle-only bridge
status, explicit unsupported version lanes, and remaining completion gates. It
must not present legacy TypeScript SDK, Homebrew, HMC gameplay, static gameplay
catalogs, scenario tasks, or server-provisioned final gameplay as active
product surfaces.
Phase 83 removes public descriptor/schema ownership from private Fabric
execution bindings. `FabricActionBinding` may identify the graph operation it
executes with a private `operationId`, but it must not expose
`DriverActionDescriptor`, `DriverActionArgument`, result descriptor metadata,
or descriptor helper functions. Schemas, availability, resource ownership, and
public descriptor projection stay graph-owned. This phase still does not finish
the broader binding exit while future gameplay breadth depends on
hand-maintained bootstrap operation definitions instead of generic runtime
discovery.
Phase 84 isolates the remaining hand-maintained bootstrap operation
definitions behind an explicit transitional graph definition layer. The live
client-state probe may select availability from runtime state, but it must not
own operation ids, Fabric adapter ids, argument/result schemas, or direct
bootstrap `RuntimeOperationNode` construction. This phase still does not finish
the broader binding exit while future gameplay breadth depends on
hand-maintained bootstrap operation definitions instead of generic runtime
discovery.
Phase 85 makes the transitional bootstrap definition layer the source for
operation id strings used by private Fabric execution bindings. Bindings may
reference `FabricBootstrapOperationIds` constants, but they must not declare
their own `operationId = "..."` literals. This phase still does not finish the
broader binding exit while future gameplay breadth depends on hand-maintained
bootstrap operation definitions instead of generic runtime discovery.
Phase 86 makes the transitional bootstrap definition layer the source for
private Fabric adapter key strings used by backend adapter registration.
`FabricDriverBackend` may register adapters with
`FabricBootstrapOperationAdapters` constants, but it must not repeat
bootstrap gameplay adapter literals such as `fabric.entity-query` or
`fabric.recipe-craft`. This phase still does not finish the broader binding
exit while future gameplay breadth depends on hand-maintained bootstrap
operation definitions instead of generic runtime discovery.
Phase 87 makes the transitional bootstrap definition layer the source for
bootstrap operation id strings used by backend adapter guard checks.
`FabricDriverBackend` may guard entity, block-query, and recipe adapters with
`FabricBootstrapOperationIds` constants, but it must not repeat bootstrap
operation id literals such as `entity.query`, `world.block.query`, or
`recipe.craft`. This phase still does not finish the broader binding exit
while future gameplay breadth depends on hand-maintained bootstrap operation
definitions instead of generic runtime discovery.
Phase 88 removes backend derivation of private Fabric adapter keys from
operation ids. `FabricDriverBackend` must register transitional binding
adapters by consuming bootstrap operation definitions, not by transforming ids
with conventions such as `fabric.${operationId.replace(".", "-")}`. This phase
still does not finish the broader binding exit while future gameplay breadth
depends on hand-maintained bootstrap operation definitions instead of generic
runtime discovery.
Phase 89 makes Fabric navigation discovery the source for current transitional
navigation and task operation ids. `FabricDriverBackend` and
`FabricClientSmokeController` may reference `FabricNavigationOperationIds`
constants, but they must not repeat operation id literals such as
`navigation.plan`, `navigation.follow`, `task.run`, or `task.status`. This
phase still does not finish the broader binding exit while future gameplay
breadth depends on hand-maintained bootstrap/navigation operation definitions
instead of generic runtime discovery.
Phase 90 makes the Fabric smoke harness consume `FabricBootstrapOperationIds`
for bootstrap action calls and required primitive checks. The smoke controller
must not repeat quoted bootstrap action ids such as `player.chat`,
`entity.query`, or `world.block.break`. This phase still does not finish the
broader binding exit while future gameplay breadth depends on hand-maintained
bootstrap/navigation operation definitions instead of generic runtime
discovery.
Phase 91 tightens completion truth for multi-version support. Unsupported
latest or representative older compatibility lanes are useful diagnostic
evidence, but they must not satisfy final completion. Final completion requires
runnable support evidence for latest/current and representative older runtime
lanes, with generated API/CLI gameplay verification on those lanes, and this
phase must not claim new support by changing docs alone.
Phase 92 removes Gradle/Kotlin compiled-lane metadata drift by generating the
Kotlin `FabricCompiledLaneMetadata` source from the same Gradle constants that
configure Loom dependencies, Fabric resource expansion, and smoke lane JSON.
It must delete the hand-written source copy, keep latest/older support lanes
explicitly open until runnable support lands, and must not claim new version
support by metadata-generation alone.
Phase 93 removes static unsupported latest/older version lanes from product
runtime code. Historical evidence may still mention `latest-release-26-2`,
`older-release-1-20-6`, `no-compatible-client-lane`, and
`runtime-lane-missing`, but active runtime code and current-facing docs must
not present those ids as a maintained product matrix. Until real provider-backed
support lands, non-provider-backed versions resolve through generic unsupported
fallbacks.
Phase 94 resolves Fabric API mod artifacts during cache preparation from Fabric
Maven metadata. Fabric API must be represented as a Fabric mod artifact and
launch mod handle, not as a static compiled-lane constant or generic classpath
library. This phase is still foundation work and must not claim latest/current
or older-version support until runnable provider-backed lanes and generated
API/CLI gameplay verification land.
Phase 95 materializes cached Fabric mod handles into the instance `mods`
directory before process launch. Resolved Fabric API and future driver/mod
artifacts must be copied through the generic launch plan, not hard-coded into
the launcher or a version-specific command path. This phase still must not
claim new Minecraft version support.
Phase 96 lets the prepared-runtime launch path include a configured Craftless
in-client driver mod as a generic `FABRIC_MOD` launch artifact. The daemon must
stay independent from `driver-fabric`; distribution or local configuration may
provide the concrete jar path. This phase wires launch artifacts only and must
not add gameplay descriptors, version-specific APIs, static action catalogs, or
new support claims.
Phase 97 packages the Fabric driver mod for normal runtime distribution and
threads driver-mod configuration from the CLI/Docker entrypoint into the daemon
provider. It must not add compile-time dependencies from `daemon` or `cli` to
`driver-fabric`; the driver jar is a runtime artifact only.
Phase 98 adds supervisor attach/proxy plumbing so a launched client driver can
replace the prepared placeholder session with a generic HTTP-backed
`DriverSession`. This is lifecycle transport infrastructure; generated
gameplay APIs still come only from the attached runtime graph/actions.

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
server-provisioned items, current multi-version compatibility evidence, and
Codex-verifiable public API/CLI final gameplay artifacts. Human Minecraft chat
confirmation is optional diagnostic evidence, not a completion requirement.

## Documentation

- Keep README and docs aligned with current architecture.
- Preserve `docs/product-positioning.md` as the Craftless naming and
  Browserless/CDP analogy record.
- Keep historical plans under `docs/superpowers/` as traceability, not current
  product truth.
- Make implemented state versus roadmap explicit.
