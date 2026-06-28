# Craftless Project Completion Checklist

This file is the active completion red line. Craftless is complete only when
the board below has no unchecked items and the final gate evidence is fresh.
Human Minecraft chat confirmation is optional diagnostic evidence and is not a
completion dependency.

Legend: `[ ]` not done, `[~]` in progress, `[x]` done with evidence, `[!]`
blocked.

## How To Use This File

- Start with the Active Completion Board. It is the source of truth for what
  remains.
- Keep this file short at the top. Put phase history in the phase sections,
  raw logs in `docs/superpowers/evidence/`, and durable rules in
  `docs/agent-operating-contract.md` or `docs/agent-module-contracts.md`.
- Do not mark completion while public gameplay breadth still depends on
  hand-maintained action catalogs, descriptor/binding pairs, scenario
  shortcuts, or one-version-only driver behavior.
- When a gate closes, link the exact phase/evidence file and name the command
  that proved it. Do not rely on memory, intent, or remote CI waiting.

## Active Completion Board

### Completion Rules

- Close items in order unless a later item is a pure documentation or guardrail
  cleanup that does not change runtime behavior.
- Every open item needs a fresh spec, plan, evidence file, focused regression
  test, and local verification command before it can move to `[x]`.
- Public gameplay breadth must come from generated runtime graph/OpenAPI data.
  Static descriptor/binding pairs, static CLI gameplay commands, scenario
  shortcuts, and per-version public APIs do not close any item here.
- Latest/current and representative older lanes must pass the same public
  API/CLI gates. A diagnostic probe or explicit unsupported result is useful
  evidence, but not completion evidence.

### Open Work Queue

1. [x] CL-01: Remove the remaining public action-list authority paths.
   Done means: daemon, CLI, public-agent runner, attach transport, and driver
   runtime code treat generated per-client OpenAPI/runtime graph as the action
   authority; `/actions` remains only a projection/debug endpoint and cannot
   be required for gameplay.
   Evidence required: a red/green guard proving public gameplay still works
   when `/actions` is empty or absent while `x-craftless-actions` is present,
   plus an architecture guard for new action-list authority uses.
   Suggested commands:
   `mise exec -- gradle :daemon:test :cli:test :testkit:test`,
   `mise run architecture-check`.
   Current evidence: Phase 171 closed daemon OpenAPI authority with
   `docs/superpowers/evidence/2026-06-28-daemon-openapi-graph-only-authority.md`;
   Phase 172 removed the remote `HttpDriverSession.actions()` fetch from the
   attach HTTP path with
   `docs/superpowers/evidence/2026-06-28-remote-driver-action-graph-authority.md`;
   Phase 173 made public-agent `/actions` projection fetches optional,
   added CLI/HTTP authority guards, and records the remaining production scan
   with
   `docs/superpowers/evidence/2026-06-28-public-agent-actions-projection-optional.md`.
2. [ ] CL-02: Finish transitional Fabric binding exit.
   Done means: new gameplay breadth is discovered/projected from Fabric
   runtime inputs such as reflection, mappings, registries, callbacks, screens,
   handlers, world/entity/inventory/client state, permissions, server
   features, and installed mods; executable adapters attach to graph nodes
   without copying a public catalog.
   Evidence required: guards that reject hand-maintained public gameplay
   descriptors and descriptor/binding pairs, plus Fabric tests proving graph
   nodes and executable adapters are derived from discovery inputs.
   Suggested commands:
   `mise exec -- gradle :protocol:test :driver-api:test :driver-fabric-discovery:test :driver-fabric:test`,
   `mise run architecture-check`.
3. [ ] CL-03: Make latest/current lane a real product lane.
   Done means: `latest-release` resolves to the current Mojang release, selects
   a packaged Craftless driver lane through the normal supervisor/CLI path,
   launches, self-attaches, exposes generated OpenAPI/actions/resources,
   streams SSE, supports JSON-RPC query/subscription, and can execute the same
   generated gameplay primitives used by the verified current lane.
   Evidence required: packaged CLI smoke, generated OpenAPI artifact,
   actions/resources projections, SSE transcript, JSON-RPC query/subscription
   transcript, runtime metadata/fingerprint, and public gameplay artifact.
   Suggested commands:
   `mise run package-cli`,
   `mise run fabric-lane-check-latest-official`,
   latest/current packaged create-client smoke,
   `mise run ci`.
4. [ ] CL-04: Make representative older lane a real product lane under the
   same gate set.
   Done means: the older lane is not a weaker smoke path. It uses the same
   cache, Java/runtime, Fabric Loader/API resolution, packaged driver
   selection, attach, generated OpenAPI, SSE, JSON-RPC, CLI, and gameplay
   verification path as latest/current.
   Evidence required: the same artifacts as CL-03 for the representative older
   Minecraft version, plus a note explaining which compatibility behavior is
   shared and which code is narrow version divergence.
   Suggested commands:
   `mise run package-cli`,
   representative older packaged create-client smoke,
   representative older final public gameplay smoke,
   `mise run ci`.
5. [ ] CL-05: Complete transport and generated-client documentation.
   Done means: README, roadmap, runbooks, and agent skill docs explain the
   stable supervisor API, live generated per-client OpenAPI, generic
   invocation, SSE, JSON-RPC query/subscription, ETag/fingerprint caching,
   adaptive CLI, Docker, install script, and reusable GitHub Action without
   implying a static gameplay SDK.
   Evidence required: protocol/daemon/CLI tests for metadata, event filters,
   correlation ids, subscriptions, generated aliases, and OpenAPI cache
   behavior; docs examples must match real command/API shapes.
   Suggested commands:
   `mise exec -- gradle :protocol:test :daemon:test :cli:test`,
   `mise exec -- bun test playwright`,
   docs grep for removed legacy/static wording.
6. [ ] CL-06: Run final local release-quality gates after CL-01 through CL-05.
   Done means all local gates pass from a clean tree without waiting on remote
   CI as proof.
   Required commands:
   `mise run lint`,
   `mise run architecture-check`,
   `mise run ci`,
   `mise run package-cli`,
   Docker runtime smoke,
   install script smoke,
   latest/current lane probe,
   representative older lane probe,
   `git diff --check`.
7. [ ] CL-07: Rerun final honest survival gameplay through public API/CLI only.
   Done means an external/public-agent path creates or attaches to a real
   client, fetches generated OpenAPI/actions/resources, subscribes to SSE or
   uses JSON-RPC subscription filters, writes chat, observes world/player/
   inventory/entity state, collects resources, crafts/equips a tool, mines or
   places blocks, attacks a target, picks up drops, and records machine
   evidence without server-provisioned inventory, manual movement, or a
   product `task.survival.*` shortcut.
   Evidence required: generated OpenAPI, actions/resources projections,
   SSE/JSON-RPC transcript, CLI/API transcript, inventory/world/entity/
   crafting/movement/combat/pickup proof, server log, and final artifact
   summary under `driver-fabric/build/craftless-final-gameplay/artifacts/`.
8. [ ] CL-08: Publish the completed state.
   Done means checklist, phase index, specs/plans, evidence, README/docs, and
   any code changes are committed and pushed directly to `main`, with the final
   commit message naming the closed CL items.
   Evidence required: clean `git status --short --branch`, local verification
   commands listed in the final evidence file, and `git push origin main`.

### Closed Gates

- [x] Root and module `AGENTS.md` files are short routing files.
- [x] Durable repository rules live in `docs/agent-operating-contract.md`.
- [x] Durable module rules live in `docs/agent-module-contracts.md`.
- [x] Repository is named Craftless and uses `com.minekube.craftless` and
  `minekube.com`.
- [x] Tooling is pinned through `mise`; Bun is used only through
  `mise exec -- bun`.
- [x] JVM HTTP surfaces use Ktor Server/Client.
- [x] CLI binary is `craftless`.
- [x] Stable supervisor OpenAPI exists at `GET /openapi.json`.
- [x] Per-client OpenAPI exists at `GET /clients/{id}/openapi.json`.
- [x] Generic invocation exists at `POST /clients/{id}:run`.
- [x] `DriverSession.actions()` defaults to runtime graph operation
  projection.
- [x] `DriverBackend.actions(clientId)` defaults to runtime graph operation
  projection.
- [x] `ClientSessionService.routesFor(clientId)` projects routes from
  generated per-client OpenAPI.
- [x] Public-agent workflow uses generated per-client OpenAPI action metadata
  instead of the `/actions` projection.
- [x] Install script, Docker runtime image, release checks, reusable GitHub
  Action, packaged CLI smoke, and agent onboarding docs have staged evidence.
- [x] Kotlin lint, formatting, unused/dead-code checks, architecture checks,
  and mise tasks exist.
- [x] Historical public API/CLI survival evidence exists without
  server-provisioned inventory, but it is not final completion evidence until
  replayed after the authority, binding-exit, and multi-version gates
  close.

## Current Baseline

Craftless currently has a Kotlin/JVM Ktor supervisor, adaptive JVM CLI,
generated per-client OpenAPI, graph-projected actions/resources, generic
invocation, SSE plus JSON-RPC-style control/query calls, packaged distribution
paths, and staged Fabric gameplay evidence. The remaining product blockers are
not basic launch or packaging. They are authority cleanup, generated gameplay
breadth, equal latest/current and older-version support, generated-client
transport documentation, and a final replay through public API/CLI only.

## Spec And Plan Inventory

Detailed spec and plan history is maintained by
`docs/superpowers/phase-index.md` and the files under
`docs/superpowers/specs/` and `docs/superpowers/plans/`. Do not duplicate the
full inventory here; add active closure gates to the board above and add phase
history below.

## Phase 1: Truth And Guardrails

- [x] `AGENTS.md`, roadmap, and this checklist define the real completion path.
- [x] Architecture checks fail if new public gameplay breadth appears only as a
  hand-written descriptor/binding pair.
- [x] README and docs no longer imply Craftless is complete before runtime
  graph, SSE stream, and final gameplay evidence are done.

Verification:

- `git diff --check`
- `rg -n "Craftless is complete|hand-written public gameplay|runtime capability graph|SSE" AGENTS.md README.md docs -S`
- `rg -n "Robin writes|Robin confirms|goal may be completed|required Robin|must.*Robin|Minecraft chat confirmation.*required" README.md AGENTS.md docs/roadmap.md docs/final-gameplay-runbook.md .agents/skills/craftless-public-gameplay-agent/SKILL.md -S`

## Phase 2: Runtime Capability Graph

- [x] Protocol graph models exist for resources, operations, handles, events,
  schemas, availability, private source evidence, and fingerprints.
- [x] Graph validation rejects duplicate ids, invalid public ids, unavailable
  nodes without reasons, and raw Fabric/Yarn/Minecraft public names.
- [x] Fingerprints change when graph node ids, schemas, availability,
  permissions, or runtime inputs change.
- [x] Driver contract can expose a graph snapshot without adding driver methods
  per gameplay action.

Verification:

- `mise exec -- gradle :protocol:test :driver-api:test`

## Phase 3: Fabric Discovery Probes

- [x] Fabric probes fill graph nodes from loader/mod metadata, registries,
  callbacks, screens, handlers, world/entity/inventory/client state, and
  permissions. Current evidence covers runtime metadata evidence, generic
  registry-family handles, client/world/player/inventory/entity/screen
  availability nodes, handles, operation events, driver-generated event-source
  nodes, Fabric API callback event-source evidence, one internal client-tick
  mixin event-source hook, and private pathfinder probe evidence.
- [x] Minecraft client state access stays on the client thread.
- [x] Probes emit private evidence and graph nodes, not public OpenAPI
  descriptors directly.
- [x] Current bootstrap gameplay affordances are represented as graph nodes.
- [x] Static descriptor/binding drift is guarded in both protocol policy tests
  and driver-fabric module tests.

Verification:

- `mise exec -- gradle :driver-fabric:test`

## Phase 4: Projection And OpenAPI

- [x] Per-client OpenAPI is generated from `RuntimeCapabilityGraph`.
- [x] `/clients/{id}/actions` and `/clients/{id}/resources` are projections of
  the same graph-generated OpenAPI.
- [x] Generated aliases, nested argument schemas, nested result schemas,
  nested resource schemas, nested handle schemas, nested event payload schemas,
  resource metadata, handle metadata, event stream metadata, and fingerprints
  come from graph projection.
- [x] Public API policy tests reject Fabric/Yarn/intermediary/raw Minecraft
  leakage in graph-projected OpenAPI.

Verification:

- `mise exec -- gradle :protocol:test :daemon:test`
- `mise run architecture-check`

## Phase 5: Generic Invocation

- [x] Generic invocation dispatches from graph operation metadata to internal
  client-thread execution adapters.
- [x] Existing bootstrap Fabric implementations are adapter inputs, not the
  public action catalog.
- [x] Unavailable, permission, schema, stale-handle, and runtime-mismatch
  errors are machine-readable. Current evidence covers unavailable and schema
  failures plus graph-projected `PERMISSION_DENIED`, `STALE_HANDLE`, and
  `RUNTIME_MISMATCH` invocation responses.
- [x] Generated-action public input failures avoid raw Kotlin exceptions before
  client scheduling. Current evidence covers `player.chat` failures for
  missing messages, blank messages, and rejected Minecraft command strings with
  machine-readable reasons across the Fabric backend and the reusable fake
  driver session. The old shared exception-only chat validator has been removed
  from `driver-api`. Phase 81 removed HMC bridge gameplay adaptation entirely.
- [x] Invocation results validate against graph-projected result schemas and
  publish correlated SSE events for generic graph invocations. Current evidence
  covers schema validation, session events, JSON-RPC correlation ids, and
  operation-typed SSE payloads.

Verification:

- `mise exec -- gradle :driver-api:test :driver-fabric:test :daemon:test`
- `mise exec -- gradle :driver-fabric:test --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricDriverModuleTest.fabric backend reports missing player chat message as action failure' --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricDriverModuleTest.fabric backend reports blank player chat message as action failure' --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricDriverModuleTest.fabric backend rejects raw minecraft command strings as chat action input'`
- `mise exec -- gradle :testkit:test --tests 'com.minekube.craftless.testkit.FakeDriverSessionTest'`

## Phase 6: SSE, JSON-RPC, And Adaptive Consumers

- [x] Daemon exposes SSE streams for supervisor and per-client live events.
- [x] Daemon exposes HTTP POST JSON-RPC-style control for invoke, subscribe,
  unsubscribe, and query. Current evidence covers invoke correlation, live
  projection query for OpenAPI/actions/resources/handles/events/subscriptions,
  and server-side subscription filters that SSE can apply by subscription id.
- [x] Event filters work server-side and client-side.
- [x] CLI can watch live events and invoke/query using live OpenAPI/stream
  metadata. Current evidence covers live event watching, invocation, and
  JSON-RPC query over live projections.
- [x] Bun helper can subscribe to events without npm, npx, yarn, pnpm, or local
  Node tooling.

Verification:

- `mise exec -- gradle :protocol:test :daemon:test :cli:test`
- `mise exec -- bun test playwright`

## Phase 7: Final Gameplay Completion

- [x] Final gameplay runbook exists in `docs/final-gameplay-runbook.md`.
- [x] Final gameplay Gradle task or run command exists for launching/attaching
  a real Craftless-controlled Fabric client and recording artifacts. Task
  registration evidence: `mise exec -- gradle :driver-fabric:tasks --group
  verification` lists `fabricFinalGameplay`.
- [~] Craftless joins a server, fetches graph-backed OpenAPI, subscribes to SSE,
  writes chat, observes world/inventory state, equips an item, mines, places a
  block, attacks an entity, and records evidence. Latest evidence is under
  `driver-fabric/build/craftless-final-gameplay/artifacts/`; human observation
  is optional diagnostic evidence rather than the completion gate.
- [~] A public-agent gameplay runner uses only the generated per-client
  OpenAPI/actions/resources, SSE/JSON-RPC events, adaptive CLI or HTTP, and
  repository agent skills/docs to complete the survival scenario. Current
  implementation evidence exists for a runner contract, JSONL artifact writer,
  process-external `:testkit:publicAgentGameplay` entrypoint, and final-harness
  artifact names `public-agent-gameplay-results.jsonl` and
  `public-agent-state.jsonl`. The Fabric final harness now injects its live
  daemon URL into that external runner while the client is connected. Latest
  no-hold live evidence from
  `driver-fabric/build/craftless-final-gameplay/artifacts/public-agent-gameplay-results.jsonl`
  shows the public agent used generated `world.block.query`,
  `navigation.plan`, `navigation.follow`, `world.block.break`, `entity.query`,
  `inventory.query`, `inventory.equip`, `world.block.interact`,
  `player.look`, `recipe.query`, `recipe.craft`, and `screen.query`; it can
  collect a bounded material buffer before recipe composition, craft planks,
  recognize station-backed recipe requirements, prefer a
  station-producing recipe before a station-backed weapon when the station is
  missing, recover dropped material through generated `player.move`, retry
  alternate public station-placement support targets, re-equip the station
  before generated block interaction, verify placed-resource state through
  generated `world.block.query` target handles, skip unnecessary post-placement
  navigation when the placed resource is already reachable, and select an
  empty public hotbar slot before opening a placed resource. It now also
  reuses generated material recipe handles after opening a station, filters
  unsuitable living targets, closes combat reach gaps with generated navigation
  or `player.move`, re-equips the generated combat slot after navigation slot
  drift, and collects visible combat loot drops through public `entity.query`,
  generated navigation, and `inventory.query` verification. The Fabric driver
  now syncs generated `inventory.equip` through the selected-slot C2S packet
  so later interactions observe the equipped slot. The latest held run has
  `publicAgentState=RAN`: it collected logs, crafted and opened a crafting
  table, crafted a `Wooden Sword`, preferred Cow as the public combat target,
  re-equipped the sword before generated `entity.attack`, killed a Cow,
  observed `Raw Beef` and `Leather` through public `entity.query`, navigated to
  the drop, and proved pickup through a final `inventory.query` containing
  `Raw Beef` and `Leather`. The held session was announced with macOS `say`,
  but no Robin join/chat confirmation was observed before the hold expired. The
  final harness now writes `final-gameplay-ready.json` and can run
  `CRAFTLESS_FABRIC_SMOKE_READY_COMMAND_JSON` with live daemon/client/server
  context before the hold window; the opt-in macOS final task defaults this to
  a concise `say` prompt. The 2026-06-27 held run also exposed that the outer
  server action timeout must cover public-agent runtime plus the full human hold
  window; the final task now separates the Fabric action timeout from the
  computed outer fixture timeout. A later 2026-06-27 held run exposed
  `insufficient-public-evidence:entity.query.attack-target.reachable` when
  generated navigation did not start and the first close-range post-move entity
  query lost the Cow; the public-agent policy now performs bounded wider
  generated `entity.query` refreshes and generic close-distance retries before
  blocking. The latest 2026-06-27 held run exited cleanly with
  `publicAgentState=RAN`: it crafted a `Wooden Sword`, found a Cow through
  generated `entity.query`, closed distance with generated navigation, attacked
  through generated `entity.attack`, observed Raw Beef as a public entity drop,
  moved to the drop, and proved pickup through `inventory.query`; Robin did not
  join or confirm in Minecraft chat before the hold ended. The final harness now
  imports server evidence while the server is still running, watches
  `server-evidence.jsonl` for the configured Robin confirmation phrase during
  the hold window, writes `final-gameplay-confirmation.json` when the chat
  confirmation is observed, and the diagnostic final task repeats the ready
  notification only when `CRAFTLESS_FABRIC_SMOKE_READY_COMMAND_JSON` and a
  positive `CRAFTLESS_FABRIC_SMOKE_READY_REMINDER_MS` are configured. A
  later 2026-06-27 held run reached the ready window but the process-external
  public agent blocked with
  `insufficient-public-evidence:inventory.query.recipe-material` because stale
  one-log inventory proof from the first material pickup was accepted as proof
  for the second material collection attempt; Phase 31 corrects this by
  requiring public material count increase evidence. The rerun after Phase 31
  reached `publicAgentState=RAN`: it collected two logs with public count
  increase proof, crafted planks, a crafting table, sticks, and a `Wooden Sword`,
  attacked a public combat target, and proved loot pickup through
  `inventory.query` showing `Raw Chicken` and `Feather`. The held session wrote
  `final-gameplay-ready.json` for `127.0.0.1:60403`; `server-evidence.jsonl`
  contains Craftless' `Player224` join, chat, and disconnect only. Robin did
  not join or confirm in Minecraft chat, and no
  `final-gameplay-confirmation.json` was written. A subsequent rerun reached
  `publicAgentState=RAN` again: it crafted a `Wooden Sword`, found a Chicken
  through generated `entity.query`, closed distance with generated navigation,
  attacked through generated `entity.attack`, observed `Raw Chicken` and
  `Feather` drops, navigated to the drops, and proved pickup through
  `inventory.query`. The held session wrote `final-gameplay-ready.json` for
  `127.0.0.1:61836`; `server-evidence.jsonl` contains Craftless' `Player546`
  join, chat, and disconnect only. Robin did not join or confirm in Minecraft
  chat, and no `final-gameplay-confirmation.json` was written. The current
  2026-06-27 held run reached `publicAgentState=RAN` again on
  `127.0.0.1:63310`: Craftless' `Player665` joined, fetched the generated
  OpenAPI/action/event projections, collected materials, crafted/equipped a
  `Wooden Sword`, found a Chicken through generated `entity.query`, closed
  distance with generated navigation, attacked through generated
  `entity.attack`, observed `Raw Chicken` and `Feather` drops through public
  entity state, and reached the ready window with
  `final-gameplay-ready.json`. The hold ended cleanly after the configured
  window; `server-evidence.jsonl` contains Craftless' `Player665` join, chat,
  and disconnect only. Robin did not join or confirm in Minecraft chat, and no
  `final-gameplay-confirmation.json` was written. The latest 2026-06-27 held
  run reached `publicAgentState=RAN` again on `127.0.0.1:62913`: Craftless'
  `Player972` joined, fetched generated OpenAPI/action/event projections,
  collected logs, crafted and equipped a `Wooden Sword`, navigated to a Sheep
  through generated `entity.query`/`navigation.plan`/`navigation.follow`,
  attacked through generated `entity.attack`, observed `White Wool` and
  `Raw Mutton` drops through public entity state, moved to the drops, and
  proved pickup through final `inventory.query`. The hold ended cleanly;
  `server-evidence.jsonl` contains Craftless' `Player972` join, chat, and
  disconnect only. Robin did not join or confirm in Minecraft chat, and no
  `final-gameplay-confirmation.json` was written.
- [x] Latest 2026-06-27 final gameplay rerun after Phase 39/40 compatibility
  work reached `publicAgentState=RAN` on `127.0.0.1:57818`: Craftless'
  `Player192` joined, wrote the final gameplay chat, discovered live generated
  actions, collected materials through public `world.block.query`,
  `navigation.plan`, `navigation.follow`, `world.block.break`,
  `inventory.query`, `recipe.query`, and `recipe.craft`, equipped a
  `Wooden Sword`, attacked a Pig through generated `entity.attack`, and wrote
  `final-gameplay-ready.json`. The hold ended cleanly with
  `final-gameplay-confirmation-timeout.json`; `server-evidence.jsonl` contains
  only Craftless' `Player192` join, chat, and disconnect. Under Phase 65 this
  timeout artifact is diagnostic only; final completion depends on public
  API/CLI gameplay evidence plus CI, distribution, and compatibility probes.
- [x] Current final gameplay evidence has been rerun under the Phase 65 gate
  and accepted without requiring `final-gameplay-confirmation.json`.
- [x] Issues found during final gameplay have current Codex evidence after the
  latest changes. Phase 75 refreshed the full public gameplay gate. The
  remaining project-completion blocker is the generated-discovery/binding exit
  tracked in Phase 76, not a Robin chat confirmation or an unresolved gameplay
  run failure.
- [x] Latest and representative older-version compatibility probes have current
  passing or explicitly unsupported evidence.

Verification:

- `CRAFTLESS_FINAL_GAMEPLAY=1 mise exec -- gradle :driver-fabric:fabricFinalGameplay`
- `mise run ci`
- Evidence directory: `driver-fabric/build/craftless-final-gameplay/artifacts/`

## Phase 8: Honest Survival Navigation Correction

- [x] Baritone and SwarmBot prior art were cloned under
  `/tmp/craftless-pathfinder-research` and reviewed for navigation, movement,
  pathing, and task-composition design input.
- [x] Navigation and task protocol models exist with Craftless-owned public
  names and no Baritone/SwarmBot/raw Minecraft public leakage.
- [x] Runtime graph projection can expose navigation/task affordances from a
  `RuntimeCapabilityGraph` without static route catalog entries or public
  Baritone/SwarmBot leakage.
- [x] Runtime graph discovery can expose navigation/task affordances from the
  running client without static gameplay shortcut descriptors.
- [x] Generic navigation/task graph adapter keys are wired through
  `DriverOperationAdapters` without adding static driver gameplay methods.
- [x] Optional Baritone integration is internal backend evidence only; public
  OpenAPI remains Craftless-owned.
- [x] A no-cheat final gameplay harness rejects server-side item provisioning
  as completion evidence.
- [x] The old internal honest survival task graph was diagnostic only and has
  been removed from active product behavior by Phase 29.
- [x] Craftless obtains weapon materials through ordinary survival gameplay,
  crafts or obtains a weapon legitimately, finds a cow, navigates to it without
  manual movement, kills it, writes chat, and records SSE/artifact evidence.
  Current evidence rejects the earlier false success and server-provisioned
  item path. The external public-agent no-hold smoke now proves ordinary
  material collection, placement, chat evidence, generated navigation, placed
  station verification/opening, and generic combat outcome evidence without
  static scenario APIs, including the exact cow/beef/leather acceptance
  variant. The latest held evidence also proves legitimate weapon
  composition through generated recipe actions: logs were collected, a crafting
  table was crafted/opened, a `Wooden Sword` was crafted, public navigation
  found a Cow, generated `entity.attack` killed it, and final inventory
  contained `Raw Beef` and `Leather`. Current no-confirmation completion
  evidence is tracked by Phase 75; human co-play remains optional diagnostic
  evidence only.
- [x] The final survival proof is reproduced by an external public-agent runner
  over generated OpenAPI/SSE/CLI/skills, not by hard-coding the scenario as a
  durable public `task.survival.*` API. The previous
  `missing-generic-primitive:world.block.query` blocker is resolved by Phase
  12; the latest public-agent evidence composes navigation, mining,
  inventory/crafting, combat, chat, and state/event verification through the
  public API. Current no-confirmation completion evidence is tracked by Phase
  75; the remaining project-completion blocker is the generated-discovery and
  transitional-binding exit tracked by Phase 76.

## Phase 9: Pathfinder-Backed Execution

- [x] Internal pathfinder backend contract and task progress registry exist.
- [x] Generic navigation operation adapters invoke the pathfinder backend
  instead of placeholder unsupported adapters when execution is available.
- [x] Reflection backend probes optional pathfinder runtime classes privately
  and never leaks backend names into OpenAPI or SSE payloads.
- [x] Final gameplay can opt into a pinned pathfinder runtime mod without
  server-side item provisioning.
- [x] Opt-in pathfinder runtime uses the remapped Loom runtime, loads nested
  optional mod dependencies, and exposes `navigation.plan`,
  `navigation.follow`, and `navigation.stop` as Craftless-owned graph actions
  when the runtime probe succeeds.

Verification:

- `mise exec -- gradle :driver-fabric:test`
- `mise run lint`
- `mise run architecture-check`

## Phase 12: Runtime Block Resource Query

- [x] Spec and plan exist for generic runtime block perception without adding
  `find.tree`, `kill.cow`, `craft.sword`, or another static survival shortcut.
- [x] `world.block.query` is projected from the Fabric runtime capability graph
  with bounded `radius`, `limit`, and optional Craftless-owned `category`
  arguments.
- [x] `world.block.handle` is projected as a runtime handle for generated
  OpenAPI/resource consumers.
- [x] `fabric.world-block-query` executes through `DriverOperationAdapters`
  and `POST /clients/{id}:run`, not through a new transitional
  `FabricActionBindings.kt` descriptor. Invalid block-query bounds now return
  machine-readable generated-action failures with `invalid-radius` or
  `invalid-limit` and empty public `blocks` evidence instead of throwing.
- [x] The public-agent no-hold gameplay run invokes `inventory.query`,
  `world.block.query`, and `entity.query` from the generated action catalog and
  records `publicAgentState=RAN` without calling `task.survival.*`.
- [x] A higher-level public agent policy starts using these generic primitives
  to query log material sources and derive navigation goals without adding
  scenario actions.
- [x] Public-agent policy continues from navigation into mining/collection,
  inventory/equip, placement, chat, and generic combat evidence. Crafting,
  exact final survival acceptance, and combat pickup evidence are covered by
  later public-agent and final gameplay evidence. Human co-play remains
  optional diagnostic evidence, not a final completion requirement.

Verification:

- `mise exec -- gradle :driver-fabric:test`
- `mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest*'`
- `CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 mise exec -- gradle :driver-fabric:fabricFinalGameplay`
- Evidence: `driver-fabric/build/craftless-final-gameplay/artifacts/public-agent-gameplay-results.jsonl`

## Phase 13: Public-Agent Material Navigation

- [x] Spec and plan exist for public-agent material navigation without adding
  `find.tree`, `mine.log`, `craft.sword`, `kill.cow`, or `task.survival.*`.
- [x] Public-agent runner queries `world.block.query` with `category = "log"`
  and records insufficient evidence if no log block position is projected.
- [x] Public-agent runner derives a generic `navigation.plan` block goal from
  a returned block position and invokes `navigation.follow` with the returned
  plan id.
- [x] Live no-hold evidence shows either navigation plan/follow succeeded from
  public material discovery or an explicit public-evidence blocker that guides
  the next generic primitive. Current evidence shows `publicAgentState=RAN`,
  `world.block.query` with log results, `navigation.plan`, `navigation.follow`,
  and post-navigation `entity.query`.
- [x] Public-agent runner now continues into target-facing, raycast,
  `world.block.break`, and final inventory verification without using
  `task.survival.*`.
- [x] Invalid generated `player.move` tick budgets now return
  machine-readable `invalid-ticks` failures before scheduling client work.
  Current evidence covers the Fabric backend and the reusable fake driver
  session used by daemon/CLI/testkit consumers. Phase 81 removed the temporary
  HMC bridge gameplay adapter, so HMC no longer participates in movement
  validation evidence.

Verification:

- `mise exec -- gradle :driver-fabric:test --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricDriverModuleTest.fabric backend returns machine readable movement failure before scheduling gateway'`
- `mise exec -- gradle :testkit:test --tests 'com.minekube.craftless.testkit.FakeDriverSessionTest'`
- `mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest*'`
- `CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 mise exec -- gradle :driver-fabric:fabricFinalGameplay`

## Phase 14: Public-Agent Material Collection

- [x] Spec and plan exist for public-agent material collection without adding
  `mine.log`, `collect.wood`, `find.tree`, or any scenario-specific action.
- [x] Public-agent runner invokes `player.query`, derives `player.look` from
  public positions, invokes `player.raycast`, invokes `world.block.break`, and
  re-queries `inventory.query`.
- [x] Invalid generated `player.look` arguments now return machine-readable
  failures such as `missing-yaw` or `invalid-pitch` before scheduling client
  work.
- [x] Invalid generated `player.raycast` scalar arguments now return
  machine-readable failures such as `invalid-max-distance` before scheduling
  client work.
- [x] Public-agent runner blocks with
  `insufficient-public-evidence:inventory.query.log` when an accepted break
  does not produce public inventory evidence.
- [x] Live no-hold evidence proves either collected-log inventory state or an
  explicit blocker that identifies the next generic block-breaking/pickup
  primitive needed. Current evidence found log blocks through
  `world.block.query`, planned/followed navigation, invoked `player.look`,
  `player.raycast`, and targeted `world.block.break`, then proved collection
  with final `inventory.query` showing `Oak Log`.

Verification:

- `mise exec -- gradle :driver-fabric:test --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricDriverModuleTest.fabric player look returns machine readable failures before scheduling gateway'`
- `mise exec -- gradle :driver-fabric:test --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricDriverModuleTest.fabric backend reports invalid raycast max distance as action failure'`
- `mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest*'`
- `CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 mise exec -- gradle :driver-fabric:fabricFinalGameplay`

## Phase 15: Public-Agent Material Exploration

- [x] Spec and plan exist for bounded public-agent material exploration without
  adding `find.tree`, `mine.log`, `collect.wood`, `craft.sword`, `kill.cow`, or
  `task.survival.*`.
- [x] Public-agent runner retries material search by composing generated
  `player.query`, `navigation.plan`, `navigation.follow`, and
  `world.block.query` actions when the first local material query is empty.
- [x] Focused test evidence covers the empty-local-query exploration path
  through generated `player.query`, `navigation.plan`, `navigation.follow`,
  and repeated `world.block.query` calls without scenario shortcuts. Evidence:
  `mise exec -- gradle :testkit:test --tests 'com.minekube.craftless.testkit.PublicAgentGameplayRunnerTest.runner explores with generic navigation when local material query is empty'`.
  The latest live no-hold run did not need exploration because local
  `world.block.query` returned log targets; it continued through targeted
  collection and final inventory proof.

Verification:

- `mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest*'`
- `CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 mise exec -- gradle :driver-fabric:fabricFinalGameplay`

## Phase 16: Targetable Generic Block Break

- [x] Spec and plan exist for targeting discovered block handles/positions
  through generic `world.block.break` without adding `mine.log`, `collect.wood`,
  `find.tree`, `craft.sword`, `kill.cow`, or `task.survival.*`.
- [x] `world.block.break` accepts public target evidence from
  `world.block.query` so public agents can break the block they discovered
  instead of relying only on current camera raycast.
- [x] Malformed `world.block.break` target handles or positions now return
  machine-readable generated-action failures such as
  `invalid-target-handle` or `invalid-target-position` instead of throwing
  before public result data can be returned.
- [x] Invalid `world.block.break` scalar arguments now return
  machine-readable generated-action failures such as
  `invalid-max-distance` or `invalid-ticks` before scheduling client work.
- [x] Live no-hold evidence shows targeted break data and collected inventory
  state. Current evidence: `world.block.query` selected
  `world.block:57:77:-292`; `world.block.break` returned the same handle and
  `inventory.query` showed `Oak Log`.

Verification:

- `mise exec -- gradle :driver-fabric:test --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricDriverModuleTest.fabric block break rejects malformed target handle' --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricDriverModuleTest.fabric block break rejects incomplete target position'`
- `mise exec -- gradle :driver-fabric:test --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricDriverModuleTest.fabric block break rejects invalid scalar arguments with machine readable failures'`
- `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest*'`
- `mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest*'`
- `CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 mise exec -- gradle :driver-fabric:fabricFinalGameplay`

## Phase 17: Public-Agent Action Timeout Blockers

- [x] Spec and plan exist for controlled public-agent generated-action request
  failures without retrying non-idempotent action calls by default.
- [x] Public-agent runner records `action-request-failed:<action-id>` blockers
  with action artifacts when a generated `POST /clients/{id}:run` call fails or
  times out.
- [x] Live no-hold evidence shows targeted block-break progress and the
  external public-agent command exits normally. Focused test evidence covers
  the controlled `action-request-failed:<action-id>` blocker path.

Verification:

- `mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest*'`
- `CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS=90000 CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS=120000 mise exec -- gradle :driver-fabric:fabricFinalGameplay`

## Phase 18: Public-Agent Material Equip

- [x] Spec and plan exist for selecting collected material through generic
  public inventory actions without adding `equip.log`, `craft.sword`,
  `kill.cow`, `task.survival.*`, or other scenario shortcuts.
- [x] Public-agent runner requires discovered `inventory.equip`, chooses a
  hotbar slot from public `inventory.query` state, invokes `inventory.equip`,
  and verifies `selected-slot` with a follow-up `inventory.query`. Focused
  public-agent tests cover success and selected-slot failure paths.
- [x] Invalid generated `inventory.equip` slot arguments now return
  machine-readable failures such as `missing-slot` or `invalid-slot` before
  scheduling client work.
- [x] Live no-hold evidence reaches `inventory.equip` for collected material
  and verifies follow-up `inventory.query` selected-slot state before
  placement. The final project still remains open on generated-discovery,
  multi-version runtime, transport, distribution, and final public API/CLI
  verification gates, not on material equip.

Verification:

- `mise exec -- gradle :driver-fabric:test --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricDriverModuleTest.fabric inventory equip returns machine readable failures before scheduling gateway'`
- `mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest*'`
- `CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS=90000 CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS=120000 mise exec -- gradle :driver-fabric:fabricFinalGameplay`

## Phase 19: Sustained Generic Block Break

- [x] Spec and plan exist for bounded generic block-break progress without
  adding `mine.log`, `collect.wood`, `find.tree`, or survival-specific product
  actions.
- [x] `world.block.break` exposes a generic `ticks` budget and returns
  `changed` evidence from observed client block state.
- [x] Public-agent runner sends bounded break ticks and blocks when
  `world.block.break` reports `changed = false`.
- [x] Live no-hold evidence shows sustained block break can change an ordinary
  survival block. Current evidence: `world.block.break` returned
  `changed = true` with `ticks = 61` for `world.block:64:81:-287`.
- [x] Current live no-hold evidence shows material proof and equip after a
  changed block path. The generic block break primitive is no longer the
  current blocker.

Verification:

- `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric runtime discovery exposes block break only from client state'`
- `mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest*'`
- `CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS=90000 CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS=120000 mise exec -- gradle :driver-fabric:fabricFinalGameplay`

## Phase 20: Public-Agent Material Pickup

- [x] Spec and plan exist for generic pickup composition without adding
  `pickup.log`, `collect.wood`, `mine.log`, or survival-specific product
  actions.
- [x] Public-agent runner prefers lower reachable material targets from public
  `world.block.query` data. Focused tests cover lower-target selection.
- [x] Public-agent runner composes pickup movement with `navigation.plan` and
  `navigation.follow` after `world.block.break` reports `changed = true`.
- [x] Live no-hold evidence reaches inventory material proof after public
  pickup movement/drop perception paths without adding a pickup shortcut. The
  latest no-hold run collected multiple ordinary materials through generated
  `world.block.query`, `navigation.plan`, `navigation.follow`,
  `world.block.break`, `entity.query`, and `inventory.query` before recipe
  composition.

Verification:

- `mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest*'`
- `CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS=90000 CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS=120000 mise exec -- gradle :driver-fabric:fabricFinalGameplay`

## Phase 21: Public-Agent Drop Perception

- [x] Spec and plan exist for using public `entity.query` drop perception
  without adding `pickup.log`, `collect.wood`, `mine.log`, or survival-specific
  product actions.
- [x] Public-agent runner queries entities after block break and pickup
  movement, optionally navigates to observed material drop positions, and still
  verifies pickup through `inventory.query`. Focused tests cover public material
  drop navigation.
- [x] Live no-hold evidence runs `entity.query` after material break and uses
  public drop perception when block-target pickup navigation is insufficient.
  Latest and prior runs reached inventory material proof without a pickup
  shortcut.

Verification:

- `mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest*'`
- `CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS=90000 CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS=120000 mise exec -- gradle :driver-fabric:fabricFinalGameplay`

## Phase 22: Bounded Material Exploration

- [x] Spec and plan exist for shorter overlapping public-agent material
  exploration without adding `find.tree`, `collect.wood`, `mine.log`, or
  product API shortcuts.
- [x] Public-agent runner uses shorter overlapping generated-navigation
  waypoints after an empty local `world.block.query`. Focused tests verify the
  first exploration waypoint is 24 blocks rather than the previous 48.
- [x] Live no-hold evidence progressed through bounded material exploration in
  prior runs and the latest run reached repeated material collection plus
  recipe composition. Focused regression evidence covers retrying material
  exploration when a discovered target is not navigable.

Verification:

- `mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest*'`
- `CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS=90000 CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS=120000 mise exec -- gradle :driver-fabric:fabricFinalGameplay`

## Phase 23: Runtime Entity Attack

- [x] Spec and plan exist for exposing generic entity attack through the
  runtime graph and generated public action path without adding `kill.cow`,
  combat macros, `task.survival.*`, or other scenario shortcuts.
- [x] `entity.attack` appears only when the live runtime has player, world,
  and interaction manager support, accepts Craftless-owned entity handles from
  `entity.query`, and is backed by a Fabric client-thread adapter.
- [x] Public-agent composition invokes `entity.attack` only through generated
  `POST /clients/{id}:run` dispatch after discovering both the action and a
  public entity handle. It now refuses to report success when `entity.attack`
  is advertised but no public attack target is found.
- [x] Public-agent composition verifies post-attack public outcome evidence
  from `entity.query` or `inventory.query` before treating combat as proven;
  without that evidence it blocks with
  `insufficient-public-evidence:entity.attack.outcome`.
- [x] `entity.query` invalid bounds now return machine-readable
  generated-action failures with `invalid-radius` or `invalid-limit` and empty
  public `entities` evidence instead of throwing.
- [x] `entity.attack` invalid public arguments now return machine-readable
  generated-action failures with `hit=false` and `reason` values such as
  `invalid-max-distance`, `missing-target`, or `invalid-entity-handle`;
  the runtime graph advertises those result fields for generated clients.
- [x] Focused driver and public-agent tests pass.
- [x] Recent live no-hold evidence reaches generic attack invocation through
  generated `entity.attack` after material pickup and placement. Focused
  public-agent tests require post-attack entity or inventory proof, bounded
  generated-navigation exploration when no target is visible, vertically
  offset target navigation, fresh close-range target revalidation before
  attack, target repositioning when a refreshed target moves, a bounded pause
  between unproven attacks, target refresh between unproven attacks, filtering
  unsuitable aquatic/living targets, generated `player.move` fallback when
  combat navigation cannot close a reach gap, and pickup of visible public
  combat loot drops before treating loot-visible combat as complete.
  Latest held live evidence records generated combat outcome proof with a
  legitimate weapon: the runner crafted a `Wooden Sword`, re-equipped its
  generated inventory slot after navigation drift, `entity.attack` hit a Cow,
  follow-up `entity.query` reported `Raw Beef` and `Leather` drops and the passive
  entity no longer alive, generated navigation moved to the drop, and final
  `inventory.query` showed `Raw Beef` and `Leather`. The final project remains
  open on the broader generated-discovery and multi-version completion gates.

Verification:

- `mise exec -- gradle :driver-fabric:test --tests '*FabricCapabilityProbeTest*' --tests '*FabricDriverModuleTest.fabric backend invokes entity attack through runtime graph adapter'`
- `mise exec -- gradle :driver-fabric:test --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricDriverModuleTest.fabric backend returns machine readable entity attack failures for invalid arguments'`
- `mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest*'`

## Phase 24: Targetable Block Interact

- [x] Spec and plan exist for targetable generic block interaction without
  adding `build.house`, `place.log`, structure macros, or other scenario
  shortcuts.
- [x] `world.block.interact` accepts public block handles or positions plus a
  side, invokes the Fabric client-thread interaction manager, and returns
  `accepted` plus state-change evidence.
- [x] Malformed `world.block.interact` target handles now return
  machine-readable generated-action failures such as
  `invalid-target-handle` with `accepted=false` instead of throwing before
  public result data can be returned.
- [x] Invalid `world.block.interact` scalar arguments now return
  machine-readable generated-action failures such as
  `invalid-max-distance` or `invalid-side` before scheduling client work.
- [x] Public-agent composition invokes targetable `world.block.interact` only
  when the generated action descriptor advertises `target`, refreshes public
  support block evidence after navigation, requires an unoccupied replaceable
  placement face, faces the public support target with
  `player.query`/`player.look`, retries bounded alternate support targets, and
  verifies `changed` before treating placement as proven.
- [x] Focused driver and public-agent tests pass.
- [x] Live no-hold gameplay evidence shows the generated
  `world.block.interact` path accepts a public block handle plus side and
  reports `accepted = true` and `changed = true`. This clears the previous
  `insufficient-public-evidence:world.block.interact.changed` blocker, but it
  is not final project completion because the broader generated-discovery,
  multi-version runtime, transport, distribution, and final public API/CLI
  gates are still outstanding.

Verification:

- `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric runtime discovery exposes block interact only from client state'`
- `mise exec -- gradle :driver-fabric:test --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricDriverModuleTest.fabric block interact rejects malformed target handle with machine readable failure'`
- `mise exec -- gradle :driver-fabric:test --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricDriverModuleTest.fabric block interact rejects invalid scalar arguments with machine readable failures'`
- `mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest*'`
- `CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS=90000 CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS=120000 mise exec -- gradle :driver-fabric:fabricFinalGameplay`

## Phase 25: Distribution Usability

- [x] Spec and plan exist for release, install, Docker, GitHub Action, and
  README quickstart surfaces.
- [x] Release workflow builds the CLI distribution with mise/Gradle, uploads
  GitHub Release artifacts and checksums, and pushes a GHCR runtime image.
  Current published release evidence: `v0.1.1` is the latest GitHub Release,
  workflow run `28316490956` completed successfully for
  `bc9e630c1c4d250584b1b5999d717b3dd17d25d3`, and release assets include
  `craftless-0.1.1.tar`, `craftless-0.1.1.zip`, and `SHA256SUMS`.
- [x] Docker image copies an already-built Craftless CLI distribution and does
  not build the project inside Docker.
  Refreshed 2026-06-28 evidence: `mise run package-cli` built
  `craftless-0.1.0-SNAPSHOT.tar` and `craftless-0.1.0-SNAPSHOT.zip`;
  `docker build -t craftless:local .` copied `build/docker/craftless/` into
  `/opt/craftless/`; and
  `docker run --rm craftless:local /opt/craftless/bin/craftless server start --once --port 0 --workspace /tmp/craftless`
  returned an `ok=true` supervisor URL.
- [x] Install script installs `craftless` from GitHub Releases without
  requiring users to clone this repository.
  Refreshed 2026-06-28 evidence: `CRAFTLESS_VERSION=v0.1.1
  CRAFTLESS_INSTALL_DIR=... CRAFTLESS_HOME=... ./install.sh` installed
  `craftless 0.1.1`, the installed CLI returned `ok=true` from
  `server start --once --port 0`, and the published tar contains
  `craftless-0.1.1/mods/craftless-driver-fabric.jar`.
- [x] Reusable GitHub Action installs Craftless and can optionally start the
  local daemon for downstream workflows.
- [x] README documents install script, Docker, and GitHub Actions usage with no
  Homebrew requirement and no legacy SDK references.

Verification:

- `mise exec -- bun test playwright`
- `mise run package-cli`
- `docker build -t craftless:local .`
- `docker run --rm craftless:local /opt/craftless/bin/craftless server start --once --port 0 --workspace /tmp/craftless`
- `mise run ci`

## Phase 26: Version-Agnostic Driver Architecture

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-26-26-version-agnostic-driver-architecture-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-26-26-version-agnostic-driver-architecture-plan.md`.
- [x] Compatibility probe exists:
  `docs/superpowers/evidence/2026-06-26-version-26-compatibility-probe.md`.
- [x] Current 1.21.6-specific Fabric code is audited and classified as build
  lane, internal provider, runtime evidence, fixture, or public-facing debt.
  Evidence:
  `docs/superpowers/evidence/2026-06-26-version-agnostic-driver-audit.md`.
- [x] Stable internal runtime/provider facades exist before adding more
  Minecraft version breadth.
  Evidence:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricRuntimeProviderTest*'`
  and `mise exec -- gradle :driver-fabric:detekt :driver-fabric:ktlintCheck`.
- [x] Compatibility matrix and provider-selection tests cover the current lane
  plus latest-release and representative older-release unsupported lanes.
  Evidence:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricCompatibilityMatrixTest*' --tests '*FabricCurrentLaneRuntimeProviderTest*'`
  and `mise exec -- gradle :testkit:test --tests '*LocalMinecraftServerSmokeTest*'`.
- [x] Runtime probe metadata records version/provider/status support and
  unavailable reasons without leaking Fabric/Yarn/Minecraft names into public
  API.
  Evidence:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricCapabilityProbeTest.runtime metadata probe emits sanitized compatibility lane evidence*' --tests '*FabricDriverModuleTest.fabric backend runtime graph includes sanitized compatibility lane evidence*'`.
- [x] Java runtime selection is version-aware; Minecraft `26.2` requires Java
  25 and must not be launched through the repository's Java 21 default.
  Evidence:
  `mise exec -- gradle :testkit:test --tests '*LocalMinecraftServerSmokeTest.local server smoke action command receives resolved Java runtime executable*' :driver-fabric:test --tests '*FabricDriverModuleTest.fabric run client consumes resolved smoke Java executable*'`.
  Testkit exports the selected executable from resolver output to action
  commands, and the Fabric `runClient` task consumes
  `CRAFTLESS_SMOKE_JAVA_EXECUTABLE` when present.
- [x] Fabric client launch selects a compiled/runtime-compatible lane for the
  requested Minecraft version instead of always launching the current `1.21.6`
  Fabric lane. Evidence:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric client smoke passes runtime lane evidence before launching client*'`
  and
  `CRAFTLESS_FABRIC_CLIENT_SMOKE=1 CRAFTLESS_SMOKE_MINECRAFT_VERSION=26.2 CRAFTLESS_LOCAL_SERVER_SMOKE_ROOT=/tmp/craftless-fabric-smoke-26-lane mise exec -- gradle :driver-fabric:fabricClientSmoke`;
  the latter writes `artifacts/runtime-lane.json` with status `UNSUPPORTED`,
  Java 25, and reason `runtime-lane-missing` before launching the current
  Fabric client lane.
- [x] Connect success is backed by observed client/server join evidence, not
  only by accepting a connect request. Evidence:
  `mise exec -- gradle :driver-runtime:test --tests '*BackendDriverSessionTest.runtime driver session does not mark connect successful without observed backend evidence*' :driver-fabric:test --tests '*FabricDriverModuleTest.fabric backend reports connect as unobserved until gateway is connected*'`.
  The runtime session no longer emits `CLIENT_CONNECTED` or returns
  `CONNECTED` for an unobserved backend request, and the Fabric backend reports
  connect observation from the gateway connection state; server-side join
  remains asserted by the smoke fixture when an expected player is configured.
- [x] Supervisor API can prepare, install, and launch a real versioned client
  runtime instead of failing with an unavailable driver factory. Evidence:
  `mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.server prepares and launches workspace client runtime without injected driver factory*' --tests '*LocalSessionApiServerTest.process client runtime launcher starts prepared command*'`
  plus `mise exec -- gradle :daemon:test :daemon:detekt :daemon:ktlintCheck`.
- [x] Cache preparation for large Minecraft/mod artifact sets is resumable and
  idempotent with retryable per-file failures and progress evidence. Evidence:
  `mise exec -- gradle :daemon:test --tests '*CachePreparationServiceTest.cache preparation reuses existing binary artifacts and fetches missing files*' --tests '*CachePreparationServiceTest.cache preparation resumes after per-file artifact fetch failure*'`
  plus `mise exec -- gradle :daemon:test :daemon:detekt :daemon:ktlintCheck`.

Verification:

- `git diff --check`
- focused tests from the Phase 26 implementation plan.
- `mise run ci` before marking implementation complete.

Verification:

- `mise exec -- gradle :protocol:test :driver-api:test :driver-fabric:test`
- `mise run architecture-check`
- No-cheat live gameplay run with empty or ordinary survival inventory.

## Phase 27: Java Runtime Resolution

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-26-27-java-runtime-resolution-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-26-27-java-runtime-resolution-plan.md`.
- [x] Java runtime requirements are derived from Minecraft version metadata,
  including Java 25 for Minecraft `26.2`. Evidence:
  `mise exec -- gradle :daemon:test --tests '*JavaRuntimeRequirementResolverTest*'`.
- [x] A Craftless-owned resolver validates and selects explicit, managed,
  mise-discovered, and system Java candidates through one internal interface.
  Evidence: `mise exec -- gradle :daemon:test --tests '*JavaRuntimeResolverTest*'`.
- [x] `mise` is an optional Java provider for product runtime selection, not
  the only way users can run compatible Minecraft clients. Evidence:
  `JavaRuntimeResolverTest` covers configured, managed-cache, mise, and system
  candidate paths without requiring `mise` on `PATH`.
- [x] Cache manifests and launch plans record selected Java runtime evidence
  and use the selected executable. Evidence:
  `mise exec -- gradle :daemon:test --tests '*CachePreparationServiceTest.cache preparation resolves and stores minecraft version metadata*'`.
- [x] Supervisor API and CLI can list or resolve Java runtimes without adding
  gameplay action catalogs. Evidence:
  `mise exec -- gradle :protocol:test :daemon:test :cli:test`.
- [x] Testkit/server smoke consumes resolver output and records Java selection
  evidence instead of depending only on `CRAFTLESS_SMOKE_JAVA_EXECUTABLE`.
  Evidence: `mise exec -- gradle :testkit:test` and
  `docs/superpowers/evidence/2026-06-26-java-runtime-resolution-smoke.md`.
- [x] Minecraft `26.2` server smoke proves Java 25 selection; Fabric client
  compatibility remains gated by Phase 26 driver-lane selection. Evidence:
  `docs/superpowers/evidence/2026-06-26-java-runtime-resolution-smoke.md`.

Verification:

- `mise exec -- gradle :protocol:test :daemon:test :cli:test :testkit:test`
- `CRAFTLESS_LOCAL_SERVER_SMOKE=1 CRAFTLESS_SMOKE_MINECRAFT_VERSION=26.2 mise exec java@temurin-25.0.3+9.0.LTS gradle@9.6.0 -- gradle :testkit:localMinecraftServerSmoke`
- `git diff --check`

## Phase 28: Generic Recipe And Crafting

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-26-28-generic-recipe-crafting-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-26-28-generic-recipe-crafting-plan.md`.
- [x] Runtime graph discovery declares the Craftless-owned `recipe` resource,
  `recipe.handle`, `recipe.query`, and `recipe.craft`. `recipe.query`,
  `recipe`, and `recipe.handle` become available only when the live client
  reports recipe-book state; otherwise they remain unavailable with
  `recipe-discovery-unavailable`. `recipe.craft` becomes available only when
  the live client also reports a craft execution context; otherwise it remains
  unavailable with `recipe-context-unavailable` or another machine-readable
  runtime reason. `recipe.query` now also projects nested result schema
  metadata for `count`, `recipes`, recipe handles, craftability,
  `requires`/`produces`, compatibility `outputs`/`ingredients`, station
  metadata, and non-craftable reasons into generated OpenAPI. `recipe.craft`
  now projects nested target/result schema metadata for recipe handles,
  accepted/changed state, requested/crafted counts, inventory fingerprints,
  phases, output slots, output/confirmation attempts, item projections, and
  machine-readable reasons.
- [x] `recipe.query` has a guarded Fabric operation adapter that projects live
  recipe-book display entries into opaque Craftless recipe handles, public
  `produces`/`requires` item labels, compatibility `outputs`/`ingredients`,
  categories, craftability, `recipe-not-craftable` reasons, and query filters.
  Invalid query bounds now return machine-readable `invalid-limit` results
  with the same generated result shape instead of throwing. Live recipe
  requirement, station, and handle evidence is sufficient for the current
  public-agent survival acceptance path; broader handler coverage remains
  future compatibility work rather than a completion blocker.
- [x] `recipe.craft` has public handle/count validation, stale-handle
  validation, live craftability checks, guarded Fabric client-thread execution
  through `clickRecipe`, before/after inventory fingerprints, and expected
  versus actual output-slot validation. Craft result evidence now reports
  `crafted-count` from the observed output slot stack count before quick-moving
  the output, then performs bounded follow-up fingerprint polling and reports
  `phase=crafting-inventory-confirmed` when public inventory evidence changes.
  Pending output evidence now also carries `requested-count` so generated
  clients can parse in-flight recipe results through the same schema as
  failure and completion results.
  Target validation, stale-handle, craftability, output, and confirmation
  failure paths now keep schema-shaped public result evidence with
  `requested-count`, `crafted-count`, `phase`, and machine-readable `reason`
  fields. Invalid count bounds now return `invalid-count` through the same
  public result contract instead of throwing. Crafting-station
  interaction/opening and live survival evidence are covered by current
  public-agent and held final gameplay artifacts; broader screen/handler
  coverage remains future compatibility work rather than a completion blocker.
- [x] Public-agent composition uses generated recipe actions when available to
  craft useful outputs, then verifies inventory state through `inventory.query`
  in focused fake-server evidence. Public-agent recipe fixtures now include
  `requested-count` in generated `recipe.craft` responses and assert the
  action log preserves it, so fake evidence matches the generated result
  schema used by live drivers.
- [x] Live no-hold final gameplay evidence shows generic recipe/crafting
  progress without `craft.sword`, `craft.planks`, `craft.table`,
  `make.weapon`, `kill.cow`, or `task.survival.*`. Evidence:
  `driver-fabric/build/craftless-final-gameplay/artifacts/public-agent-gameplay-results.jsonl`
  shows `recipe.query` returning `recipe.handle:805` for Oak Planks,
  `recipe.craft` with `changed=true`, `phase=crafting-output-taken`, and
  follow-up `inventory.query` showing 4 Oak Planks.
- [x] Public-agent composition can chain generated material and combat recipes
  in focused fake-server evidence, and it requires combat-ready inventory
  proof before treating a generated weapon recipe as successful.
- [x] Public-agent composition now has focused and live evidence for station-backed
  recipe ordering and opening: when a mixed generated `recipe.query` contains a
  station-backed weapon and a recipe that produces the missing station, the
  agent crafts the station first, verifies it through public inventory,
  re-equips it before generated `world.block.interact`, verifies `screen.query`
  opened the station, then continues through stick and weapon recipes. Current
  held live evidence reached `publicAgentState=RAN`: the public agent collected
  materials, crafted/equipped a `Wooden Sword`, attacked a Chicken through
  generated `entity.attack`, observed `Raw Chicken` and `Feather` drops through
  public `entity.query`, and reached the ready window without static survival
  shortcuts.

Verification:

- `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest*'`
- `mise exec -- gradle :driver-fabric:test --tests '*recipe*'`
- `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric backend crafts a discovered recipe handle through runtime graph adapter*'`
- `mise exec -- gradle :driver-fabric:test --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricDriverModuleTest.fabric recipe craft execution takes generic crafting output after recipe fill'`
- `mise exec -- gradle :testkit:test --tests 'com.minekube.craftless.testkit.PublicAgentGameplayRunnerTest.runner treats generated material recipes as useful crafting progress'`
- `mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest*'`
- `CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS=90000 CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS=120000 mise exec -- gradle :driver-fabric:fabricFinalGameplay`
- `mise run ci`

## Phase 29: Legacy Survival Task API Removal

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-26-29-legacy-survival-task-api-removal-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-26-29-legacy-survival-task-api-removal-plan.md`.
- [x] The active Fabric driver no longer contains `SurvivalTaskGraph`,
  `FabricSurvivalTaskExecutor`, survival observation ports, or survival
  resource handles.
- [x] `task.run` and `task.status` remain generic future affordances only and
  are unavailable with `task-executor-unavailable` until a generic task graph
  executor exists.
- [x] The final gameplay smoke controller no longer invokes
  `task.survival.honest-cow-hunt` or writes `survival-task-results.jsonl`.
- [x] Final live gameplay proves the scenario through generated
  public actions, SSE events, and adaptive consumers. Focused evidence now
  covers generated station-backed recipe composition without `craft.sword` or
  station shortcuts. Current live
  no-hold evidence covers public material pickup, station placement/opening,
  generic combat, and public combat loot pickup without `task.survival.*`.
  Current held evidence covers honest weapon acquisition/composition as
  required by the survival scenario. The legacy API removal phase is complete;
  optional held multiplayer observation remains diagnostic in Phase 7 and the final
  completion gate.

Verification:

- `mise exec -- gradle :driver-fabric:test --tests '*FabricNavigationDiscoveryTest.fabric backend task adapter refuses legacy survival tasks*'`
- `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest*' --tests '*FabricNavigationDiscoveryTest*'`
- `mise run ci`

## Phase 30: Bounded Attack Exploration

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-30-bounded-attack-exploration-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-30-bounded-attack-exploration-plan.md`.
- [x] The 2026-06-27 final gameplay run reached the ready window but the
  process-external public agent blocked with
  `insufficient-public-evidence:entity.query.attack-target` after crafting a
  `Wooden Sword` and finding only dropped items plus vertically distant Cod and
  Salmon through generated `entity.query`.
- [x] The public-agent policy now performs bounded multi-ring attack
  exploration through generated `navigation.plan`, `navigation.follow`, and
  `entity.query` before reporting that attack target evidence is missing.
- [x] Focused regression evidence proves the runner keeps ignoring aquatic
  living entities as completion evidence, continues beyond the first waypoint
  ring, and attacks a later valid Cow handle without scenario shortcuts.
- [x] Final live gameplay was rerun after this correction and reached
  `publicAgentState=RAN`. Evidence:
  `driver-fabric/build/craftless-final-gameplay/artifacts/public-agent-command.log`
  reports `publicAgentState=RAN`; `final-gameplay-ready.json` was written for
  `127.0.0.1:56513`; `server-evidence.jsonl` contains only Craftless'
  `Player50` join/chat/disconnect and no Robin confirmation chat; no
  `final-gameplay-confirmation.json` was written before the hold expired.

Verification:

- `mise exec -- gradle :testkit:test --tests 'com.minekube.craftless.testkit.PublicAgentGameplayRunnerTest.runner continues bounded generated attack exploration beyond first waypoint ring'`
- `mise exec -- gradle :testkit:test`
- `mise run ci`

## Phase 31: Material Count Evidence

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-31-material-count-evidence-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-31-material-count-evidence-plan.md`.
- [x] The 2026-06-27 held final gameplay rerun reached the ready window but
  the process-external public agent blocked with
  `insufficient-public-evidence:inventory.query.recipe-material` after a
  second generated `world.block.break` because stale one-log inventory evidence
  satisfied the pickup loop.
- [x] Public-agent material collection now tracks the public log count before
  each generated break and requires later `inventory.query` evidence to exceed
  that count before treating the attempt as successful.
- [x] Focused regression evidence proves stale one-log inventory does not
  satisfy the second recipe-material collection attempt and that the runner
  keeps querying generated pickup evidence until the public count reaches two.
- [x] Final live gameplay was rerun after this correction and reached
  `publicAgentState=RAN`. Evidence:
  `driver-fabric/build/craftless-final-gameplay/artifacts/public-agent-command.log`
  reports `publicAgentState=RAN`; `final-gameplay-ready.json` was written for
  `127.0.0.1:61836`; `server-evidence.jsonl` contains Craftless' `Player546`
  join/chat/disconnect and no Robin confirmation chat; no
  `final-gameplay-confirmation.json` was written before the hold expired.

Verification:

- `mise exec -- gradle :testkit:test --tests 'com.minekube.craftless.testkit.PublicAgentGameplayRunnerTest.runner keeps collecting recipe materials until public inventory count increases'`
- `mise exec -- gradle :testkit:test --tests 'com.minekube.craftless.testkit.PublicAgentGameplayRunnerTest'`
- `mise run ci`

## Phase 32: Material Reach Evidence

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-32-material-reach-evidence-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-32-material-reach-evidence-plan.md`.
- [x] The 2026-06-27 held final gameplay rerun blocked because generated
  `navigation.follow` reported success for a material target while public
  `player.query` still placed the client outside generated block-break reach.
  The following generated `world.block.break` failed with
  `INVALID_ACTION_INPUT` because the target exceeded max distance.
- [x] Public-agent material collection now verifies public player position
  against the break reach budget after generated navigation and before sending
  `world.block.break`.
- [x] Focused regression evidence proves the runner blocks with
  `insufficient-public-evidence:navigation.follow.succeeded`, records
  `player.query`, and does not call `world.block.break` when public state
  proves the target is still out of reach.
- [x] Final live gameplay was rerun after this correction and reached
  `publicAgentState=RAN`. Evidence:
  `driver-fabric/build/craftless-final-gameplay/artifacts/public-agent-command.log`
  reports `publicAgentState=RAN`; the public agent collected material,
  crafted/equipped a `Wooden Sword`, attacked a generated public Chicken
  target, picked up `Feather` and `Raw Chicken`, and wrote
  `final-gameplay-ready.json` for `127.0.0.1:56270`.
  `server-evidence.jsonl` contains Craftless' `Player161`
  join/chat/disconnect and no Robin confirmation chat; no
  `final-gameplay-confirmation.json` was written before the hold expired.

Verification:

- `mise exec -- gradle :testkit:test --tests 'com.minekube.craftless.testkit.PublicAgentGameplayRunnerTest.runner verifies public position after generated navigation reports success'`
- `mise exec -- gradle :testkit:test --tests 'com.minekube.craftless.testkit.PublicAgentGameplayRunnerTest'`
- `mise exec -- gradle :testkit:test`
- `mise run lint`
- `mise run jvm-test`

## Phase 33: Combat Reach Fallback

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-33-combat-reach-fallback-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-33-combat-reach-fallback-plan.md`.
- [x] The 2026-06-27 held final gameplay rerun blocked because generated
  `navigation.follow` reported success for a public Pig target while follow-up
  `player.query` and `entity.query` still placed the target outside generated
  attack reach. The runner correctly refused `entity.attack`, but did not use
  the discovered generic `player.move` fallback because navigation had not
  reported failure.
- [x] Public-agent combat focus now re-checks public reach evidence after
  generated navigation and uses one bounded generic `player.move` nudge when
  the target remains outside attack reach and `player.move` is discovered.
- [x] Focused regression evidence proves the runner invokes generated
  `player.move` before `entity.attack` when combat navigation reports success
  but public state still contradicts reachability.
- [x] Existing guardrail evidence still proves the runner does not invoke
  `entity.attack` when the target remains outside generated attack reach and
  `player.move` is not discovered.
- [x] Broader local gates pass after this correction.
- [x] Final live gameplay was rerun after this correction and exposed the next
  generic evidence issue: the process-external public agent entered a long
  generated `navigation.follow` request. Thread dumps showed the request inside
  `ReflectiveFabricPathfinderBackend.waitForPathCompletion`, while manual
  generated `player.query`, `inventory.query`, and `world.block.query` calls
  still responded. The run was stopped before completion because the public
  agent had not written incremental action artifacts for the in-flight
  generated request.

Verification:

- `mise exec -- gradle :testkit:test --tests 'com.minekube.craftless.testkit.PublicAgentGameplayRunnerTest.runner uses generated player move when combat navigation succeeds but target remains out of reach'`
- `mise exec -- gradle :testkit:test`
- `mise run lint`
- `mise run jvm-test`

## Phase 34: Incremental Public-Agent Artifacts

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-34-incremental-public-agent-artifacts-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-34-incremental-public-agent-artifacts-plan.md`.
- [x] The 2026-06-27 final gameplay rerun after Phase 33 showed that
  long-running generated actions need in-progress artifacts. The public agent
  had fetched generated discovery and had a live connection to
  `POST /clients/{id}:run`, but `public-agent-gameplay-results.jsonl` still
  contained stale earlier smoke entries because action artifacts were written
  only after runner completion.
- [x] Public-agent artifacts are now initialized immediately after public
  discovery. Gameplay artifacts are truncated at run start, action-started
  events are appended before each generated POST, action responses are appended
  as they arrive, and blockers are appended without rewriting prior action
  evidence.
- [x] Focused regression evidence proves generated action request failures
  write `public-agent-action-started`, the failed generated action response,
  and the public-agent blocker.
- [x] Broader local gates pass after this correction.
- [x] Final live gameplay has been rerun after this correction and either
  reached `publicAgentState=RAN` or recorded the next precise generic public
  evidence/action blocker with incremental artifacts.

Verification:

- `mise exec -- gradle :testkit:test --tests 'com.minekube.craftless.testkit.PublicAgentGameplayRunnerTest.runner records blocked artifacts when generated action request fails'`
- `mise exec -- gradle :testkit:test`
- `mise run lint`
- `mise run jvm-test`

## Phase 35: Final Confirmation Timeout Artifact

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-35-final-confirmation-timeout-artifact-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-35-final-confirmation-timeout-artifact-plan.md`.
- [x] The latest held final gameplay rerun reached `publicAgentState=RAN`, but
  no Robin join or chat confirmation was observed before the hold expired.
  Absence of `final-gameplay-confirmation.json` was the only machine-readable
  completion-gate outcome.
- [x] The final harness now writes
  `final-gameplay-confirmation-timeout.json` when the configured confirmation
  phrase is not observed before the hold deadline.
- [x] Focused regression evidence proves timeout artifact writing, and the
  existing confirmation test still proves matching chat writes
  `final-gameplay-confirmation.json`.

Verification:

- `mise exec -- gradle :driver-fabric:test --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricDriverModuleTest.fabric smoke controller writes confirmation timeout artifact when Robin chat is not observed'`
- `mise exec -- gradle :driver-fabric:test --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricDriverModuleTest.fabric smoke controller stops final session after configured chat confirmation evidence' --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricDriverModuleTest.fabric smoke controller writes confirmation timeout artifact when Robin chat is not observed'`

## Phase 36: Legacy Survival Task Namespace Guard

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-36-legacy-survival-task-namespace-guard-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-36-legacy-survival-task-namespace-guard-plan.md`.
- [x] Protocol navigation tests no longer use `task.survival.*` as a positive
  serialization example.
- [x] `NavigationTaskRequest` rejects `task.survival.*` at the protocol
  boundary.
- [x] `NavigationProgressEvent.type` rejects `task.survival.*` so legacy
  scenario namespaces cannot leak back through server-emitted progress
  metadata.
- [x] Fabric task-adapter evidence now uses a neutral generic task id and still
  proves task execution remains unavailable without a generic executor.

Verification:

- `mise exec -- gradle :protocol:test --tests 'com.minekube.craftless.protocol.NavigationModelsTest'`
- `mise exec -- gradle :driver-fabric:test --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricNavigationDiscoveryTest.fabric backend task adapter keeps generic tasks unavailable without executor'`

## Phase 37: Scenario Shortcut Action Guard

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-37-scenario-shortcut-action-guard-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-37-scenario-shortcut-action-guard-plan.md`.
- [x] Public OpenAPI action descriptors reject known scenario shortcut ids such
  as `find.tree`, `mine.log`, `craft.sword`, and `kill.cow`.
- [x] Runtime graph operation nodes reject the same shortcut ids through the
  shared action-id validator.
- [x] Existing generic action ids remain valid; this guard does not remove
  generic primitives such as `recipe.craft`, `inventory.equip`,
  `world.block.break`, or `entity.attack`.

Verification:

- `mise exec -- gradle :protocol:test --tests 'com.minekube.craftless.protocol.NamespacePolicyTest.public action descriptors reject scenario shortcut action ids'`
- `mise exec -- gradle :protocol:test`

## Phase 38: Combat Miss Retry

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-38-combat-miss-retry-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-38-combat-miss-retry-plan.md`.
- [x] The 2026-06-27 final gameplay rerun reached the human-ready window but
  the process-external public agent blocked with
  `insufficient-public-evidence:entity.attack.hit` after a generated
  `entity.attack` miss. Evidence:
  `driver-fabric/build/craftless-final-gameplay/artifacts/public-agent-gameplay-results.jsonl`
  shows a Cow target discovered through generated `entity.query`, one accepted
  generated `entity.attack` with `hit=true`, a later refreshed Cow position
  outside the generated attack reach, and a second generated `entity.attack`
  returning `hit=false` with `entity-target-out-of-range`.
- [x] Public-agent combat now treats a generated attack miss as recoverable
  while bounded combat attempts remain: it refreshes public `entity.query`
  evidence, re-focuses through the existing generated navigation and optional
  `player.move` path, then retries generated `entity.attack`.
- [x] Focused regression evidence proves the runner records additional public
  `entity.query` evidence and retries generated `entity.attack` instead of
  immediately blocking on the first generated attack miss.

Verification:

- `mise exec -- gradle :testkit:test --rerun-tasks --tests 'com.minekube.craftless.testkit.PublicAgentGameplayRunnerTest.runner revalidates public attack target after generated attack misses'`

## Phase 39: Fabric Library Replacement

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-39-fabric-library-replacement-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-39-fabric-library-replacement-plan.md`.
- [x] Fabric cache preparation now derives Maven module keys from fetched
  Minecraft version manifests and Fabric loader profiles.
- [x] Fabric loader-profile libraries replace duplicate Minecraft libraries
  with the same Maven group and artifact before prepared artifacts and launch
  classpath are built.
- [x] Non-duplicate Minecraft libraries remain cached and present on the launch
  classpath.
- [x] This phase changes daemon launch/cache preparation only and adds no
  public gameplay action, route, CLI gameplay command, or version-specific
  hard-coded library list.

Verification:

- `mise exec -- gradle :daemon:test --tests 'com.minekube.craftless.daemon.CachePreparationServiceTest.fabric cache preparation lets fabric libraries replace duplicate minecraft libraries'`
- `mise exec -- gradle :daemon:test`
- `mise run lint`
- `mise run architecture-check`
- `mise run ci`

## Phase 40: Rule-Selected Native Libraries

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-40-rule-selected-native-libraries-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-40-rule-selected-native-libraries-plan.md`.
- [x] Cache preparation applies Mojang library rules before classifying
  Minecraft libraries for prepared artifacts and launch metadata.
- [x] Rule-selected `natives-*` artifact libraries are extracted as native
  libraries and kept out of the Java classpath.
- [x] Legacy `downloads.classifiers` native entries remain supported.
- [x] The focused regression fixture is platform-aware so the behavior is
  checked on the current CI host instead of only on macOS ARM64.

Verification:

- `mise exec -- gradle :daemon:test --tests 'com.minekube.craftless.daemon.CachePreparationServiceTest.cache preparation extracts rule selected native artifact libraries outside classpath'`
- `mise exec -- gradle :daemon:test`
- `mise run lint`
- `mise run architecture-check`
- `mise run ci`

## Phase 41: Launch Argument Placeholders

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-41-launch-argument-placeholders-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-41-launch-argument-placeholders-plan.md`.
- [x] Process client runtime launch now resolves profile and instance
  placeholders from `CreateClientRequest` and `InstanceFiles` before invoking
  `ProcessBuilder`.
- [x] Offline profile launches now provide `auth_player_name`, standard
  Minecraft offline `auth_uuid`, offline `auth_access_token`, `user_type`,
  `gameRoot`, `assets_index_name`, `version_type`, launcher metadata, and
  `quickPlayPath`.
- [x] Empty optional quick-play mode, account-id, and resolution placeholders
  are omitted with their flags instead of being passed to the client as
  unresolved or blank arguments.
- [x] This phase changes supervisor process launch construction only and adds
  no public gameplay action, generated route family, CLI gameplay catalog, or
  scenario shortcut.

Verification:

- `mise exec -- gradle :daemon:test --tests 'com.minekube.craftless.daemon.LocalSessionApiServerTest.process client runtime launcher starts prepared command'`
- `mise exec -- gradle :daemon:test --tests 'com.minekube.craftless.daemon.CachePreparationServiceTest.cache preparation resolves and stores minecraft version metadata'`
- `mise exec -- gradle :daemon:test :daemon:ktlintCheck :daemon:detekt`
- `mise run architecture-check`
- `mise run lint`
- `mise run ci`

## Phase 42: Standard Asset Object Layout

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-42-standard-asset-object-layout-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-42-standard-asset-object-layout-plan.md`.
- [x] Cache preparation stores Minecraft asset objects in Mojang's standard
  `cache/assets/objects/<first-two-hash-chars>/<hash>` layout instead of a
  Craftless-specific hashed `.asset` filename.
- [x] Cache preparation still derives object download sources from the Mojang
  asset hash and validates hashes before using them in cache handles.
- [x] This phase changes supervisor cache preparation only and adds no public
  gameplay action, generated route family, CLI gameplay catalog, Fabric
  descriptor/binding pair, scenario shortcut, or custom asset serving route.

Verification:

- `mise exec -- gradle :daemon:test --tests 'com.minekube.craftless.daemon.CachePreparationServiceTest.cache preparation resolves and stores minecraft version metadata'`
- `mise exec -- gradle :daemon:test --tests 'com.minekube.craftless.daemon.CachePreparationServiceTest.cache preparation rejects invalid minecraft asset hashes before writing cache handles'`
- `mise exec -- gradle :daemon:test`
- `mise run lint`
- `mise run architecture-check`
- `mise run ci`

## Phase 43: Client Logging Config

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-43-client-logging-config-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-43-client-logging-config-plan.md`.
- [x] Cache preparation detects Mojang `logging.client.file` metadata, caches
  the client logging config under the selected Minecraft version cache root,
  and includes it in prepared artifacts.
- [x] Prepared launch arguments append the Mojang `logging.client.argument`
  with `${path}` resolved to the prepared logging config handle.
- [x] Logging config ids are validated before cache handles are derived.
- [x] This phase changes supervisor cache/launch metadata only and adds no
  public gameplay action, generated route family, CLI gameplay catalog, Fabric
  descriptor/binding pair, scenario shortcut, or custom logging API.

Verification:

- `mise exec -- gradle :daemon:test --tests 'com.minekube.craftless.daemon.CachePreparationServiceTest.cache preparation resolves and stores minecraft version metadata'`
- `mise exec -- gradle :daemon:test --tests 'com.minekube.craftless.daemon.CachePreparationServiceTest.cache preparation rejects invalid minecraft logging config ids before writing cache handles'`
- `mise exec -- gradle :protocol:test :daemon:test`
- `mise run lint`
- `mise run architecture-check`
- `mise run ci`

## Phase 44: Asset Index Id

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-44-asset-index-id-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-44-asset-index-id-plan.md`.
- [x] Cache preparation reads Mojang `assetIndex.id` from the selected
  Minecraft version manifest instead of assuming it equals the requested
  Minecraft version.
- [x] Cache preparation stores asset indexes under
  `cache/assets/indexes/<assetIndex.id>.json`.
- [x] Prepared launch arguments resolve `${assets_index_name}` to
  `assetIndex.id`.
- [x] Asset index ids are validated before cache handles are derived.
- [x] This phase changes supervisor cache/launch metadata only and adds no
  public gameplay action, generated route family, CLI gameplay catalog, Fabric
  descriptor/binding pair, scenario shortcut, custom asset serving API, or
  version-specific hard-coded asset id.

Verification:

- `mise exec -- gradle :daemon:test --tests 'com.minekube.craftless.daemon.CachePreparationServiceTest.cache preparation resolves and stores minecraft version metadata'`
- `mise exec -- gradle :daemon:test --tests 'com.minekube.craftless.daemon.CachePreparationServiceTest.cache preparation rejects invalid asset index ids before writing cache handles'`
- `mise exec -- gradle :daemon:test`
- `mise run lint`
- `mise run architecture-check`
- `mise run ci`

## Phase 45: Descriptor-Derived Graph Schemas

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-45-descriptor-derived-graph-schemas-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-45-descriptor-derived-graph-schemas-plan.md`.
- [x] Fabric runtime graph operation projection resolves existing discovered
  Craftless action descriptors once per operation.
- [x] Runtime operation arguments derive nested `RuntimeSchema` metadata from
  descriptor argument schemas.
- [x] Runtime operation results derive `RuntimeSchema` metadata and required
  property flags from descriptor result schemas.
- [x] This phase changes Fabric graph projection only, preserves explicit
  runtime-only schemas, and adds no public gameplay action, generated route
  family, CLI gameplay catalog, Fabric descriptor/binding pair, scenario
  shortcut, or invocation behavior.

Verification:

- `mise exec -- gradle :driver-fabric:test --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricCapabilityProbeTest.fabric graph operations derive result schema from action descriptors'`
- `mise exec -- gradle :driver-fabric:test --tests '*FabricCapabilityProbeTest*'`
- `mise run lint`
- `mise run architecture-check`
- `mise run ci`

## Phase 46: Compiled Fabric Lane Metadata

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-46-compiled-fabric-lane-metadata-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-46-compiled-fabric-lane-metadata-plan.md`.
- [x] Kotlin-side current compiled Fabric lane metadata is centralized in one
  internal runtime object.
- [x] The compatibility matrix derives the supported current lane from that
  metadata object.
- [x] The current-lane provider derives its provider id and supported Minecraft
  version from that metadata object.
- [x] Client smoke and final gameplay plan defaults derive their Minecraft
  version from that metadata object.
- [x] Runtime metadata providers, smoke controller client creation, and smoke
  plan wording are guarded against reintroducing duplicated compiled-lane
  string literals in product runtime code.
- [x] This phase changes internal Fabric lane metadata only, preserves the
  explicit latest-release unsupported `26.2` lane evidence, and adds no public gameplay action,
  generated route family, CLI gameplay catalog, Fabric descriptor/binding pair,
  scenario shortcut, new compiled lane, or public version-specific API.

Verification:

- `mise exec -- gradle :driver-fabric:test --tests '*FabricCompatibilityMatrixTest*' --tests '*FabricCurrentLaneRuntimeProviderTest*' --tests '*FabricDriverModuleTest*'`
- `mise exec -- gradle :driver-fabric:test`
- `mise run lint`
- `mise run architecture-check`
- `mise run ci`
- Remote GitHub Actions `ci` run `28293561483` passed for implementation
  commit `79cf636`.

## Phase 47: Compiled Fabric Resource Metadata

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-47-compiled-fabric-resource-metadata-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-47-compiled-fabric-resource-metadata-plan.md`.
- [x] `driver-fabric/build.gradle.kts` defines build-time compiled-lane values
  for the current Minecraft, Yarn mappings, Fabric Loader, Fabric API, Java
  major, lane id, and provider id.
- [x] Fabric dependencies and smoke runtime-lane JSON use those build-time
  compiled-lane values.
- [x] Source `fabric.mod.json` uses Gradle-expanded placeholders for compiled
  lane metadata instead of hard-coded current-lane strings.
- [x] Processed `fabric.mod.json` keeps honest compiled-lane wording and
  dependency values aligned with `FabricCompiledLaneMetadata`.
- [x] This phase changes build/resource metadata only and adds no public
  gameplay action, generated route family, CLI gameplay catalog, Fabric
  descriptor/binding pair, scenario shortcut, new compiled lane, or public
  version-specific API.

Verification:

- `mise exec -- gradle :driver-fabric:processResources :driver-fabric:test --tests '*FabricDriverModuleTest.fabric mod source metadata is expanded from compiled lane placeholders*' --tests '*FabricDriverModuleTest.fabric metadata declares client entrypoint and mixin config*'`
- `mise exec -- gradle :driver-fabric:processResources :driver-fabric:test`
- `mise run lint`
- `mise run architecture-check`
- `mise run ci`

## Phase 48: Stable Fabric Entrypoint Boundary

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-48-stable-fabric-entrypoint-boundary-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-48-stable-fabric-entrypoint-boundary-plan.md`.
- [x] `fabric.mod.json` points at the stable non-versioned Craftless Fabric
  entrypoint.
- [x] Current compiled-lane startup logic is behind an internal
  `FabricCurrentLaneBootstrap` in the version-scoped implementation package.
- [x] Bytecode-sensitive mixin/accessor package metadata remains version-scoped.
- [x] This phase changes Fabric entrypoint wiring only and adds no public
  gameplay action, generated route family, CLI gameplay catalog, Fabric
  descriptor/binding pair, scenario shortcut, new compiled lane, or public
  version-specific API.

Verification:

- `mise exec -- gradle --no-daemon :driver-fabric:test --tests '*FabricDriverModuleTest.fabric metadata declares client entrypoint and mixin config*' --tests '*FabricDriverModuleTest.fabric mod source metadata is expanded from compiled lane placeholders*'`
- `mise exec -- gradle --no-daemon :driver-fabric:test`
- `mise run lint`
- `mise run architecture-check`
- `mise run ci`

## Phase 49: README Current Status Alignment

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-49-readme-current-status-alignment-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-49-readme-current-status-alignment-plan.md`.
- [x] README cache-prepare examples use the current compiled Fabric Loader
  lane instead of stale loader metadata.
- [x] README active status no longer presents diagnostic server-side item
  provisioning as current product evidence.
- [x] README documents final gameplay evidence as public-agent generated API
  composition without server-provisioned inventory or manual movement for
  Craftless.
- [x] Bun distribution tests guard README quickstarts and reject stale
  provisioning/status wording.
- [x] This phase changes README/product docs only and adds no public gameplay
  action, generated route family, CLI gameplay catalog, Fabric
  descriptor/binding pair, scenario shortcut, new compiled lane, or public
  version-specific API.

Verification:

- `mise exec -- bun test playwright/src/distribution.test.ts`
- `git diff --check`
- `mise run architecture-check`
- `mise run ci`

## Phase 50: Latest Release Lane Evidence

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-50-latest-release-lane-evidence-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-50-latest-release-lane-evidence-plan.md`.
- [x] The real latest-release `26.2` compatibility lane remains unsupported
  Fabric client runtime evidence, not supported version breadth. Refreshed
  evidence on 2026-06-28 confirms the Mojang manifest still reports latest
  release `26.2` and latest snapshot `26.3-snapshot-1`:
  `docs/superpowers/evidence/2026-06-26-version-26-compatibility-probe.md`.
- [x] The unsupported `26.2` lane id is `latest-release-26-2` instead of
  simulated wording.
- [x] Runtime provider evidence uses `no-compatible-client-lane` and
  `runtime-lane-missing`.
- [x] Gradle-generated smoke `runtime-lane.json` uses the same latest-release
  unsupported lane ids as the Kotlin compatibility matrix.
- [x] Runtime capability evidence remains sanitized and does not expose
  Fabric/Yarn/Minecraft names as public contracts.
- [x] This phase changes compatibility evidence naming only and adds no public
  gameplay action, generated route family, CLI gameplay catalog, Fabric
  descriptor/binding pair, scenario shortcut, new compiled lane, or public
  version-specific API.

Verification:

- `mise exec -- gradle :driver-fabric:test --tests '*FabricCompatibilityMatrixTest*' --tests '*FabricCapabilityProbeTest.runtime metadata probe emits sanitized compatibility lane evidence*' :testkit:test --tests '*LocalMinecraftServerSmokeTest.local server smoke records unsupported runtime lane without provisioning server*'`
- `git diff --check`
- `mise run architecture-check`
- `mise run ci`

## Phase 51: Fabric Bootstrap Selection Boundary

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-51-fabric-bootstrap-selection-boundary-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-51-fabric-bootstrap-selection-boundary-plan.md`.
- [x] Stable Fabric entrypoint calls a non-versioned internal bootstrap
  selector instead of importing the current version-scoped bootstrap directly.
- [x] A stable internal `FabricDriverBootstrap` contract exposes provider id,
  Minecraft version, and initialization.
- [x] The current compiled-lane bootstrap implements that contract while
  retaining current startup behavior.
- [x] Selector tests prove current compiled-lane metadata without initializing
  Minecraft.
- [x] This phase changes internal Fabric startup selection only and adds no
  public gameplay action, generated route family, CLI gameplay catalog, Fabric
  descriptor/binding pair, scenario shortcut, new compiled lane, or public
  version-specific API.

Verification:

- `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.stable fabric entrypoint delegates through bootstrap selector only*' --tests '*FabricBootstrapSelectorTest*'`
- `git diff --check`
- `mise exec -- gradle :driver-fabric:test`
- `mise run lint`
- `mise run architecture-check`
- `mise run ci`

## Phase 52: Stable Fabric Version Boundary Guard

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-52-stable-fabric-version-boundary-guard-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-52-stable-fabric-version-boundary-guard-plan.md`.
- [x] `FabricBootstrapSelector` exposes registered bootstrap metadata without
  initializing Minecraft.
- [x] Selector metadata matches the supported current lane in
  `defaultFabricCompatibilityMatrix()`.
- [x] Source architecture tests scan the stable top-level Fabric production
  package and allow version-scoped implementation imports only from
  `FabricBootstrapSelector.kt`.
- [x] This phase changes internal Fabric startup guardrails only and adds no
  public gameplay action, generated route family, CLI gameplay catalog, Fabric
  descriptor/binding pair, scenario shortcut, new compiled lane, or public
  version-specific API.

Verification:

- `mise exec -- gradle :driver-fabric:test --tests '*FabricBootstrapSelectorTest*' --tests '*FabricDriverModuleTest.stable fabric production package imports versioned implementations only through bootstrap selector*'`
- `git diff --check`
- `mise exec -- gradle :driver-fabric:test`
- `mise run lint`
- `mise run architecture-check`
- `mise run ci`
- Remote GitHub Actions `ci` run `28293149080` passed for implementation
  commit `3f01b84`.

## Phase 53: Matrix-Authoritative Fabric Provider Selection

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-53-matrix-authoritative-fabric-provider-selection-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-53-matrix-authoritative-fabric-provider-selection-plan.md`.
- [x] `selectFabricRuntimeProvider(...)` resolves identities through the
  compatibility matrix before evaluating provider support.
- [x] Unsupported matrix lanes such as latest-release `26.2` fail with
  machine-readable matrix evidence even if a provider would report support.
- [x] Supported matrix lanes require a provider whose id matches the lane's
  provider id.
- [x] Current compiled-lane provider selection still succeeds through the
  matrix-backed selector.
- [x] This phase changes internal Fabric runtime provider selection only and
  adds no public gameplay action, generated route family, CLI gameplay catalog,
  Fabric descriptor/binding pair, scenario shortcut, new compiled lane, or
  public version-specific API.

Verification:

- `mise exec -- gradle :driver-fabric:test --tests '*FabricRuntimeProviderTest*' --tests '*FabricCurrentLaneRuntimeProviderTest*'`
- `git diff --check`
- `mise exec -- gradle :driver-fabric:test`
- `mise run lint`
- `mise run architecture-check`
- `mise run ci`

## Phase 54: Public-Agent Timeout Boundary

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-54-public-agent-timeout-boundary-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-54-public-agent-timeout-boundary-plan.md`.
- [x] `PublicAgentGameplayRunnerConfig` keeps
  `CRAFTLESS_PUBLIC_AGENT_ACTION_REQUEST_TIMEOUT_MS` as the highest-precedence
  override.
- [x] `PublicAgentGameplayRunnerConfig` prefers
  `CRAFTLESS_FABRIC_SMOKE_ACTION_TIMEOUT_MS` over the long outer
  `CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS` when no explicit public-agent timeout is
  set.
- [x] `fabricFinalGameplay` exports
  `CRAFTLESS_PUBLIC_AGENT_ACTION_REQUEST_TIMEOUT_MS` below the Fabric helper
  action timeout so blocker artifacts can be written before the smoke
  controller times out the helper process.
- [x] `FabricClientSmokeController` gives the public-agent helper process the
  long outer smoke timeout while keeping generated-action requests on the
  shorter Fabric action timeout.
- [x] `mise run architecture-check` runs Gradle test targets as separate
  invocations before the Bun Playwright tests so the local verification gate
  does not hit Gradle binary test-result collisions.
- [x] Final gameplay has been rerun after this timeout boundary, with either a
  ready-for-Robin hold or preserved blocker artifacts from the next concrete
  public-agent failure.
- [x] This phase changes public-agent evidence plumbing only and adds no
  public gameplay action, generated route family, CLI gameplay catalog, Fabric
  descriptor/binding pair, scenario shortcut, new compiled lane, public
  version-specific API, or new Minecraft support claim.

Verification:

- `mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest.runner config prefers fabric smoke action timeout over outer smoke timeout*' :driver-fabric:test --tests '*FabricDriverModuleTest.final gameplay config exports public agent action request timeout below fabric action timeout*'`
- `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric smoke controller gives public agent process the outer smoke timeout*'`
- `git diff --check`
- `mise exec -- gradle :testkit:test`
- `mise exec -- gradle :driver-fabric:test`
- `mise run lint`
- `mise run architecture-check`
- `mise run ci`
- Remote GitHub Actions `ci` run `28294009718` passed for implementation
  commit `ef388b9`.

## Phase 55: Public-Agent Pickup Convergence

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-55-public-agent-pickup-convergence-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-55-public-agent-pickup-convergence-plan.md`.
- [x] The public-agent runner keeps pickup behavior outside the product API and
  composes generated `entity.query`, `player.query`, `player.look`,
  `player.move`, and `inventory.query`.
- [x] Fallback generated pickup movement sends `jump=true` when public
  `player.query` and target coordinates show the visible material drop is
  meaningfully above the player.
- [x] Existing bounded material pickup evidence remains based on public
  inventory proof; accepted movement alone is not completion evidence.
- [x] Final gameplay has been rerun after this pickup convergence correction.
  Current 2026-06-27 held evidence has `publicAgentState=RAN`, no
  `public-agent-blocked.json`, `final-gameplay-ready.json` for
  `127.0.0.1:61963`, generated `entity.attack` killed a Sheep, and final
  `inventory.query` showed `White Wool` and `Raw Mutton`. The hold expired at
  `2026-06-27 18:44:16 CEST`; this is diagnostic history only. Final
  completion remains open on the active Codex-verifiable gates.
- [x] This phase changes public-agent acceptance policy only and adds no
  public gameplay action, generated route family, CLI gameplay catalog, Fabric
  descriptor/binding pair, scenario shortcut, new compiled lane, public
  version-specific API, or new Minecraft support claim.

Verification:

- `mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest.runner jumps during generated pickup fallback movement toward elevated material drop*'`
- `git diff --check`
- `mise exec -- gradle :testkit:test`
- `mise exec -- gradle :driver-fabric:test`
- `mise run lint`
- `mise run architecture-check`
- `mise run ci`

## Phase 56: Final Gameplay Timeout Budget

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-56-final-gameplay-timeout-budget-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-56-final-gameplay-timeout-budget-plan.md`.
- [x] Final gameplay timeout budgets are non-circular: outer local-smoke
  timeout covers public-agent helper process timeout plus human confirmation
  hold plus buffer, instead of reusing the shorter Fabric generated-action
  timeout.
- [x] `FabricClientSmokeController` parses a dedicated public-agent helper
  process timeout while keeping per-action generated HTTP timeout separate.
- [x] Held final gameplay has been rerun after this correction and exited with
  `final-gameplay-confirmation-timeout.json` instead of a Gradle process
  timeout. Current 2026-06-27 evidence has `publicAgentState=RAN`, no
  `public-agent-blocked.json`, `final-gameplay-ready.json` for
  `127.0.0.1:49973`, generated `entity.attack` killed a Sheep, final
  `inventory.query` showed `Raw Mutton`, and the Gradle task exited
  successfully after the configured hold without Robin's confirmation chat.
- [x] A later 2026-06-27 held rerun again reached the ready window and exited
  with `final-gameplay-confirmation-timeout.json` instead of a process timeout,
  but the Gradle task failed afterward because `server-evidence.jsonl` had
  been cleared by a nested child process and no longer contained the expected
  Craftless chat evidence. Evidence: `final-gameplay-ready.json` for
  `127.0.0.1:53413`, generated actions earned a `Wooden Sword`,
  `entity.attack` killed a Pig, final `inventory.query` showed `Raw Porkchop`,
  and no `final-gameplay-confirmation.json` was written before the hold
  expired. Phase 57 corrects the child environment isolation bug exposed by
  this run.
- [x] This phase changes final-gameplay timeout/evidence plumbing only and adds
  no public gameplay action, generated route family, CLI gameplay catalog,
  Fabric descriptor/binding pair, scenario shortcut, new compiled lane, public
  version-specific API, or new Minecraft support claim.

Verification:

- `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric final gameplay outer timeout covers public agent runtime and human hold window*' --tests '*FabricDriverModuleTest.fabric smoke controller parses public agent process timeout separately from outer smoke timeout*'`
- `git diff --check`
- `mise run lint`
- `mise run architecture-check`
- `mise run ci`
- `CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS=90000 CRAFTLESS_FABRIC_SMOKE_ACTION_TIMEOUT_MS=120000 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=1800000 mise exec -- gradle :driver-fabric:fabricFinalGameplay`

## Phase 57: Final Gameplay Child Environment Isolation

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-57-final-gameplay-child-environment-isolation-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-57-final-gameplay-child-environment-isolation-plan.md`.
- [x] Fabric smoke child command environments strip inherited local-server and
  final-gameplay owner variables before adding public-agent or ready-specific
  variables.
- [x] Public-agent and ready-notification child commands preserve their
  explicit child-specific environment variables while avoiding inherited server
  ownership flags.
- [x] Held final gameplay has been rerun after this correction and no longer
  fails because `server-evidence.jsonl` is cleared after join/chat evidence was
  recorded. Current 2026-06-27 evidence kept `server-evidence.jsonl` with
  `Player543` join and `hello from Craftless final gameplay` chat. The same run
  exposed a separate blocked-outcome propagation bug tracked in Phase 58.
- [x] This phase changes final-gameplay subprocess environment isolation only
  and adds no public gameplay action, generated route family, CLI gameplay
  catalog, Fabric descriptor/binding pair, scenario shortcut, new compiled
  lane, public version-specific API, or new Minecraft support claim.

Verification:

- `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric smoke child commands do not inherit server owner environment*' --tests '*FabricDriverModuleTest.fabric smoke controller runs process external public agent command with live daemon url*' --tests '*FabricDriverModuleTest.fabric smoke controller runs ready notification command with live session context*' --tests '*FabricDriverModuleTest.fabric smoke controller writes confirmation timeout artifact when Robin chat is not observed*'`
- `git diff --check`
- `mise run lint`
- `mise run architecture-check`
- `mise run ci`
- `CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS=90000 CRAFTLESS_FABRIC_SMOKE_ACTION_TIMEOUT_MS=120000 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=1800000 mise exec -- gradle :driver-fabric:fabricFinalGameplay`

## Phase 58: Public Agent Blocked Outcome Propagation

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-58-public-agent-blocked-outcome-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-58-public-agent-blocked-outcome-plan.md`.
- [x] A blocked public-agent helper artifact makes the Fabric final-gameplay
  controller fail before writing `final-gameplay-ready.json`.
- [x] The existing successful ready-notification and Robin confirmation paths
  remain covered by focused regression tests.
- [x] Local gates have been rerun after the blocked-outcome propagation change.
- [x] This phase changes final-gameplay outcome propagation only and adds no
  public gameplay action, generated route family, CLI gameplay catalog, Fabric
  descriptor/binding pair, scenario shortcut, new compiled lane, public
  version-specific API, or new Minecraft support claim.

Verification:

- `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric smoke controller does not enter ready hold when public agent reports blocked*' --tests '*FabricDriverModuleTest.fabric smoke controller runs ready notification command with live session context*' --tests '*FabricDriverModuleTest.fabric smoke controller stops final session after configured chat confirmation evidence*'`
- `git diff --check`
- `mise run lint`
- `mise run architecture-check`
- `mise run ci`

## Phase 59: Pathfinder Interaction Goal

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-59-pathfinder-interaction-goal-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-59-pathfinder-interaction-goal-plan.md`.
- [x] The private Fabric pathfinder adapter prefers an interaction-reachable
  block goal when the runtime pathfinder exposes one.
- [x] The private Fabric pathfinder adapter preserves exact block goal fallback
  when no interaction-reachable goal is available.
- [x] Public `navigation.plan`, `navigation.follow`, OpenAPI, events, and
  artifacts remain Craftless-owned and do not expose backend class names.
- [x] Held final gameplay has been rerun after this correction and either
  advances beyond the Phase 57/58 material navigation blocker or records a
  newer precise generic blocker without server evidence being cleared or a
  ready artifact being written for blocked gameplay. Current 2026-06-27
  evidence reached `final-gameplay-ready.json` for `127.0.0.1:59029`, kept
  `server-evidence.jsonl` join/chat/disconnect evidence for `Player988`, and
  public-agent generated actions equipped a `Wooden Sword`, killed a Pig, and
  picked up `Raw Porkchop`; the run then wrote
  `final-gameplay-confirmation-timeout.json` because Robin's confirmation chat
  was not observed.
- [x] This phase changes private pathfinder adapter goal selection only and adds
  no public gameplay action, generated route family, CLI gameplay catalog,
  Fabric descriptor/binding pair, scenario shortcut, new compiled lane, public
  version-specific API, or new Minecraft support claim.

Verification:

- `mise exec -- gradle :driver-fabric:test --tests '*ReflectiveFabricPathfinderBackendTest.reflection backend prefers interaction reachable block goal when available*'`
- `mise exec -- gradle :driver-fabric:test --tests '*ReflectiveFabricPathfinderBackendTest*'`
- `git diff --check`
- `mise run lint`
- `mise run architecture-check`
- `mise run ci`
- `CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS=90000 CRAFTLESS_FABRIC_SMOKE_ACTION_TIMEOUT_MS=120000 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=1800000 mise exec -- gradle :driver-fabric:fabricFinalGameplay`

## Phase 60: Final Gameplay Join Handoff

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-60-final-gameplay-join-handoff-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-60-final-gameplay-join-handoff-plan.md`.
- [x] `final-gameplay-ready.json` includes the configured confirmation phrase
  when the final gameplay hold requires Robin's Minecraft chat confirmation.
- [x] `final-gameplay-join-instructions.txt` is written at the same ready
  boundary with the server address, client id, base URL, artifacts directory,
  hold duration, and exact confirmation phrase.
- [x] Existing final confirmation and timeout semantics remain unchanged: the
  join handoff does not mark completion, bypass Robin's chat confirmation, or
  treat a timeout as success.
- [x] This phase changes final-gameplay handoff evidence only and adds no
  public gameplay action, generated route family, CLI gameplay catalog, Fabric
  descriptor/binding pair, scenario shortcut, new compiled lane, public
  version-specific API, or new Minecraft support claim.

Verification:

- `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric smoke controller runs ready notification command with live session context*'`
- `git diff --check`
- `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric smoke controller runs ready notification command with live session context*' --tests '*FabricDriverModuleTest.fabric smoke controller stops final session after configured chat confirmation evidence*' --tests '*FabricDriverModuleTest.fabric smoke controller writes confirmation timeout artifact when Robin chat is not observed*'`
- `mise run architecture-check`

## Phase 61: Local Server Action Environment Boundary

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-61-local-server-action-env-boundary-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-61-local-server-action-env-boundary-plan.md`.
- [x] The local-server smoke action command strips inherited outer local-server
  owner variables such as `CRAFTLESS_LOCAL_SERVER_SMOKE`,
  `CRAFTLESS_LOCAL_SERVER_SMOKE_ROOT`, and
  `CRAFTLESS_SMOKE_ACTION_COMMAND_JSON`.
- [x] The local-server smoke action command preserves Fabric client smoke and
  final-gameplay/public-agent child variables so `:driver-fabric:runClient`
  still enters the Fabric smoke controller with pathfinder/final-gameplay
  context instead of opening a normal unmanaged client.
- [x] Held final gameplay has been rerun after this correction and reaches
  final ready handoff with `server-evidence.jsonl`; when Robin did not join,
  it wrote `final-gameplay-confirmation-timeout.json`.
- [x] This phase changes final-gameplay harness process isolation only and adds
  no public gameplay action, generated route family, CLI gameplay catalog,
  Fabric descriptor/binding pair, scenario shortcut, new compiled lane, public
  version-specific API, or new Minecraft support claim.

Verification:

- `mise exec -- gradle :testkit:test --tests 'com.minekube.craftless.testkit.LocalMinecraftServerSmokeTest.local server action command environment removes outer owner variables and keeps child smoke variables'`
- `mise exec -- gradle :testkit:test`
- `git diff --check`
- `mise run architecture-check`
- `CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS=90000 CRAFTLESS_FABRIC_SMOKE_ACTION_TIMEOUT_MS=120000 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=1800000 mise exec -- gradle :driver-fabric:fabricFinalGameplay`

## Phase 62: Final Gameplay Activity Hold

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-62-final-gameplay-activity-hold-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-62-final-gameplay-activity-hold-plan.md`.
- [x] The final gameplay confirmation hold can extend after observed
  non-confirmation Minecraft chat activity so Robin can keep playing before
  sending the required completion phrase.
- [x] Timeout remains a non-success outcome, and activity does not bypass
  Robin's explicit Minecraft chat confirmation.
- [x] This phase changes final-gameplay harness evidence/lifetime only and adds
  no public gameplay action, generated route family, CLI gameplay catalog,
  Fabric descriptor/binding pair, scenario shortcut, new compiled lane, public
  version-specific API, or new Minecraft support claim.

Verification:

- `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric smoke controller extends final gameplay hold on chat activity*'`
- `git diff --check`

## Phase 63: Public Agent Partial Recipe Material

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-63-public-agent-partial-recipe-material-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-63-public-agent-partial-recipe-material-plan.md`.
- [x] The external public-agent runner can continue into generic recipe
  discovery/crafting after public inventory evidence proves at least one
  material item and later collection navigation fails.
- [x] The runner still uses generated generic actions only and adds no product
  gameplay action, generated route family, CLI gameplay catalog, Fabric
  descriptor/binding pair, scenario shortcut, new compiled lane, public
  version-specific API, or new Minecraft support claim.

Verification:

- `mise exec -- gradle :testkit:test --tests 'com.minekube.craftless.testkit.PublicAgentGameplayRunnerTest' --rerun-tasks`
- `git diff --check`

## Phase 64: Public Agent Live Co-Play Guidance

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-64-public-agent-live-coplay-guidance-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-64-public-agent-live-coplay-guidance-plan.md`.
- [x] The repo-local public gameplay agent skill documents that agents must
  read generated per-client OpenAPI schemas before invoking actions and shows
  the current generated block-goal shape for `navigation.plan`.
- [x] The same skill documents live co-play behavior: use Minecraft chat and
  Craftless events, verify movement through public state, and treat only clear
  standalone messages such as `stop`, `stopp`, or `halt` as stop commands.
- [x] This phase changes only agent guidance and docs. It adds no public
  gameplay action, generated route family, CLI gameplay catalog, Fabric
  descriptor/binding pair, scenario shortcut, new compiled lane, public
  version-specific API, or new Minecraft support claim.

Verification:

- `git diff --check`

## Phase 65: Codex Evidence Completion Gate

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-65-codex-evidence-completion-gate-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-65-codex-evidence-completion-gate-plan.md`.
- [x] Active completion docs no longer require Robin's Minecraft chat
  confirmation; human co-play remains diagnostic evidence only.
- [x] The final completion gate now requires Codex-verifiable public API/CLI
  evidence across CI, CLI smoke, Docker smoke, release/install checks,
  latest-version compatibility evidence, representative older-version
  evidence, and honest survival gameplay.
- [x] This phase changes completion governance only and adds no public gameplay
  action, generated route family, CLI gameplay catalog, Fabric
  descriptor/binding pair, scenario shortcut, new compiled lane, public
  version-specific API, or new Minecraft support claim.

Verification:

- `git diff --check`
- `rg -n "Robin writes|Robin confirms|goal may be completed|required Robin|must.*Robin|Minecraft chat confirmation.*required" README.md AGENTS.md docs/roadmap.md docs/final-gameplay-runbook.md .agents/skills/craftless-public-gameplay-agent/SKILL.md -S`
- `mise exec -- bun test playwright`
- `mise run architecture-check`
- Remote GitHub Actions `ci` run `28304667338` passed for implementation
  commit `9da4207`.

## Phase 66: Representative Older Release Lane Evidence

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-66-representative-older-release-lane-evidence-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-66-representative-older-release-lane-evidence-plan.md`.
- [x] Mojang metadata for Minecraft `1.20.6` is recorded as a representative
  older release with Java major version `21`. Evidence:
  `docs/superpowers/evidence/2026-06-28-representative-older-release-lane-evidence.md`.
- [x] The compatibility matrix resolves `1.20.6` to
  `older-release-1-20-6` with status `UNSUPPORTED`, provider
  `no-compatible-client-lane`, and reason `runtime-lane-missing`.
- [x] Unknown versions still use the generic unsupported fallback, so known
  older-release evidence does not become broad support.
- [x] This phase changes compatibility evidence only and adds no public
  gameplay action, generated route family, CLI gameplay catalog, Fabric
  descriptor/binding pair, scenario shortcut, new compiled lane, public
  version-specific API, or new Minecraft support claim.

Verification:

- `mise exec -- bun -e 'const manifest = await (await fetch("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")).json(); const v = manifest.versions.find((version) => version.id === "1.20.6"); if (!v) throw new Error("missing 1.20.6"); const meta = await (await fetch(v.url)).json(); console.log(JSON.stringify({ id: v.id, type: v.type, releaseTime: v.releaseTime, time: v.time, sha1: v.sha1, javaVersion: meta.javaVersion }, null, 2));'`
- `mise exec -- gradle :driver-fabric:test --tests '*FabricCompatibilityMatrixTest*'`

## Phase 67: Final Gameplay Codex Evidence Default

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-67-final-gameplay-codex-evidence-default-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-67-final-gameplay-codex-evidence-default-plan.md`.
- [x] `FabricFinalGameplayPlan.default()` gates completion on generated
  OpenAPI/actions/resources, SSE, no server-side item provisioning, no static
  fallback, and Codex evidence instead of Robin or Minecraft chat
  confirmation.
- [x] `fabricFinalGameplay` no longer injects default
  `CRAFTLESS_FABRIC_SMOKE_CONFIRM_CHAT_CONTAINS`,
  `CRAFTLESS_FABRIC_SMOKE_READY_REMINDER_MS`, or a macOS `say` prompt.
- [x] Explicit diagnostic confirmation remains available when
  `CRAFTLESS_FABRIC_SMOKE_CONFIRM_CHAT_CONTAINS` is set by the operator.
- [x] This phase changes final-gameplay defaults only and adds no public
  gameplay action, generated route family, CLI gameplay catalog, Fabric
  descriptor/binding pair, scenario shortcut, new compiled lane, public
  version-specific API, or new Minecraft support claim.

Verification:

- `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric final gameplay plan gates completion on graph streams and Codex evidence*' --tests '*FabricDriverModuleTest.fabric final gameplay defaults to Codex evidence gate without chat confirmation phrase*' --tests '*FabricDriverModuleTest.fabric smoke controller can hold the final gameplay session open*'`

## Phase 68: Full Codex Evidence Gate Refresh

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-68-full-codex-evidence-gate-refresh-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-68-full-codex-evidence-gate-refresh-plan.md`.
- [x] Full evidence file exists:
  `docs/superpowers/evidence/2026-06-28-full-codex-evidence-gate-refresh.md`.
- [x] Distribution evidence is refreshed: `mise run package-cli`, packaged CLI
  smoke, Docker build, Docker smoke, and install script smoke all succeeded.
- [x] Compatibility evidence is refreshed: live Mojang metadata still reports
  latest release `26.2` requiring Java 25, representative older release
  `1.20.6` requiring Java 21, matrix/probe tests pass, and
  `fabricClientSmoke` records `26.2` as `UNSUPPORTED/runtime-lane-missing`.
- [x] Final gameplay evidence is refreshed under the Phase 67 default with no
  confirmation phrase. The public agent fetched generated OpenAPI,
  actions/resources, and SSE; crafted and equipped a `Wooden Sword`; found
  Cows through `entity.query`; killed a Cow through `entity.attack`; and
  observed `Raw Beef`, `Leather`, and the Cow with `alive:false`.
- [x] This phase records evidence and docs only. It adds no public gameplay
  action, generated route family, CLI gameplay catalog, Fabric
  descriptor/binding pair, scenario shortcut, new compiled lane, public
  version-specific API, or new Minecraft support claim.

Verification:

- `mise run package-cli`
- `build/docker/craftless/bin/craftless server start --once --port 0 --workspace /tmp/craftless-cli-smoke-1782603968`
- `docker build -t craftless:local .`
- `docker run --rm craftless:local /opt/craftless/bin/craftless server start --once --port 0 --workspace /tmp/craftless`
- `tmp="$(mktemp -d /tmp/craftless-install-smoke.XXXXXX)" && CRAFTLESS_VERSION=v0.1.0 CRAFTLESS_INSTALL_DIR="$tmp/bin" CRAFTLESS_HOME="$tmp/home" ./install.sh && "$tmp/bin/craftless" server start --once --port 0 --workspace "$tmp/workspace"`
- `mise exec -- gradle :driver-fabric:test --tests '*FabricCompatibilityMatrixTest*' --tests '*FabricCapabilityProbeTest.runtime metadata probe emits sanitized compatibility lane evidence*' :testkit:test --tests '*LocalMinecraftServerSmokeTest.local server smoke records unsupported runtime lane without provisioning server*'`
- `CRAFTLESS_FABRIC_CLIENT_SMOKE=1 CRAFTLESS_SMOKE_MINECRAFT_VERSION=26.2 CRAFTLESS_LOCAL_SERVER_SMOKE_ROOT=/tmp/craftless-fabric-smoke-26-lane-refresh CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 mise exec -- gradle :driver-fabric:fabricClientSmoke`
- `CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS=90000 CRAFTLESS_FABRIC_SMOKE_ACTION_TIMEOUT_MS=120000 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 mise exec -- gradle :driver-fabric:fabricFinalGameplay`

## Phase 69: README And Roadmap Evidence Alignment

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-69-readme-roadmap-evidence-alignment-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-69-readme-roadmap-evidence-alignment-plan.md`.
- [x] README current status now states that Phase 68 final gameplay evidence
  passed through generated OpenAPI/actions/resources, SSE, public-agent
  composition, `Wooden Sword` crafting/equip, Cow attack, and Raw Beef/Leather
  observation without server-provisioned inventory or static survival macros.
- [x] README still keeps the broader project incomplete until the active
  checklist proves the remaining generic-discovery, multi-version, transport,
  CLI, docs, and gameplay gates with current evidence.
- [x] Roadmap current baseline no longer presents provisioned iron-sword smoke
  as product proof.
- [x] Roadmap describes latest `26.2` and representative older `1.20.6` as
  explicit unsupported Fabric client lanes with live metadata/probe evidence,
  not as supported client breadth.
- [x] This phase changes docs only. It adds no public gameplay action,
  generated route family, CLI gameplay catalog, Fabric descriptor/binding pair,
  scenario shortcut, new compiled lane, public version-specific API, or new
  Minecraft support claim.

Verification:

- `git diff --check`
- `mise run architecture-check`
- `mise run ci`

## Phase 70: Public Agent Operational Workflow

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-70-public-agent-operational-workflow-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-70-public-agent-operational-workflow-plan.md`.
- [x] The public gameplay agent skill now includes adaptive CLI discovery with
  `craftless clients <id> actions`, generic CLI invocation with
  `craftless clients <id> run <action>`, generated invocation through
  `POST /clients/{id}:run`, POST JSON-RPC-style control/query, and
  `GET /clients/{id}/events:stream` as the observation path.
- [x] The same skill keeps missing generic primitives explicit through
  `missing-generic-primitive:<action-or-resource>` and records required final
  gameplay artifacts including `public-agent-state.jsonl`.
- [x] A protocol policy test now prevents the skill from losing the generated
  API/CLI/SSE/JSON-RPC workflow guidance.
- [x] This phase changes agent guidance and policy tests only. It adds no
  public gameplay action, generated route family, CLI gameplay catalog, Fabric
  descriptor/binding pair, scenario shortcut, new compiled lane, public
  version-specific API, or new Minecraft support claim.

Verification:

- `mise exec -- gradle :protocol:test --tests '*NamespacePolicyTest.public gameplay agent skill keeps generated workflow guidance' --rerun-tasks`
- `git diff --check`
- `mise run architecture-check`
- `mise run ci`

## Phase 71: System Java PATH Discovery

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-71-system-java-path-discovery-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-71-system-java-path-discovery-plan.md`.
- [x] The system Java runtime provider discovers fake `java` executables from
  `PATH` without requiring `JAVA_HOME`.
- [x] The discovered `PATH` candidate is still validated by the bounded Java
  validator and reported as provider `SYSTEM`.
- [x] This phase changes supervisor/runtime Java discovery only. It adds no
  public gameplay action, generated route family, CLI gameplay catalog, Fabric
  descriptor/binding pair, scenario shortcut, new compiled lane, public
  version-specific API, or new Minecraft support claim.

Verification:

- `mise exec -- gradle :daemon:test --tests '*JavaRuntimeResolverTest.discovers system Java from PATH without JAVA_HOME'`
- `mise exec -- gradle :daemon:test --tests '*JavaRuntimeResolverTest*'`
- `git diff --check`
- `mise run architecture-check`
- `mise run ci`

## Phase 72: Generated Actions Help

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-72-generated-actions-help-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-72-generated-actions-help-plan.md`.
- [x] `craftless clients <id> actions --help` renders command aliases,
  argument flags, and route evidence from the live per-client OpenAPI document.
- [x] `craftless clients <id> actions` without `--help` still returns JSON
  action metadata.
- [x] This phase changes adaptive CLI rendering only. It adds no public
  gameplay action, generated route family, static CLI gameplay catalog, Fabric
  descriptor/binding pair, scenario shortcut, new compiled lane, public
  version-specific API, or new Minecraft support claim.

Verification:

- `mise exec -- gradle :cli:test --tests '*CraftlessCliTest.clients actions help is generated from live openapi actions'`
- `mise exec -- gradle :cli:test`
- `git diff --check`
- `mise run architecture-check`
- `mise run ci`

## Phase 73: Asset Object Integrity Resume

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-73-asset-object-integrity-resume-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-73-asset-object-integrity-resume-plan.md`.
- [x] Minecraft asset objects carry expected SHA-1 metadata from the asset
  index into cache artifact metadata.
- [x] Cache preparation reuses a cached asset object only when its local SHA-1
  matches the expected object hash.
- [x] Cache preparation re-fetches and replaces corrupt existing asset-object
  files.
- [x] This phase changes cache resumability only. It adds no public gameplay
  action, generated route family, CLI gameplay catalog, Fabric
  descriptor/binding pair, scenario shortcut, new compiled lane, public
  version-specific API, or new Minecraft support claim.

Verification:

- `mise exec -- gradle :daemon:test --tests '*CachePreparationServiceTest.cache preparation refetches corrupt existing asset objects'`
- `mise exec -- gradle :daemon:test --tests '*CachePreparationServiceTest*'`
- `git diff --check`
- `mise run architecture-check`
- `mise run ci`

## Phase 74: Metadata Binary Checksums

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-74-metadata-binary-checksums-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-74-metadata-binary-checksums-plan.md`.
- [x] Minecraft client jar artifacts carry optional SHA-1 from Mojang version
  manifests.
- [x] Minecraft library and native classifier artifacts carry optional SHA-1
  from Mojang version manifests.
- [x] Managed Java runtime file artifacts carry optional SHA-1 from Java
  runtime manifests.
- [x] Fabric profile `downloads.artifact` library artifacts carry optional
  SHA-1 when Fabric metadata provides it.
- [x] Cache preparation re-fetches corrupt cached binary artifacts when
  upstream SHA-1 metadata is known.
- [x] This phase changes cache integrity only. It adds no public gameplay
  action, generated route family, CLI gameplay catalog, Fabric
  descriptor/binding pair, scenario shortcut, new compiled lane, public
  version-specific API, or new Minecraft support claim.

Verification:

- `mise exec -- gradle :daemon:test --tests '*CachePreparationServiceTest.cache preparation refetches corrupt metadata checksum binaries'`
- `mise exec -- gradle :daemon:test --tests '*CachePreparationServiceTest*'`
- `git diff --check`
- `mise run architecture-check`
- `mise run ci`

## Phase 75: Post-Cache-Integrity Evidence Refresh

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-75-post-cache-integrity-evidence-refresh-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-75-post-cache-integrity-evidence-refresh-plan.md`.
- [x] Evidence file is refreshed:
  `docs/superpowers/evidence/2026-06-28-post-cache-integrity-evidence-refresh.md`.
- [x] Distribution evidence is refreshed after Phases 73/74: `mise run
  package-cli`, packaged CLI smoke, Docker build, Docker smoke, and install
  script smoke.
- [x] Compatibility evidence is refreshed after Phases 73/74: live Mojang
  metadata, matrix/probe tests, and the explicit unsupported latest Fabric
  client lane smoke.
- [x] Final public gameplay evidence is refreshed after Phases 73/74 through
  generated OpenAPI/actions/resources and SSE, without server-provisioned
  inventory, manual Craftless movement, or scenario shortcuts.
- [x] Local `git diff --check`, `mise run architecture-check`, and `mise run
  ci` pass after recording the evidence.
- [x] Changes are committed and pushed to `main`, and GitHub Actions CI passes
  for the pushed commit.
- [x] This phase records evidence and docs only. It adds no public gameplay
  action, generated route family, CLI gameplay catalog, Fabric
  descriptor/binding pair, scenario shortcut, new compiled lane, public
  version-specific API, or new Minecraft support claim.

Verification:

- `mise run package-cli`
- packaged CLI `server start --once --port 0`
- `docker build -t craftless:local .`
- Docker `server start --once --port 0`
- installer smoke with `CRAFTLESS_VERSION=v0.1.0`
- live Mojang manifest probe through `mise exec -- bun`
- `mise exec -- gradle :driver-fabric:test --tests '*FabricCompatibilityMatrixTest*' --tests '*FabricCapabilityProbeTest.runtime metadata probe emits sanitized compatibility lane evidence*' :testkit:test --tests '*LocalMinecraftServerSmokeTest.local server smoke records unsupported runtime lane without provisioning server*'`
- `CRAFTLESS_FABRIC_CLIENT_SMOKE=1 CRAFTLESS_SMOKE_MINECRAFT_VERSION=26.2 CRAFTLESS_LOCAL_SERVER_SMOKE_ROOT=/tmp/craftless-fabric-smoke-26-lane-refresh CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 mise exec -- gradle :driver-fabric:fabricClientSmoke`
- `CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS=90000 CRAFTLESS_FABRIC_SMOKE_ACTION_TIMEOUT_MS=120000 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 mise exec -- gradle :driver-fabric:fabricFinalGameplay`
- `git diff --check`
- `mise run architecture-check`
- `mise run ci`

## Phase 76: Completion Audit And Binding Exit

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-76-completion-audit-and-binding-exit-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-76-completion-audit-and-binding-exit-plan.md`.
- [x] Evidence file exists:
  `docs/superpowers/evidence/2026-06-28-completion-audit-and-binding-exit.md`.
- [x] Active completion docs do not require Robin's Minecraft chat
  confirmation; human co-play remains optional diagnostic evidence only.
- [x] Current verified gates are recorded: CLI packaging and smoke, Docker
  build and smoke, install script smoke, Ktor-only JVM HTTP/client/SSE
  governance, mise/Bun-only repository workflow, JSON-RPC-style invocation,
  SSE streaming, compatibility probes, and final honest public API/CLI
  gameplay evidence.
- [x] Current open gates are recorded: public gameplay breadth still depends on
  the transitional hand-written Fabric bootstrap operation definitions, latest `26.2` and
  representative older `1.20.6` are explicit unsupported lanes rather than
  supported runtime lanes, and adding more descriptor/binding pairs is not
  completion progress.
- [x] This phase changes governance and evidence docs only. It adds no public
  gameplay action, generated route family, CLI gameplay catalog, Fabric
  descriptor/binding pair, scenario shortcut, new compiled lane, public
  version-specific API, or new Minecraft support claim.

Verification:

- `git diff --check`
- `mise run architecture-check`
- `mise run ci`
- GitHub Actions CI for the pushed `main` commit

## Phase 77: Graph-Owned Fabric Actions

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-77-graph-owned-fabric-actions-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-77-graph-owned-fabric-actions-plan.md`.
- [x] Public Fabric action descriptors returned by `FabricDriverBackend.actions`
  are projected from `RuntimeCapabilityGraph.operations`.
- [x] Public Fabric action descriptors now use
  `DriverActionSource.RUNTIME_PROBE` instead of `DriverActionSource.BINDING`
  when exposed through `actions()`.
- [x] Transitional `FabricActionBinding` implementations remain private
  execution adapters; the phase does not add new Fabric descriptor/binding
  pairs or gameplay breadth.
- [x] Unit tests preserve generated-operation invocation behavior while
  updating source assertions to the graph-owned public descriptor source.
- [~] The broader binding-exit blocker remains active until descriptor schemas
  and future gameplay breadth no longer depend on hand-maintained bootstrap
  descriptor/schema code.
- [x] This phase adds no public gameplay action, generated route family, CLI
  gameplay catalog, Fabric descriptor/binding pair, scenario shortcut, new
  compiled lane, public version-specific API, or new Minecraft support claim.

Verification:

- Red test:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric public actions are projected from runtime graph instead of binding descriptors*'`
- Green focused test:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric public actions are projected from runtime graph instead of binding descriptors*'`
- Focused regression:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric runtime discovery probes client state before advertising unavailable raycast*' --tests '*FabricDriverModuleTest.fabric runtime discovery exposes player query only from client state*' --tests '*FabricDriverModuleTest.fabric runtime discovery exposes inventory equip only from client state*'`
- Full Fabric regression:
  `mise exec -- gradle :driver-fabric:test`
- Final local and remote verification are recorded in
  `docs/superpowers/evidence/2026-06-28-graph-owned-fabric-actions.md`.

## Phase 78: Graph-Native Fabric Schemas

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-78-graph-native-fabric-schemas-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-78-graph-native-fabric-schemas-plan.md`.
- [x] `FabricCapabilityProbeContext` no longer receives
  `FabricActionBinding` maps for graph schema construction.
- [x] Current bootstrap operation argument/result schemas are represented as
  graph-local `RuntimeSchema` definitions in `FabricCapabilityProbe.kt`.
- [x] `player.chat`, `player.move`, `inventory.equip`, `player.raycast`,
  `world.block.break`, and `world.block.interact` schemas remain available
  without binding descriptor fallback.
- [x] Transitional `FabricActionBinding` implementations remain private
  execution adapters; this phase does not add new Fabric descriptor/binding
  pairs or gameplay breadth.
- [~] The broader binding-exit blocker remains active until future gameplay
  breadth is generated from generic runtime discovery instead of
  hand-maintained bootstrap operation definitions.
- [x] This phase adds no public gameplay action, generated route family, CLI
  gameplay catalog, Fabric descriptor/binding pair, scenario shortcut, new
  compiled lane, public version-specific API, or new Minecraft support claim.

Verification:

- Red test:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricCapabilityProbeTest.fabric capability probe context does not receive action bindings for graph schemas*'`
- Green focused tests:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricCapabilityProbeTest.fabric capability probe context does not receive action bindings for graph schemas*'`
  `mise exec -- gradle :driver-fabric:test --tests '*FabricCapabilityProbeTest.fabric graph schemas stay available without binding descriptor fallback*'`
- Full Fabric regression:
  `mise exec -- gradle :driver-fabric:test`
- Final local and remote verification are recorded in
  `docs/superpowers/evidence/2026-06-28-graph-native-fabric-schemas.md`.

## Phase 79: Graph-Owned Fabric Invoke Dispatch

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-79-graph-owned-fabric-invoke-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-79-graph-owned-fabric-invoke-plan.md`.
- [x] `FabricDriverBackend.invoke(...)` looks up operations in
  `RuntimeCapabilityGraph.operations`.
- [x] Generic invoke compatibility returns graph availability reasons when operations are
  unavailable.
- [x] Generic invoke compatibility dispatches available operations through
  `DriverOperationAdapters`.
- [x] `FabricDriverBackend` no longer accepts or calls `FabricActionDiscovery`
  for public-compatible dispatch.
- [x] Current transitional `FabricActionBinding` implementations are
  consolidated into the private adapter map. This phase does not add new Fabric
  descriptor/binding pairs or gameplay breadth.
- [x] Phase 80 deletes standalone `FabricActionDiscovery`; it is no longer a
  later cleanup target.
- [~] The broader binding-exit blocker remains active until future gameplay
  breadth is generated from generic runtime discovery instead of
  hand-maintained bootstrap operation definitions.
- [x] This phase adds no public gameplay action, generated route family, CLI
  gameplay catalog, Fabric descriptor/binding pair, scenario shortcut, new
  compiled lane, public version-specific API, or new Minecraft support claim.

Verification:

- Green focused tests:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric backend dispatch does not depend on fabric action discovery*' --tests '*FabricDriverModuleTest.fabric compatibility invoke dispatches unavailable operations from runtime graph*' --tests '*FabricDriverModuleTest.fabric compatibility invoke adapters come from private binding map*'`
- Full Fabric regression:
  `mise exec -- gradle :driver-fabric:test`
- Final local and remote verification are recorded in
  `docs/superpowers/evidence/2026-06-28-graph-owned-fabric-invoke.md`.

## Phase 80: Action Discovery Deletion

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-80-action-discovery-deletion-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-80-action-discovery-deletion-plan.md`.
- [x] `FabricActionDiscovery.kt` is deleted.
- [x] `FabricActionDiscovery`, `FabricActionProbe`,
  `FabricActionDiscoveryContext`, `FabricDiscoveredAction`, and
  `defaultFabricActionDiscovery` no longer appear in driver-fabric main or
  test source outside the guard's forbidden-name list.
- [x] `FabricClientCapabilitySnapshot` now belongs to the capability-probe
  graph layer.
- [x] Fabric backend `actions(...)` and `invoke(...)` remain graph-owned.
- [x] This phase adds no public gameplay action, generated route family, CLI
  gameplay catalog, Fabric descriptor/binding pair, scenario shortcut, new
  compiled lane, public version-specific API, or new Minecraft support claim.
- [~] The broader binding-exit blocker remains active until future gameplay
  breadth is generated from generic runtime discovery instead of
  hand-maintained bootstrap operation definitions.

Verification:

- Red guard:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric standalone action discovery layer is removed*'`
  failed before deletion because `FabricActionDiscovery.kt` still existed.
- Green focused guard:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric standalone action discovery layer is removed*'`
- Full Fabric regression:
  `mise exec -- gradle :driver-fabric:test`
- Final local and remote verification are recorded in
  `docs/superpowers/evidence/2026-06-28-action-discovery-deletion.md`.

## Phase 81: HMC Bridge Gameplay Removal

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-81-hmc-bridge-gameplay-removal-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-81-hmc-bridge-gameplay-removal-plan.md`.
- [x] `HmcBridgeDriverBackend` is lifecycle-only and no longer exposes HMC
  bridge-owned gameplay descriptors.
- [x] Invoking `player.chat` or `player.move` against `HmcBridgeDriverBackend`
  returns Craftless-owned `UNSUPPORTED` responses.
- [x] `HmcBridgeBackend` no longer contains chat, move, jump, look, or
  `MoveIntent` helpers, and `ClientAction` is lifecycle-only.
- [x] `RealClientSmokePlan` is launch/lifecycle evidence only and no longer
  lists chat, movement, chat-log, or position-change proof steps.
- [x] Bridge docs no longer say the HMC bridge accepts gameplay actions.
- [x] This phase adds no public gameplay action, generated route family, CLI
  gameplay catalog, Fabric descriptor/binding pair, scenario shortcut, new
  compiled lane, public version-specific API, or new Minecraft support claim.

Verification:

- Red bridge guard:
  `mise exec -- gradle :bridge-hmc:test --tests '*HmcBridgeBackendTest.bridge backend source has no gameplay helpers*'`
- Red runtime guards:
  `mise exec -- gradle :driver-runtime:test --tests '*BackendDriverSessionTest.hmc bridge backend is lifecycle only and exposes no gameplay actions*' --tests '*BackendDriverSessionTest.hmc bridge backend has no static gameplay action catalog*'`
- Green bridge regression:
  `mise exec -- gradle :bridge-hmc:test`
- Green runtime regression:
  `mise exec -- gradle :driver-runtime:test`
- Final local and remote verification are recorded in
  `docs/superpowers/evidence/2026-06-28-hmc-bridge-gameplay-removal.md`.

## Phase 82: README Public Entrypoint Overhaul

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-82-readme-public-entrypoint-overhaul-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-82-readme-public-entrypoint-overhaul-plan.md`.
- [x] README now leads with product shape, status at a glance, install,
  Docker, GitHub Actions, generated API usage, cache/runtime preparation,
  current verification, comparison, roadmap, development, and docs.
- [x] README states that gameplay breadth comes from the runtime capability
  graph and generated per-client OpenAPI.
- [x] README states that HMC bridge code is lifecycle/launch evidence only and
  not a gameplay adapter.
- [x] README preserves current final gameplay evidence without
  server-provisioned inventory.
- [x] Distribution tests guard quickstart strings and the generated-API/bridge
  lifecycle-only wording.
- [x] This phase changes README/docs/tests only and adds no public gameplay
  action, generated route family, CLI gameplay catalog, Fabric
  descriptor/binding pair, scenario shortcut, new compiled lane, public
  version-specific API, or new Minecraft support claim.

Verification:

- Red README guard:
  `mise exec -- bun test playwright`
- Green README guard:
  `mise exec -- bun test playwright`
- Final local gates:
  `git diff --check`
  `mise run ci`
- Final local and remote verification are recorded in
  `docs/superpowers/evidence/2026-06-28-readme-public-entrypoint-overhaul.md`.

## Phase 83: Fabric Binding Descriptor Removal

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-83-fabric-binding-descriptor-removal-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-83-fabric-binding-descriptor-removal-plan.md`.
- [x] `FabricActionBinding` is private execution metadata only and exposes
  `operationId`, not `DriverActionDescriptor`.
- [x] `FabricActionBindings.kt` no longer imports or uses
  `DriverActionDescriptor`, `DriverActionArgument`,
  `DriverActionResultDescriptor`, or `DriverActionResultProperty`.
- [x] Descriptor helper functions are removed from `FabricActionBindings.kt`.
- [x] `FabricDriverBackend.operationAdapters(...)` registers private adapters
  from `operationId`, not `descriptor.id`.
- [x] Existing graph-projected public action schemas and invocation behavior
  remain covered by Fabric tests.
- [~] The broader binding-exit blocker remains active until future gameplay
  breadth is generated from generic runtime discovery instead of
  hand-maintained bootstrap operation definitions.
- [x] This phase adds no public gameplay action, generated route family, CLI
  gameplay catalog, Fabric descriptor/binding pair, scenario shortcut, new
  compiled lane, public version-specific API, or new Minecraft support claim.

Verification:

- Red guards:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric action bindings do not own public descriptors or schemas*' --tests '*FabricDriverModuleTest.fabric operation adapter registration does not use binding descriptors*' --tests '*FabricDriverModuleTest.transitional fabric binding operation ids are represented as runtime graph operations*'`
- Green focused tests:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric action bindings do not own public descriptors or schemas*' --tests '*FabricDriverModuleTest.fabric operation adapter registration does not use binding descriptors*' --tests '*FabricDriverModuleTest.transitional fabric binding operation ids are represented as runtime graph operations*' --tests '*FabricDriverModuleTest.fabric backend exposes bootstrap bindings as graph operation adapters*'`
- Full Fabric regression:
  `mise exec -- gradle :driver-fabric:test`
- Final local and remote verification are recorded in
  `docs/superpowers/evidence/2026-06-28-fabric-binding-descriptor-removal.md`.

## Phase 84: Bootstrap Operation Definition Isolation

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-84-bootstrap-operation-definition-isolation-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-84-bootstrap-operation-definition-isolation-plan.md`.
- [x] Transitional bootstrap operation ids, adapter ids, and schemas are
  isolated in
  `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricBootstrapOperationDefinitions.kt`.
- [x] `FabricClientStateCapabilityProbe` computes live client state,
  resources, handles, and availability, but does not own bootstrap operation
  ids, Fabric adapter ids, operation schemas, or direct bootstrap
  `RuntimeOperationNode` construction.
- [x] Bootstrap operation definitions still project into the runtime graph and
  private executable bindings still attach to graph operation adapters.
- [~] The broader binding-exit blocker remains active until future gameplay
  breadth is generated from generic runtime discovery instead of
  hand-maintained bootstrap operation definitions.
- [x] This phase adds no public gameplay action, generated route family, CLI
  gameplay catalog, Fabric execution binding, scenario shortcut, new compiled
  lane, public version-specific API, or new Minecraft support claim.

Verification:

- Red guards:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric client state probe does not own bootstrap operation definitions*' --tests '*FabricDriverModuleTest.bootstrap operation definitions still project into runtime graph*'`
- Green focused tests:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric client state probe does not own bootstrap operation definitions*' --tests '*FabricDriverModuleTest.bootstrap operation definitions still project into runtime graph*' --tests '*FabricDriverModuleTest.fabric backend exposes bootstrap bindings as graph operation adapters*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-bootstrap-operation-definition-isolation.md`.

## Phase 85: Binding Operation Id Source Ownership

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-85-binding-operation-id-source-ownership-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-85-binding-operation-id-source-ownership-plan.md`.
- [x] `FabricBootstrapOperationIds` is the internal source for bootstrap
  operation id strings.
- [x] `FabricActionBindings.kt` references bootstrap operation id constants
  instead of declaring `operationId = "..."` literals.
- [x] Fabric and protocol policy tests reject duplicated binding operation id
  literals.
- [~] The broader binding-exit blocker remains active until future gameplay
  breadth is generated from generic runtime discovery instead of
  hand-maintained bootstrap operation definitions.
- [x] This phase adds no public gameplay action, generated route family, CLI
  gameplay catalog, Fabric execution binding, scenario shortcut, new compiled
  lane, public version-specific API, or new Minecraft support claim.

Verification:

- Red guards:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric action bindings do not own operation id literals*'`
  and
  `mise exec -- gradle :protocol:test --tests '*NamespacePolicyTest.private fabric gameplay bindings are limited to bootstrap operation id references*'`
- Green focused tests:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric action bindings do not own operation id literals*' --tests '*FabricDriverModuleTest.transitional fabric binding operation ids are represented as runtime graph operations*' --tests '*FabricDriverModuleTest.fabric backend exposes bootstrap bindings as graph operation adapters*'`
  and
  `mise exec -- gradle :protocol:test --tests '*NamespacePolicyTest.private fabric gameplay bindings are limited to bootstrap operation id references*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-binding-operation-id-source-ownership.md`.

## Phase 86: Fabric Adapter Key Source Ownership

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-86-fabric-adapter-key-source-ownership-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-86-fabric-adapter-key-source-ownership-plan.md`.
- [x] `FabricBootstrapOperationAdapters` is the internal source for bootstrap
  private adapter key strings.
- [x] `FabricDriverBackend.kt` registers entity, block-query, and recipe
  adapters with bootstrap adapter constants instead of duplicated
  `fabric.*` literals.
- [x] Existing graph-owned invocation tests still cover private adapter
  registration.
- [~] The broader binding-exit blocker remains active until future gameplay
  breadth is generated from generic runtime discovery instead of
  hand-maintained bootstrap operation definitions.
- [x] This phase adds no public gameplay action, generated route family, CLI
  gameplay catalog, Fabric execution binding, scenario shortcut, new compiled
  lane, public version-specific API, or new Minecraft support claim.

Verification:

- Red guard:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric backend does not own bootstrap adapter key literals*'`
- Green focused tests:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric backend does not own bootstrap adapter key literals*' --tests '*FabricDriverModuleTest.fabric backend exposes bootstrap bindings as graph operation adapters*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-fabric-adapter-key-source-ownership.md`.

## Phase 87: Backend Operation Id Source Ownership

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-87-backend-operation-id-source-ownership-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-87-backend-operation-id-source-ownership-plan.md`.
- [x] `FabricDriverBackend.kt` guards entity, block-query, and recipe adapters
  with `FabricBootstrapOperationIds` constants instead of duplicated bootstrap
  operation-id literals.
- [x] Existing graph-owned invocation tests still cover private adapter
  dispatch.
- [~] The broader binding-exit blocker remains active until future gameplay
  breadth is generated from generic runtime discovery instead of
  hand-maintained bootstrap operation definitions.
- [x] This phase adds no public gameplay action, generated route family, CLI
  gameplay catalog, Fabric execution binding, scenario shortcut, new compiled
  lane, public version-specific API, or new Minecraft support claim.

Verification:

- Red guard:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric backend does not own bootstrap operation id guard literals*'`
- Green focused tests:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric backend does not own bootstrap operation id guard literals*' --tests '*FabricDriverModuleTest.fabric backend exposes bootstrap bindings as graph operation adapters*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-backend-operation-id-source-ownership.md`.

## Phase 88: Binding Adapter Key Derivation Removal

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-88-binding-adapter-key-derivation-removal-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-88-binding-adapter-key-derivation-removal-plan.md`.
- [x] `FabricDriverBackend.kt` registers private binding adapters from
  bootstrap operation definitions instead of deriving adapter keys from
  operation ids.
- [x] Existing graph-owned invocation tests still cover private adapter
  dispatch.
- [~] The broader binding-exit blocker remains active until future gameplay
  breadth is generated from generic runtime discovery instead of
  hand-maintained bootstrap operation definitions.
- [x] This phase adds no public gameplay action, generated route family, CLI
  gameplay catalog, Fabric execution binding, scenario shortcut, new compiled
  lane, public version-specific API, or new Minecraft support claim.

Verification:

- Red guard:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric backend does not derive binding adapter keys from operation ids*'`
- Green focused tests:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric backend does not derive binding adapter keys from operation ids*' --tests '*FabricDriverModuleTest.fabric backend exposes bootstrap bindings as graph operation adapters*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-binding-adapter-key-derivation-removal.md`.

## Phase 89: Navigation Operation Id Source Ownership

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-89-navigation-operation-id-source-ownership-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-89-navigation-operation-id-source-ownership-plan.md`.
- [x] `FabricNavigationDiscovery.kt` owns current transitional navigation and
  task operation ids through `FabricNavigationOperationIds`.
- [x] `FabricDriverBackend.kt` and `FabricClientSmokeController.kt` consume
  navigation operation constants instead of duplicated quoted operation-id
  literals.
- [~] The broader binding-exit blocker remains active until future gameplay
  breadth is generated from generic runtime discovery instead of
  hand-maintained bootstrap/navigation operation definitions.
- [x] This phase adds no public gameplay action, generated route family, CLI
  gameplay catalog, Fabric execution binding, scenario shortcut, new compiled
  lane, public version-specific API, or new Minecraft support claim.

Verification:

- Red guard:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric backend and smoke do not own navigation operation id literals*'`
- Green focused test:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric backend and smoke do not own navigation operation id literals*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-navigation-operation-id-source-ownership.md`.

## Phase 90: Smoke Bootstrap Action Id Source Ownership

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-90-smoke-bootstrap-action-id-source-ownership-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-90-smoke-bootstrap-action-id-source-ownership-plan.md`.
- [x] `FabricClientSmokeController.kt` uses `FabricBootstrapOperationIds`
  constants for bootstrap action calls and public-agent required primitives
  instead of duplicated quoted action-id literals.
- [~] The broader binding-exit blocker remains active until future gameplay
  breadth is generated from generic runtime discovery instead of
  hand-maintained bootstrap/navigation operation definitions.
- [x] This phase adds no public gameplay action, generated route family, CLI
  gameplay catalog, Fabric execution binding, scenario shortcut, new compiled
  lane, public version-specific API, or new Minecraft support claim.

Verification:

- Red guard:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric smoke controller does not own bootstrap action id literals*'`
- Green focused test:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric smoke controller does not own bootstrap action id literals*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-smoke-bootstrap-action-id-source-ownership.md`.

## Phase 91: Version Support Completion Gate

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-91-version-support-completion-gate-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-91-version-support-completion-gate-plan.md`.
- [x] The final completion gate requires runnable support evidence for latest
  and representative older runtime lanes.
- [x] Unsupported latest/older compatibility lanes remain diagnostic evidence
  only and do not satisfy completion.
- [~] Actual latest/current and representative older Fabric client runtime
  support remains open until runnable lane support and generated API/CLI
  gameplay verification land.
- [x] This phase adds no public gameplay action, generated route family, CLI
  gameplay catalog, Fabric execution binding, scenario shortcut, new compiled
  lane, public version-specific API, or new Minecraft support claim.

Verification:

- Red guard:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.completion gate does not accept unsupported version lanes as support*'`
- Green focused test:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.completion gate does not accept unsupported version lanes as support*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-version-support-completion-gate.md`.

## Phase 92: Build-Generated Compiled Lane Metadata

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-92-build-generated-compiled-lane-metadata-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-92-build-generated-compiled-lane-metadata-plan.md`.
- [x] `driver-fabric` generates `FabricCompiledLaneMetadata.kt` from Gradle
  compiled-lane constants under `build/generated/sources`.
- [x] No hand-written `FabricCompiledLaneMetadata.kt` remains under
  `driver-fabric/src/main/kotlin`.
- [x] Kotlin compilation depends on the generated metadata source.
- [~] Actual latest/current and representative older Fabric client runtime
  support remains open until runnable lane support and generated API/CLI
  gameplay verification land.
- [x] This phase adds no public gameplay action, generated route family, CLI
  gameplay catalog, Fabric execution binding, scenario shortcut, new compiled
  lane, public version-specific API, or new Minecraft support claim.

Verification:

- Red guard:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.compiled lane metadata is generated by gradle not handwritten source*'`
- Green focused test:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.compiled lane metadata is generated by gradle not handwritten source*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-build-generated-compiled-lane-metadata.md`.

## Phase 93: Static Unsupported Version Lane Removal

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-93-static-unsupported-version-lane-removal-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-93-static-unsupported-version-lane-removal-plan.md`.
- [x] `defaultFabricCompatibilityMatrix()` no longer catalogs static
  latest/older unsupported lanes.
- [x] `26.2`, `1.20.6`, and other non-provider-backed versions resolve through
  generic unsupported-version fallback until runnable support lands.
- [x] `driver-fabric/build.gradle.kts` no longer has a `26.2` smoke lane JSON
  branch or hard-coded latest/older unsupported lane ids.
- [~] Historical latest/older probe evidence remains in evidence files and
  older phase records, but active runtime code and current-facing docs must not
  present those ids as maintained product matrix entries.
- [~] Actual latest/current and representative older Fabric client runtime
  support remains open until runnable lane support and generated API/CLI
  gameplay verification land.
- [x] This phase adds no public gameplay action, generated route family, CLI
  gameplay catalog, Fabric execution binding, scenario shortcut, new compiled
  lane, public version-specific API, or new Minecraft support claim.

Verification:

- Red guard:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricCompatibilityMatrixTest.matrix does not catalog static unsupported latest or older lanes*'`
- Green focused test:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricCompatibilityMatrixTest*' --tests '*FabricDriverModuleTest*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-static-unsupported-version-lane-removal.md`.

## Phase 94: Fabric API Cache Resolution

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-94-fabric-api-cache-resolution-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-94-fabric-api-cache-resolution-plan.md`.
- [x] Fabric cache preparation can resolve a matching Fabric API Maven artifact
  for the requested Minecraft version from Fabric Maven metadata.
- [x] Fabric API is cached as a `FABRIC_MOD` artifact, not as a generic
  classpath library.
- [x] `CacheLaunchPlan.mods` exposes Fabric mod handles for launchers.
- [~] Actual latest/current and representative older Fabric client runtime
  support remains open until runnable lane support and generated API/CLI
  gameplay verification land.
- [x] This phase adds no public gameplay action, generated route family, CLI
  gameplay catalog, Fabric execution binding, scenario shortcut, new compiled
  lane, public version-specific API, or new Minecraft support claim.

Verification:

- Red guard:
  `mise exec -- gradle :daemon:test --tests '*CachePreparationServiceTest.fabric cache preparation resolves fabric api mod artifact from maven metadata*'`
- Green focused test:
  `mise exec -- gradle :daemon:test --tests '*CachePreparationServiceTest.fabric cache preparation resolves fabric api mod artifact from maven metadata*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-fabric-api-cache-resolution.md`.

## Phase 95: Launch Mod Materialization

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-95-launch-mod-materialization-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-95-launch-mod-materialization-plan.md`.
- [x] `ProcessClientRuntimeLauncher` copies every `CacheLaunchPlan.mods`
  handle into the instance `mods` directory before launching the client
  process.
- [x] The process launcher test proves cached Fabric mod artifacts are
  materialized into instance files before launch.
- [~] Actual latest/current and representative older Fabric client runtime
  support remains open until runnable lane support and generated API/CLI
  gameplay verification land.
- [x] This phase adds no public gameplay action, generated route family, CLI
  gameplay catalog, Fabric execution binding, scenario shortcut, new compiled
  lane, public version-specific API, or new Minecraft support claim.

Verification:

- Red guard:
  `mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.process client runtime launcher starts prepared command*'`
- Green focused test:
  `mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.process client runtime launcher starts prepared command*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-launch-mod-materialization.md`.

## Phase 96: Craftless Driver Mod Launch Artifact

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-96-craftless-driver-mod-launch-artifact-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-96-craftless-driver-mod-launch-artifact-plan.md`.
- [x] `WorkspaceClientRuntimeDriverFactory.prepare` can consume a configured
  Craftless Fabric driver mod path without depending on `driver-fabric`.
- [x] The configured driver mod is cached as a `FABRIC_MOD` artifact under the
  workspace and included in `CacheLaunchPlan.mods`.
- [x] Focused daemon tests prove the configured driver mod launch artifact
  appears in the prepared runtime manifest and launch plan.
- [x] Actual live in-client attach and generated API execution are covered by
  Phase 102; normal installed CLI distribution closure is covered by Phase 103.
- [x] This phase adds no public gameplay action, generated route family, CLI
  gameplay catalog, Fabric execution binding, scenario shortcut, new compiled
  lane, public version-specific API, or new Minecraft support claim.

Verification:

- Red guard:
  `mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.prepared runtime launch plan includes configured craftless fabric driver mod*'`
- Green focused test:
  `mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.prepared runtime launch plan includes configured craftless fabric driver mod*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-craftless-driver-mod-launch-artifact.md`.

## Phase 97: Packaged Driver Mod Distribution

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-97-packaged-driver-mod-distribution-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-97-packaged-driver-mod-distribution-plan.md`.
- [x] CLI `server start` forwards `CRAFTLESS_FABRIC_DRIVER_MOD` from its env
  map into the daemon provider.
- [x] `mise run package-cli` builds `:driver-fabric:remapJar` and stages
  `build/docker/craftless/mods/craftless-driver-fabric.jar`.
- [x] Docker runtime configuration sets `CRAFTLESS_FABRIC_DRIVER_MOD` to the
  staged driver mod path.
- [x] Actual live in-client attach and generated API execution are covered by
  Phase 102; normal installed CLI distribution closure is covered by Phase 103.
- [x] This phase adds no public gameplay action, generated route family, CLI
  gameplay catalog, Fabric execution binding, scenario shortcut, new compiled
  lane, public version-specific API, or new Minecraft support claim.

Verification:

- Red guard:
  `mise exec -- gradle :cli:test --tests '*CraftlessCliTest.server start forwards configured fabric driver mod environment*'`
- Red guard:
  `mise exec -- gradle :protocol:test --tests '*NamespacePolicyTest.package cli stages craftless fabric driver mod for docker runtime*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-packaged-driver-mod-distribution.md`.

## Phase 98: Driver Attach Proxy

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-98-driver-attach-proxy-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-98-driver-attach-proxy-plan.md`.
- [x] A prepared client session can be replaced by an attached driver session.
- [x] `POST /clients/{id}:attach` can attach a loopback HTTP driver endpoint.
- [x] Attached generated OpenAPI/actions/runtime metadata are projected from
  the attached driver, not the prepared placeholder.
- [x] `POST /clients/{id}:run` can invoke through the attached HTTP driver
  endpoint.
- [x] Fabric in-client endpoint startup is resolved by Phase 100 unit-level
  loopback endpoint evidence.
- [x] Actual live packaged in-client attach and generated API execution are
  covered by Phase 102.
- [x] This phase adds no public gameplay action, generated route family, CLI
  gameplay catalog, Fabric execution binding, scenario shortcut, new compiled
  lane, public version-specific API, or new Minecraft support claim.

Verification:

- Red guard:
  `mise exec -- gradle :daemon:test --tests '*ClientSessionServiceTest.attached driver replaces prepared session as openapi authority*'`
- Red guard:
  `mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.server attach proxies generated run calls to remote driver endpoint*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-driver-attach-proxy.md`.

## Phase 99: Launch Attach Environment

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-99-launch-attach-environment-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-99-launch-attach-environment-plan.md`.
- [x] Workspace runtime preparation passes `CRAFTLESS_CLIENT_ID` and
  `CRAFTLESS_DAEMON_URL` attach environment into launchers.
- [x] Process launches set those environment variables on the Minecraft client
  process.
- [x] Fabric in-client endpoint startup and self-attach are resolved by Phase
  100 unit-level transport evidence.
- [x] Actual live packaged in-client attach and generated API execution are
  covered by Phase 102.
- [x] This phase adds no public gameplay action, generated route family, CLI
  gameplay catalog, Fabric execution binding, scenario shortcut, new compiled
  lane, public version-specific API, or new Minecraft support claim.

Verification:

- Red guard:
  `mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.server prepares and launches workspace client runtime without injected driver factory*'`
- Red guard:
  `mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.process client runtime launcher starts prepared command*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-launch-attach-environment.md`.

## Phase 100: Fabric Driver Self-Attach

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-100-fabric-driver-self-attach-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-100-fabric-driver-self-attach-plan.md`.
- [x] Fabric driver parses `CRAFTLESS_CLIENT_ID` and
  `CRAFTLESS_DAEMON_URL` as optional lifecycle attach environment.
- [x] Fabric driver starts a loopback-only DriverSession HTTP endpoint.
- [x] Fabric driver posts its endpoint to `POST /clients/{id}:attach`.
- [x] Current-lane Fabric bootstrap starts self-attach from the real backend
  session after backend installation.
- [x] This phase adds no public gameplay action, generated route family, CLI
  gameplay catalog, Fabric execution binding, scenario shortcut, new compiled
  lane, public version-specific API, or new Minecraft support claim.

Verification:

- Red guard:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverSelfAttachTest.attach environment*'`
- Red guard:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverSelfAttachTest.loopback endpoint exposes driver session contract*'`
- Red guard:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverSelfAttachTest.self attach posts loopback endpoint to daemon*'`
- Red guard:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.current lane bootstrap starts self attach from backend session*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-fabric-driver-self-attach.md`.

## Phase 101: Packaged Driver Runtime Dependencies

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-101-packaged-driver-runtime-dependencies-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-101-packaged-driver-runtime-dependencies-plan.md`.
- [x] Fabric driver mod declares nested runtime dependencies for Craftless
  modules, Kotlin, kotlinx, Ktor, and config runtime jars.
- [x] `package-cli` verifies the staged driver mod contains nested jars,
  Kotlin stdlib, coroutines, and Ktor HTTP runtime jars.
- [x] `mise run package-cli` succeeds after the stricter staged-mod checks.
- [x] Actual live packaged in-client attach and generated API projection is
  now covered by Phase 102.
- [x] This phase adds no public gameplay action, generated route family, CLI
  gameplay catalog, Fabric execution binding, scenario shortcut, new compiled
  lane, public version-specific API, or new Minecraft support claim.

Verification:

- Red guard:
  `mise exec -- gradle :protocol:test --tests '*NamespacePolicyTest.fabric driver mod declares nested runtime dependencies*'`
- Package smoke:
  `mise run package-cli`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-packaged-driver-runtime-dependencies.md`.

## Phase 102: Packaged Live Attach And Cold-Cache Usability

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-102-packaged-live-attach-cold-cache-usability-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-102-packaged-live-attach-cold-cache-usability-plan.md`.
- [x] Packaged CLI API calls use a Ktor `HttpTimeout` with a 15-minute default
  and `CRAFTLESS_HTTP_REQUEST_TIMEOUT_MS` override for local supervisor calls.
- [x] Cache preparation downloads independent Minecraft asset objects with
  bounded parallelism while preserving existing checksum/resume behavior.
- [x] Packaged `craftless server start` plus packaged `craftless clients create`
  launches a real Fabric client with the staged driver mod.
- [x] The real Fabric driver self-attaches back to the packaged supervisor and
  emits `client.attached`.
- [x] Packaged CLI can read generated `/clients/{id}/actions`,
  `/clients/{id}/resources`, and SSE event stream from that attached client.
- [x] This phase adds no public gameplay action, generated route family, CLI
  gameplay catalog, Fabric execution binding, scenario shortcut, new compiled
  lane, public version-specific API, or new Minecraft support claim.

Verification:

- Red/green guard:
  `mise exec -- gradle :daemon:test --tests 'com.minekube.craftless.daemon.CachePreparationServiceTest.cache preparation fetches independent asset objects concurrently'`
- Red/green guard:
  `mise exec -- gradle :cli:test --tests 'com.minekube.craftless.cli.CraftlessCliTest.client create uses configured api request timeout'`
- Package smoke:
  `mise run package-cli`
- Live packaged smoke:
  packaged `craftless server start --port 18081 --workspace /tmp/...` with
  `CRAFTLESS_FABRIC_DRIVER_MOD=build/docker/craftless/mods/craftless-driver-fabric.jar`,
  packaged `craftless clients create attach-smoke --version 1.21.6 --loader fabric --offline-name AttachSmoke`,
  `client.attached`, packaged `clients attach-smoke actions`, packaged
  `clients attach-smoke resources`, and `GET /clients/attach-smoke/events:stream`.
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-packaged-live-attach-cold-cache-usability.md`.

## Phase 103: Installed CLI Driver Mod Distribution

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-103-installed-cli-driver-mod-distribution-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-103-installed-cli-driver-mod-distribution-plan.md`.
- [x] Normal CLI tar/zip distributions include
  `mods/craftless-driver-fabric.jar` from the remapped Fabric driver mod.
- [x] `craftless server start` uses explicit
  `CRAFTLESS_FABRIC_DRIVER_MOD` when set and otherwise auto-discovers the
  distribution-local `mods/craftless-driver-fabric.jar`.
- [x] `package-cli` verifies both tar and zip distributions contain the
  packaged driver mod before refreshing the Docker context.
- [x] Docker staging still verifies Fabric metadata and nested runtime jars for
  the same packaged driver mod path.
- [x] This phase adds no public gameplay action, generated route family, CLI
  gameplay catalog, Fabric execution binding, scenario shortcut, compile-time
  daemon dependency on `driver-fabric`, public version-specific API, or new
  Minecraft support claim.

Verification:

- Red/green guard:
  `mise exec -- gradle :cli:test --tests '*CraftlessCliTest.server start uses packaged fabric driver mod when env is absent*'`
- Red/green guard:
  `mise exec -- gradle :protocol:test --tests '*NamespacePolicyTest.package cli stages craftless fabric driver mod for docker runtime*'`
- Package smoke:
  `mise run package-cli`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-installed-cli-driver-mod-distribution.md`.

## Phase 104: v0.1.1 Release Install Evidence

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-104-v011-release-install-evidence-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-104-v011-release-install-evidence-plan.md`.
- [x] README install and reusable GitHub Action examples target `v0.1.1`.
- [x] Distribution tests guard the `v0.1.1` README examples.
- [x] `v0.1.1` release publication, release assets, install-script smoke, and
  installed archive driver-mod evidence are recorded in
  `docs/superpowers/evidence/2026-06-28-v011-release-install-evidence.md`.
- [x] This phase adds no public gameplay action, generated route family, CLI
  gameplay catalog, Fabric execution binding, scenario shortcut, compile-time
  daemon dependency on `driver-fabric`, public version-specific API, or new
  Minecraft support claim.

Verification:

- Focused docs guard:
  `mise exec -- bun test playwright/src/distribution.test.ts`
- Release workflow:
  `gh run view 28316490956 --json status,conclusion,headSha,url`
- Release assets:
  `gh release view v0.1.1 --json tagName,url,assets,targetCommitish,publishedAt`
- Install smoke:
  `CRAFTLESS_VERSION=v0.1.1 CRAFTLESS_INSTALL_DIR=... CRAFTLESS_HOME=... ./install.sh`
- Install evidence:
  `docs/superpowers/evidence/2026-06-28-v011-release-install-evidence.md`

## Phase 105: Active Unsupported Lane Fixture Cleanup

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-105-active-unsupported-lane-fixture-cleanup-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-105-active-unsupported-lane-fixture-cleanup-plan.md`.
- [x] Active smoke fixtures no longer use historical static unsupported lane
  ids such as `latest-release-26-2` or `older-release-1-20-6`.
- [x] The active unsupported runtime-lane smoke fixture uses generic
  `fabric-unsupported-26-2` with `unsupported-version`.
- [x] This phase adds no public gameplay action, generated route family, CLI
  gameplay catalog, Fabric execution binding, scenario shortcut, compile-time
  daemon dependency on `driver-fabric`, public version-specific API, runnable
  latest/older lane, or new Minecraft support claim.

Verification:

- Red/green guard:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.active smoke fixtures do not keep static latest unsupported lane ids*'`
- Affected smoke fixture:
  `mise exec -- gradle :testkit:test --tests '*LocalMinecraftServerSmokeTest.local server smoke records unsupported runtime lane without provisioning server*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-active-unsupported-lane-fixture-cleanup.md`.

## Phase 106: Explicit Unused And Dead-Code Gates

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-106-explicit-unused-dead-code-gates-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-106-explicit-unused-dead-code-gates-plan.md`.
- [x] Detekt config explicitly includes unused and dead-code rules:
  `UnusedImport`, `UnusedParameter`, `UnusedPrivateClass`,
  `UnusedPrivateFunction`, `UnusedPrivateProperty`, `UnusedVariable`,
  `UnreachableCatchBlock`, `UnreachableCode`, and `UnusedUnaryOperator`.
- [x] `mise run unused-check` runs Detekt explicitly.
- [x] `mise run ci` includes `mise run unused-check`.
- [x] This phase adds no new dependency, public gameplay action, generated
  route family, CLI gameplay catalog, Fabric execution binding, scenario
  shortcut, public version-specific API, runnable latest/older lane, or new
  Minecraft support claim.

Verification:

- Red/green guard:
  `mise exec -- gradle :protocol:test --tests '*NamespacePolicyTest.kotlin quality gates include explicit unused and dead code checks*'`
- Explicit unused/dead-code gate:
  `mise run unused-check`
- Full local CI:
  `mise run ci`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-explicit-unused-dead-code-gates.md`.

## Phase 107: Version-Aware Driver Mod Selection

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-107-version-aware-driver-mod-selection-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-107-version-aware-driver-mod-selection-plan.md`.
- [x] `ClientRuntimeDriverModProvider` receives a
  `ClientRuntimeDriverModRequest` containing loader, requested Minecraft
  version, and resolved/requested loader version.
- [x] `WorkspaceClientRuntimeDriverFactory.prepare` builds the driver-mod
  request after cache preparation so Fabric loader metadata can participate in
  future lane selection.
- [x] The existing `CRAFTLESS_FABRIC_DRIVER_MOD` fallback remains the current
  single packaged-driver path for Fabric launches.
- [x] This phase adds no new compiled Fabric lane, public gameplay action,
  generated route family, CLI gameplay catalog, Fabric gameplay binding,
  scenario shortcut, public version-specific API, runnable latest/older lane,
  or new Minecraft support claim.

Verification:

- Red/green guard:
  `mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.prepared runtime asks driver mod provider for requested runtime lane*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-version-aware-driver-mod-selection.md`.

## Phase 108: Driver Mod Manifest Provider

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-108-driver-mod-manifest-provider-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-108-driver-mod-manifest-provider-plan.md`.
- [x] `ConfiguredClientRuntimeDriverModProvider` supports
  `CRAFTLESS_DRIVER_MOD_MANIFEST` with entries keyed by loader, Minecraft
  version, optional loader version, and local jar path.
- [x] Exact manifest entries win over the single
  `CRAFTLESS_FABRIC_DRIVER_MOD` fallback.
- [x] Manifest misses fall back to `CRAFTLESS_FABRIC_DRIVER_MOD` for current
  single-driver releases.
- [x] Relative manifest entry paths resolve relative to the manifest file
  directory.
- [x] This phase adds no new compiled Fabric lane, public gameplay action,
  generated route family, CLI gameplay catalog, Fabric gameplay binding,
  scenario shortcut, public version-specific API, runnable latest/older lane,
  or new Minecraft support claim.

Verification:

- Red/green guard:
  `mise exec -- gradle :daemon:test --tests '*ConfiguredClientRuntimeDriverModProviderTest*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-driver-mod-manifest-provider.md`.

## Phase 109: Packaged Driver Mod Manifest

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-109-packaged-driver-mod-manifest-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-109-packaged-driver-mod-manifest-plan.md`.
- [x] CLI distributions generate and include root-level `driver-mods.json`.
- [x] `craftless server start` auto-discovers packaged `driver-mods.json`
  before falling back to `mods/craftless-driver-fabric.jar`.
- [x] `mise run package-cli` checks tar and zip distributions for both
  `mods/craftless-driver-fabric.jar` and `driver-mods.json`.
- [x] The legacy single-jar fallback remains available when no packaged
  manifest exists.
- [x] This phase adds no new compiled Fabric lane, public gameplay action,
  generated route family, CLI gameplay catalog, Fabric gameplay binding,
  scenario shortcut, public version-specific API, runnable latest/older lane,
  or new Minecraft support claim.

Verification:

- Red/green CLI guard:
  `mise exec -- gradle :cli:test --tests '*CraftlessCliTest.server start uses packaged driver mod manifest when env is absent*'`
- Distribution guard:
  `mise exec -- bun test playwright/src/distribution.test.ts`
- Package smoke:
  `mise run package-cli`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-packaged-driver-mod-manifest.md`.

## Phase 110: Strict Fabric Runtime Lane Identity

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-110-strict-fabric-runtime-lane-identity-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-110-strict-fabric-runtime-lane-identity-plan.md`.
- [x] Supported Fabric compatibility lanes now require exact runtime identity:
  Minecraft game version, Fabric Loader version, Fabric API version, and
  mappings fingerprint.
- [x] Same-game-version runtime drift resolves to an unsupported lane with
  reason `unsupported-runtime-identity`.
- [x] Unknown Minecraft versions continue to resolve to `unsupported-version`.
- [x] Provider selection returns null for mismatched runtime identity.
- [x] This phase adds no new compiled Fabric lane, public gameplay action,
  generated route family, CLI gameplay catalog, Fabric gameplay binding,
  scenario shortcut, public version-specific API, runnable latest/older lane,
  or new Minecraft support claim.

Verification:

- Red/green guard:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricCompatibilityMatrixTest*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-strict-fabric-runtime-lane-identity.md`.

## Phase 111: Latest Version Alias Resolution

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-111-latest-version-alias-resolution-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-111-latest-version-alias-resolution-plan.md`.
- [x] `latest-release` resolves through Mojang `latest.release` before
  prepared cache handles and manifest paths are built.
- [x] `latest-snapshot` resolves through Mojang `latest.snapshot` before
  prepared cache handles and manifest paths are built.
- [x] Returned `CachePrepareResult.minecraftVersion` uses the concrete
  resolved version id instead of preserving the alias as the runtime id.
- [x] Fabric cache preparation uses the resolved concrete version for Fabric
  loader metadata and profile URLs.
- [x] This phase adds no new compiled Fabric lane, public gameplay action,
  generated route family, CLI gameplay catalog, Fabric gameplay binding,
  scenario shortcut, public version-specific API, runnable latest/older lane,
  or new Minecraft support claim.

Verification:

- Red/green guard:
  `mise exec -- gradle :daemon:test --tests '*CachePreparationServiceTest.*latest*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-latest-version-alias-resolution.md`.

## Phase 112: Resolved Driver Mod Lane Request

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-112-resolved-driver-mod-lane-request-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-112-resolved-driver-mod-lane-request-plan.md`.
- [x] Prepared client runtime driver-mod selection uses the concrete
  `CachePrepareResult.minecraftVersion` after alias resolution.
- [x] Prepared client runtime driver-mod selection still passes the resolved
  Fabric Loader version.
- [x] Exact version requests keep the same provider request behavior.
- [x] This phase adds no new compiled Fabric lane, public gameplay action,
  generated route family, CLI gameplay catalog, Fabric gameplay binding,
  scenario shortcut, public version-specific API, runnable latest/older lane,
  or new Minecraft support claim.

Verification:

- Red/green guard:
  `mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.prepared runtime asks driver mod provider for *runtime lane*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-resolved-driver-mod-lane-request.md`.

## Phase 113: Shared Version Index Resolution

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-113-shared-version-index-resolution-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-113-shared-version-index-resolution-plan.md`.
- [x] Cache preparation and Java runtime resolution share daemon
  version-index helpers.
- [x] Java runtime resolution accepts `latest-release` through the supervisor
  API/CLI path and derives requirements from the resolved concrete Mojang
  version.
- [x] Existing cache-preparation alias behavior remains covered.
- [x] This phase adds no new compiled Fabric lane, public gameplay action,
  generated route family, CLI gameplay catalog, Fabric gameplay binding,
  scenario shortcut, public version-specific API, runnable latest/older lane,
  or new Minecraft support claim.

Verification:

- Red/green Java runtime guard:
  `mise exec -- gradle :cli:test --tests '*CraftlessCliTest.runtimes java resolve resolves latest release alias through supervisor api*'`
- Cache alias regression guard:
  `mise exec -- gradle :daemon:test --tests '*CachePreparationServiceTest.*latest*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-shared-version-index-resolution.md`.

## Phase 114: Active Docs Latest Alias

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-114-active-docs-latest-alias-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-114-active-docs-latest-alias-plan.md`.
- [x] README create-client and cache-prepare examples use `latest-release`.
- [x] Client file-management docs describe `latest-release` and
  `latest-snapshot` alias handling.
- [x] Roadmap describes concrete latest ids as historical probe evidence
  rather than the active current-version contract.
- [x] Bun docs guard rejects active-doc drift back to concrete current-latest
  wording.
- [x] This phase adds no new compiled Fabric lane, public gameplay action,
  generated route family, CLI gameplay catalog, Fabric gameplay binding,
  scenario shortcut, public version-specific API, runnable latest/older lane,
  or new Minecraft support claim.

Verification:

- Red/green docs guard:
  `mise exec -- bun test playwright/src/distribution.test.ts --test-name-pattern "active docs prefer latest aliases over concrete latest ids"`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-active-docs-latest-alias.md`.

## Phase 115: Local Server Latest Alias

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-115-local-server-latest-alias-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-115-local-server-latest-alias-plan.md`.
- [x] Local server smoke provisioning accepts `latest-release` and
  `latest-snapshot` through shared Mojang version-index alias resolution.
- [x] Server jars provisioned through an alias are written under the resolved
  concrete Minecraft version, for example `minecraft-server-26.2.jar`.
- [x] Daemon cache preparation and Java runtime resolution still use the same
  shared alias/manifest helper.
- [x] Existing exact-version server provisioning remains covered.
- [x] This phase adds no new compiled Fabric lane, public gameplay action,
  generated route family, CLI gameplay catalog, Fabric gameplay binding,
  scenario shortcut, public version-specific API, runnable latest/older lane,
  or new Minecraft support claim.

Verification:

- Red/green server provisioning guard:
  `mise exec -- gradle :testkit:test --tests '*MinecraftServerJarProvisionerTest.fixture provisions latest release server jar under resolved version*'`
- Shared alias regression guards:
  `mise exec -- gradle :daemon:test --tests '*CachePreparationServiceTest.*latest*'`
  and
  `mise exec -- gradle :cli:test --tests '*CraftlessCliTest.runtimes java resolve resolves latest release alias through supervisor api*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-local-server-latest-alias.md`.

## Phase 116: Local Smoke Default Latest Alias

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-116-local-smoke-default-latest-alias-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-116-local-smoke-default-latest-alias-plan.md`.
- [x] `LocalMinecraftServerSmokeConfig` defaults to `latest-release`.
- [x] `LocalMinecraftServerSmokeConfig.fromEnvironment()` falls back to
  `latest-release` when `CRAFTLESS_SMOKE_MINECRAFT_VERSION` is unset.
- [x] Explicit smoke version overrides such as `1.21.6` remain preserved.
- [x] This phase adds no new compiled Fabric lane, public gameplay action,
  generated route family, CLI gameplay catalog, Fabric gameplay binding,
  scenario shortcut, public version-specific API, runnable latest/older lane,
  or new Minecraft support claim.

Verification:

- Red/green default guard:
  `mise exec -- gradle :testkit:test --tests '*LocalMinecraftServerSmokeTest.local server smoke is disabled by default*'`
- Focused testkit regression:
  `mise exec -- gradle :testkit:test --tests '*LocalMinecraftServerSmokeTest.*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-local-smoke-default-latest-alias.md`.

## Phase 117: Live Event Action Fallback Removal

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-117-live-event-action-fallback-removal-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-117-live-event-action-fallback-removal-plan.md`.
- [x] Daemon live event normalization no longer maps raw `chat` or `movement`
  session event types to concrete gameplay action ids.
- [x] Action invocation events remain typed through explicit `operationId`.
- [x] SSE and JSON-RPC action event regressions still pass.
- [x] This phase adds no new public gameplay action, generated route family,
  CLI gameplay catalog, Fabric gameplay binding, scenario shortcut, public
  version-specific API, runnable latest/older lane, or new Minecraft support
  claim.

Verification:

- Red/green source guard:
  `mise exec -- gradle :protocol:test --tests '*NamespacePolicyTest.daemon live event normalization does not synthesize gameplay action ids*'`
- SSE/JSON-RPC regression guard:
  `mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.server streams filtered live client events as sse*' --tests '*LocalSessionApiServerTest.server invokes actions through json rpc with correlation ids*' --tests '*LocalSessionApiServerTest.server persists json rpc subscriptions as sse filters*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-live-event-action-fallback-removal.md`.

## Phase 118: Action Result Event Type Removal

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-118-action-result-event-type-removal-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-118-action-result-event-type-removal-plan.md`.
- [x] `DriverActionResult` no longer carries static `DriverEventType`
  metadata.
- [x] Accepted action session events use the invoked `operationId` as their
  event type.
- [x] Backend driver sessions no longer synthesize accepted driver events from
  action result metadata.
- [x] Failed or rejected action results still record `ERROR` events where the
  driver session has a message.
- [x] This phase adds no new public gameplay action, generated route family,
  CLI gameplay catalog, Fabric gameplay binding, scenario shortcut, public
  version-specific API, runnable latest/older lane, replacement action-event
  enum, or new Minecraft support claim.

Verification:

- Red/green driver API contract guard:
  `mise exec -- gradle :driver-api:test --tests '*DriverSessionContractTest.driver action results do not carry static event type metadata*'`
- Focused runtime/daemon/Fabric regressions:
  `mise exec -- gradle :driver-runtime:test --tests '*BackendDriverSessionTest.*'`
  `mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.server streams generic graph invocation results without legacy event metadata*' --tests '*LocalSessionApiServerTest.server dispatches graph operations through registered operation adapters*' --tests '*LocalSessionApiServerTest.server streams filtered live client events as sse*'`
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverSelfAttachTest.*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-action-result-event-type-removal.md`.

## Phase 119: Driver Event Type Gameplay Removal

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-119-driver-event-type-gameplay-removal-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-119-driver-event-type-gameplay-removal-plan.md`.
- [x] `DriverEventType` exposes lifecycle/system values only:
  `CLIENT_CREATED`, `CLIENT_CONNECTED`, `CLIENT_STOPPED`, and `ERROR`.
- [x] Accepted fake driver gameplay invocations return accepted action results
  without adding raw chat or movement driver events.
- [x] Fabric self-attach loopback accepted action invocation leaves raw driver
  events lifecycle-only.
- [x] Error and lifecycle event regressions remain covered.
- [x] This phase adds no new public gameplay action, generated route family,
  CLI gameplay catalog, Fabric gameplay binding, scenario shortcut, public
  version-specific API, runnable latest/older lane, replacement gameplay event
  enum, or new Minecraft support claim.

Verification:

- Red/green driver API contract guard:
  `mise exec -- gradle :driver-api:test --tests '*DriverSessionContractTest.driver event types do not expose static gameplay categories*'`
- Focused fake/daemon/Fabric regressions:
  `mise exec -- gradle :testkit:test --tests '*FakeDriverSessionTest.*'`
  `mise exec -- gradle :daemon:test --tests '*ClientSessionServiceTest.created clients expose a driver session contract*'`
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverSelfAttachTest.loopback endpoint exposes driver session contract*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-driver-event-type-gameplay-removal.md`.

## Phase 120: Invoke Fallback Naming Removal

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-120-invoke-fallback-naming-removal-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-120-invoke-fallback-naming-removal-plan.md`.
- [x] Active Kotlin source/tests, AGENTS/checklist text, and active specs/plans
  avoid stale old-invoke wording for the graph-owned generic invocation path.
- [x] Test-only fallback counters use `fallbackInvokeCount`.
- [x] Phase 79 docs and verification commands use generic invoke
  compatibility wording.
- [x] This phase adds no new public gameplay action, generated route family,
  CLI gameplay catalog, Fabric gameplay binding, scenario shortcut, public
  version-specific API, runnable latest/older lane, replacement gameplay event
  enum, runtime dispatch behavior change, or new Minecraft support claim.

Verification:

- Red/green protocol policy guard:
  `mise exec -- gradle :protocol:test --tests '*NamespacePolicyTest.active code and governance avoid stale invoke wording*'`
- Focused daemon/Fabric regressions:
  `mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.server dispatches graph operations through registered operation adapters*' --tests '*LocalSessionApiServerTest.server rejects graph operation availability and schema before operation adapters*'`
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric compatibility invoke dispatches unavailable operations from runtime graph*' --tests '*FabricDriverModuleTest.fabric compatibility invoke adapters come from private binding map*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-invoke-fallback-naming-removal.md`.

## Phase 121: Metadata Fallback Naming Removal

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-121-metadata-fallback-naming-removal-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-121-metadata-fallback-naming-removal-plan.md`.
- [x] Daemon Java runtime fallback internals describe versions before Mojang
  Java runtime metadata instead of using broad old-path naming.
- [x] Daemon native library fallback internals describe classifier metadata
  instead of using broad old-path naming.
- [x] The required Mojang launch literal `user_type=legacy` remains unchanged.
- [x] This phase adds no new public gameplay action, generated route family,
  CLI gameplay catalog, Fabric gameplay binding, scenario shortcut, public
  version-specific API, runnable latest/older lane, replacement gameplay event
  enum, runtime behavior change, or new Minecraft support claim.

Verification:

- Red/green daemon source guard and resolver regression:
  `mise exec -- gradle :daemon:test --tests '*JavaRuntimeRequirementResolverTest.*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-metadata-fallback-naming-removal.md`.

## Phase 122: Removed Survival Namespace Wording

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-122-removed-survival-namespace-wording-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-122-removed-survival-namespace-wording-plan.md`.
- [x] Active protocol validation messages describe `task.survival.*` as a
  removed survival scenario namespace, not as an old API generation.
- [x] Active protocol tests guard against reintroducing stale old-path survival
  wording in protocol source/tests.
- [x] `task.survival.*` remains rejected; this phase adds no public gameplay
  action, generated route family, CLI gameplay catalog, Fabric gameplay
  binding, scenario shortcut, public version-specific API, runnable
  latest/older lane, replacement gameplay event enum, runtime behavior change,
  or new Minecraft support claim.

Verification:

- Red/green protocol wording guard and navigation model regression:
  `mise exec -- gradle :protocol:test --tests '*NavigationModelsTest.*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-removed-survival-namespace-wording.md`.

## Phase 123: Create Client Loader Version

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-123-create-client-loader-version-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-123-create-client-loader-version-plan.md`.
- [x] `CreateClientRequest` exposes optional `loaderVersion` and validates it
  as a cache-safe segment.
- [x] The stable supervisor OpenAPI create-client request schema exposes
  nullable `loaderVersion`.
- [x] Client runtime preparation passes requested `loaderVersion` into cache
  preparation, so the resolved driver-mod provider lane can include the
  selected loader version.
- [x] Alias requests still ask the driver-mod provider for the prepared
  concrete Minecraft version and resolved loader version.
- [x] This phase adds no compiled Fabric lane, public gameplay action,
  generated route family, CLI gameplay catalog, Fabric gameplay binding,
  scenario shortcut, public version-specific API, runnable latest/older lane,
  runtime behavior support claim, or new Minecraft support claim.

Verification:

- Red/green protocol DTO and OpenAPI schema tests:
  `mise exec -- gradle :protocol:test --tests '*ClientModelsTest.*' --tests '*OpenApiGenerationTest.*'`
- Red/green daemon runtime-lane pass-through test:
  `mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.*loader version*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-create-client-loader-version.md`.

## Phase 124: CLI Create Client Loader Version

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-124-cli-create-client-loader-version-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-124-cli-create-client-loader-version-plan.md`.
- [x] `craftless clients create` accepts `--loader-version <version>`.
- [x] The CLI create-client request includes `loaderVersion` only when the
  user provides the flag.
- [x] `clients create` usage mentions `[--loader-version <version>]`.
- [x] This phase adds no compiled Fabric lane, public gameplay action,
  generated route family, CLI gameplay catalog, Fabric gameplay binding,
  scenario shortcut, public version-specific API, runnable latest/older lane,
  runtime behavior support claim, or new Minecraft support claim.

Verification:

- Red/green CLI loader-version request and usage tests:
  `mise exec -- gradle :cli:test --tests '*CraftlessCliTest.*loader version*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-cli-create-client-loader-version.md`.

## Phase 125: Driver Mod Manifest Miss

- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-125-driver-mod-manifest-miss-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-125-driver-mod-manifest-miss-plan.md`.
- [x] `CRAFTLESS_DRIVER_MOD_MANIFEST` is authoritative for Fabric driver-mod
  lane selection when configured.
- [x] A configured manifest miss for a prepared Fabric runtime lane fails
  client creation instead of falling back to a single potentially incompatible
  Fabric driver jar.
- [x] `CRAFTLESS_FABRIC_DRIVER_MOD` remains a fallback only when no manifest is
  configured.
- [x] Packaged CLI server-start coverage verifies a manifest miss returns HTTP
  400 and does not copy the fallback driver jar into the workspace mod cache.
- [x] This phase adds no compiled Fabric lane, public gameplay action,
  generated route family, CLI gameplay catalog, Fabric gameplay binding,
  scenario shortcut, public version-specific API, runnable latest/older lane,
  runtime behavior support claim, or new Minecraft support claim.

Verification:

- Red/green daemon provider and manifest-selection tests:
  `mise exec -- gradle :daemon:test --tests '*ConfiguredClientRuntimeDriverModProviderTest.*' --tests '*LocalSessionApiServerTest.*driver mod manifest*'`
- Red/green packaged CLI manifest-miss tests:
  `mise exec -- gradle :cli:test --tests '*CraftlessCliTest.*driver mod manifest*'`
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-driver-mod-manifest-miss.md`.

## Phase 139: Packaged Older Fabric Lane Selection Smoke

- [x] The phase index and checklist record Phase 139 as selection smoke only,
  not older runtime support completion.
- [x] A daemon create-client smoke uses a packaged-style two-entry
  `driver-mods.json` containing current `1.21.6` and representative older
  `1.20.6` Fabric lanes.
- [x] The `1.20.6` create-client request prepares the older Fabric runtime
  metadata with Fabric Loader `0.19.3`, Fabric API `0.100.8+1.20.6`, and Java
  major version 21.
- [x] The prepared launch plan stages
  `mods/fabric-1.20.6/craftless-driver-fabric.jar` content and does not stage
  the current lane driver jar content.
- [x] This phase adds no public gameplay API, static gameplay catalog,
  version-specific public route family, survival shortcut, real process launch,
  attach proof, generated OpenAPI proof, gameplay proof, or older support
  completion claim.

Verification:

- Red test:
  `mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.prepared runtime selects packaged older fabric lane from manifest'`
  failed at `:daemon:compileTestKotlin` because
  `preparedRuntimeMetadataFetcherWithOlderLane` did not exist yet.
- Green test:
  `mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.prepared runtime selects packaged older fabric lane from manifest'`
  passed.
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-packaged-older-lane-selection-smoke.md`.

## Phase 140: Parameterized Fabric Smoke Client Command

- [x] The phase index and checklist record that older/future lane smoke
  evidence is invalid if the inner client command silently launches the
  default current lane.
- [x] The default `fabricClientSmoke` action command preserves the active
  `craftless.fabric.*` lane properties when launching
  `:driver-fabric:runClient`.
- [x] The propagated properties cover Minecraft version, Yarn mappings, Fabric
  Loader, Fabric API, Java major version, lane id, provider id, artifact key,
  and mappings fingerprint.
- [x] Explicit `CRAFTLESS_SMOKE_ACTION_COMMAND_JSON` overrides remain supported.
- [x] This phase adds no public gameplay API, static gameplay catalog,
  version-specific public route family, survival shortcut, real older process
  launch proof, attach proof, generated OpenAPI proof, gameplay proof, or
  older support completion claim.

Verification:

- Red test:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric client smoke runClient command preserves parameterized lane properties'`
  failed after the corrected guard because the build script did not expose
  `fabricSmokeLaneGradleProperties`.
- Green focused tests:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric client smoke runClient command preserves parameterized lane properties' --tests '*FabricDriverModuleTest.fabric client smoke passes runtime lane evidence before launching client'`
  passed.
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-parameterized-fabric-smoke-client-command.md`.

## Phase 141: Representative Older Fabric Real-Client Smoke

- [x] The representative older Fabric lane ran as a real client against a real
  local Minecraft `1.20.6` server.
- [x] The smoke used Fabric Loader `0.19.3`, Fabric API `0.100.8+1.20.6`,
  Java major version 21, lane/provider id `fabric-1-20-6-lane`, and mappings
  fingerprint `craftless-fabric-bindings-1-20-6`.
- [x] Runtime metadata, generated OpenAPI, generated actions, generated
  resources, SSE events, JSON event artifacts, generated action results, and
  server join/chat/disconnect evidence were written under
  `/tmp/craftless-fabric-smoke-older-lane/artifacts/`.
- [x] The smoke invoked generated actions for chat, move, screen query, world
  time query, player query, entity query, inventory query/equip, look,
  block break, and block interact.
- [x] This phase adds no public gameplay API, static gameplay catalog,
  version-specific public route family, survival shortcut, installed packaged
  CLI older-lane proof, or final honest survival completion claim.
- [x] This phase remains diagnostic because the smoke provisioned an iron
  sword. Final survival completion still requires honest gameplay without
  server-provisioned inventory or hard-coded scenario shortcuts.

Verification:

- Real smoke:
  `CRAFTLESS_FABRIC_CLIENT_SMOKE=1 CRAFTLESS_SMOKE_MINECRAFT_VERSION=1.20.6 CRAFTLESS_LOCAL_SERVER_SMOKE_ROOT=/tmp/craftless-fabric-smoke-older-lane CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS=420000 mise exec -- gradle :driver-fabric:fabricClientSmoke ...`
  passed with `BUILD SUCCESSFUL in 1m 28s`.
- Artifact root:
  `/tmp/craftless-fabric-smoke-older-lane/artifacts/`.
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-representative-older-fabric-real-client-smoke.md`.

## Phase 142: Installed Packaged Older Fabric Live Attach

- [x] `mise run package-cli` passed and rebuilt the packaged CLI distribution
  plus Docker staging directory.
- [x] The packaged binary started a supervisor at `http://127.0.0.1:18082`
  with workspace `/tmp/craftless-packaged-older-live-attach/workspace`.
- [x] The packaged CLI created client `older-cli` with Minecraft `1.20.6`,
  loader `FABRIC`, Fabric Loader `0.19.3`, and offline profile `OlderCli`.
- [x] The packaged client staged both `craftless-driver-fabric
  0.1.0-SNAPSHOT` for Minecraft `1.20.6` and Fabric API `0.100.8+1.20.6`.
- [x] SSE events showed `client.created` followed by `client.attached`.
- [x] The attached generated per-client OpenAPI reported
  `x-craftless-driver=craftless-driver-fabric`,
  `x-craftless-minecraft-version=1.20.6`,
  `x-craftless-loader-version=0.19.3`, mappings fingerprint
  `craftless-fabric-bindings-1-20-6`, and runtime graph fingerprint
  `graph:90a3b5c0f713c767`.
- [x] The attached generated OpenAPI exposed 22 generated actions, 14
  generated resources, and 22 generated action alias paths through the packaged
  CLI/API surface.
- [x] The client stopped with state `STOPPED`, the supervisor was stopped, and
  no managed Craftless/older-lane processes remained.
- [x] This phase adds no public gameplay API, static gameplay catalog,
  version-specific public route family, survival shortcut, or final honest
  survival completion claim.

Verification:

- Package build:
  `mise run package-cli` passed.
- Packaged live attach artifact root:
  `/tmp/craftless-packaged-older-live-attach/artifacts/`.
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-installed-packaged-older-fabric-live-attach.md`.

## Phase 143: Installed Latest Release Alias Compatibility Probe

- [x] The live Mojang manifest was captured and reported latest release `26.2`
  and latest snapshot `26.3-snapshot-1`.
- [x] `mise run package-cli` passed before the installed probe.
- [x] The packaged binary started a supervisor at `http://127.0.0.1:18083`
  with workspace `/tmp/craftless-packaged-latest-release-probe/workspace`.
- [x] The packaged CLI request for `clients create latest-cli --version
  latest-release --loader fabric` resolved the moving alias to Minecraft
  `26.2`.
- [x] Prepared runtime evidence shows Java major version 25,
  `java-runtime-epsilon`, asset index `32`, Fabric Loader `0.19.3`, and Fabric
  API `0.153.0+26.2`.
- [x] The packaged driver manifest contains provider-backed Craftless driver
  entries for `1.21.6` and `1.20.6`, but no `26.2` entry.
- [x] The packaged create request failed honestly with:
  `driver mod manifest has no Fabric entry for 26.2 0.19.3
  fabricApiVersion=0.153.0+26.2 javaMajorVersion=25`.
- [x] The supervisor was stopped and no managed Craftless/latest-release probe
  processes remained.
- [x] This phase adds no public gameplay API, static gameplay catalog,
  version-specific public route family, survival shortcut, or final
  latest/current support claim.

Verification:

- Package build:
  `mise run package-cli` passed.
- Packaged latest-release probe artifact root:
  `/tmp/craftless-packaged-latest-release-probe/artifacts/`.
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-installed-latest-release-alias-compatibility-probe.md`.

## Phase 144: Latest Driver Lane Preflight

- [x] `WorkspaceClientRuntimeDriverFactory.prepare` resolves the requested
  Minecraft version, preferred Fabric Loader version, Fabric API artifact
  version, and Java major version before full cache preparation.
- [x] The resolved driver-mod manifest lookup runs before client jars, asset
  objects, Java runtime files, Fabric libraries, or Fabric API jars are fetched.
- [x] A missing packaged `latest-release` lane for resolved Minecraft `26.2`,
  Fabric Loader `0.19.3`, Fabric API `0.153.0+26.2`, and Java major version
  25 fails with a concrete HTTP 400 error.
- [x] The regression test asserts no runtime launcher invocation, no binary
  fetches, and no asset-object cache directory for the unsupported latest lane.
- [x] Root and nested module `AGENTS.md` files state the same version-agnostic
  rule: shared resolver/graph/runtime behavior by default, per-version code
  only where Minecraft, Fabric API, mappings, or bytecode signatures actually
  diverge.
- [x] This phase adds no public gameplay API, static gameplay catalog,
  version-specific public route family, survival shortcut, or final
  latest/current support claim.

Verification:

- Focused daemon regression coverage:
  `mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.client creation rejects missing latest fabric driver lane before binary downloads' --tests '*LocalSessionApiServerTest.prepared runtime selects packaged older fabric lane from manifest' --tests '*ConfiguredClientRuntimeDriverModProviderTest*'`.
- Whitespace verification:
  `git diff --check`.
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-latest-driver-lane-preflight.md`.

## Phase 145: Latest Official Mapping Lane Probe

- [x] `driver-fabric/build.gradle.kts` has an explicit
  `craftless.fabric.mappingMode` lane property.
- [x] The default Yarn/remap lane remains unchanged for the verified current
  and older packaged paths.
- [x] Official mode removes the Yarn `mappings` dependency instead of passing
  `craftless.fabric.yarnMappings`.
- [x] `.mise.toml` exposes `fabric-lane-check-latest-official`.
- [x] The latest official probe runs Gradle through mise with
  `java@temurin-25.0.3+9.0.LTS` and `gradle@9.6.0`.
- [x] The latest official probe uses Minecraft `26.2`, Fabric Loader `0.19.3`,
  Fabric API `0.153.0+26.2`, Java major version 25, and
  `craftless-fabric-official-bindings-26-2`.
- [x] The latest official probe writes
  `build/reports/fabric-lane-check-latest-official.log` and
  `build/reports/fabric-lane-check-latest-official.status`.
- [x] Phase 145 probe evidence recorded:
  `status=source-compatibility-blocked` and
  `blockers=loom-remap-requires-mappings`.
- [x] This phase adds no public gameplay API, static gameplay catalog,
  version-specific public route family, survival shortcut, packaged 26.x
  driver manifest entry, or final latest/current support claim.

Verification:

- Focused guard tests:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric compiled lane build is parameterized for compatibility probes' --tests '*FabricDriverModuleTest.mise latest lane probe uses official mapping boundary not yarn remap lane*'`.
- Latest official lane probe:
  `mise run fabric-lane-check-latest-official`.
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-latest-official-mapping-lane-probe.md`.

## Phase 146: Latest Official Fabric Lane Boundary

- [x] `settings.gradle.kts` includes `driver-fabric-official`.
- [x] The root Gradle plugin block declares non-remap
  `net.fabricmc.fabric-loom`.
- [x] `driver-fabric-official` applies `net.fabricmc.fabric-loom`, not
  `net.fabricmc.fabric-loom-remap`.
- [x] `driver-fabric-official` uses Java 25, Minecraft `26.2`, Fabric Loader
  `0.19.3`, and Fabric API `0.153.0+26.2`.
- [x] `driver-fabric-official` has a minimal Fabric client entrypoint and no
  gameplay bindings, route catalogs, generated action descriptors, or scenario
  shortcuts.
- [x] `fabric-lane-check-latest-official` now compiles
  `:driver-fabric-official:compileKotlin`,
  `:driver-fabric-official:processResources`, and
  `:driver-fabric-official:jar` through Java 25.
- [x] Latest official lane probe evidence now records `status=compiled`.
- [x] This phase adds no packaged 26.x driver manifest entry, no public
  gameplay API, no static gameplay catalog, no version-specific public route
  family, no survival shortcut, and no final latest/current support claim.

Verification:

- Focused guard tests:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.latest official lane probe uses separate non remap module boundary' --tests '*FabricDriverModuleTest.mise latest lane probe uses official mapping boundary not yarn remap lane*'`.
- Latest official lane probe:
  `mise run fabric-lane-check-latest-official`.
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-latest-official-fabric-lane-boundary.md`.

## Phase 147: Shared Fabric Attach Boundary

- [x] Spec written:
  `docs/superpowers/specs/2026-06-28-147-shared-fabric-attach-boundary-design.md`.
- [x] Plan written:
  `docs/superpowers/plans/2026-06-28-147-shared-fabric-attach-boundary-plan.md`.
- [x] Root and nested module `AGENTS.md` files state the same
  version-agnostic rule: shared Fabric attach/runtime/discovery/projection
  infrastructure by default, per-version code only where Minecraft, Fabric
  API, mappings, loader, or bytecode signatures actually diverge.
- [x] `settings.gradle.kts` includes a neutral shared Fabric attach module.
- [x] `driver-fabric` consumes shared attach/loopback infrastructure without
  changing the verified Yarn/remap runtime behavior.
- [x] `driver-fabric-official` consumes shared attach/loopback infrastructure
  without depending on the Yarn/remap `driver-fabric` module.
- [x] The latest/current official entrypoint starts the shared self-attach
  path with a metadata-only runtime backend.
- [x] Existing self-attach tests cover the shared module.
- [x] `mise run fabric-lane-check-latest-official` still records
  `status=compiled`.
- [x] This phase adds no packaged 26.x driver manifest entry, no public
  gameplay API, no static gameplay catalog, no version-specific public route
  family, no survival shortcut, and no final latest/current support claim.

Verification:

- Red guard before implementation:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.official lane uses shared fabric attach boundary without depending on yarn remap lane'`
  failed before the shared module existed.
- Shared attach tests:
  `mise exec -- gradle :driver-fabric-attach:test`.
- Official lane compile boundary:
  `mise exec -- gradle :driver-fabric-official:compileKotlin :driver-fabric-official:processResources :driver-fabric-official:jar`.
- Current Yarn/remap lane regression:
  `mise exec -- gradle :driver-fabric:test`.
- Latest official lane probe:
  `mise run fabric-lane-check-latest-official`, with
  `build/reports/fabric-lane-check-latest-official.status` containing
  `status=compiled`.
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-shared-fabric-attach-boundary.md`.

## Phase 148: Official Fabric Runtime Dependency Packaging

- [x] Spec written:
  `docs/superpowers/specs/2026-06-28-148-official-fabric-runtime-dependency-packaging-design.md`.
- [x] Plan written:
  `docs/superpowers/plans/2026-06-28-148-official-fabric-runtime-dependency-packaging-plan.md`.
- [x] `driver-fabric-official` nests shared runtime dependencies required for
  metadata-only self-attach.
- [x] The official jar includes nested `protocol`, `driver-api`,
  `driver-runtime`, `driver-fabric-attach`, Ktor, Kotlin, coroutines, and
  serialization runtime jars.
- [x] The official jar does not include `driver-fabric`, `daemon`, or
  `bridge-hmc`.
- [x] `mise run fabric-lane-check-latest-official` still records
  `status=compiled`.
- [x] This phase adds no packaged 26.x driver manifest entry, no public
  gameplay API, no static gameplay catalog, no version-specific public route
  family, no survival shortcut, and no final latest/current support claim.

Verification:

- Red guard before implementation:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.official lane packages shared attach runtime dependencies without yarn remap gameplay lane'`
  failed before official nested includes existed.
- Green guard:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.official lane packages shared attach runtime dependencies without yarn remap gameplay lane'`.
- Official jar build and nested-jar inspection:
  `mise exec -- gradle :driver-fabric-official:compileKotlin :driver-fabric-official:processResources :driver-fabric-official:jar`
  and
  `jar tf driver-fabric-official/build/libs/driver-fabric-official-0.1.0-SNAPSHOT.jar | grep '^META-INF/jars/' | sort`.
- Latest official lane probe:
  `mise run fabric-lane-check-latest-official`, with
  `build/reports/fabric-lane-check-latest-official.status` containing
  `status=compiled`.
- Lint and whitespace:
  `mise exec -- gradle lint` and `git diff --check`.
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-official-fabric-runtime-dependency-packaging.md`.

## Phase 149: Official Fabric Launch Attach Probe

- [x] Spec written:
  `docs/superpowers/specs/2026-06-28-149-official-fabric-launch-attach-probe-design.md`.
- [x] Plan written:
  `docs/superpowers/plans/2026-06-28-149-official-fabric-launch-attach-probe-plan.md`.
- [x] `driver-fabric-official` has an opt-in `officialFabricAttachProbe`
  Gradle task.
- [x] The probe runner lives under `driver-fabric-official/src/test` and does
  not enter the production official mod jar.
- [x] The probe launches `:driver-fabric-official:runClient` through
  mise-managed Java 25 by default.
- [x] The probe injects `CRAFTLESS_CLIENT_ID` and `CRAFTLESS_DAEMON_URL` into
  the launched process.
- [x] Running the task without `CRAFTLESS_OFFICIAL_FABRIC_ATTACH_PROBE` skips
  safely.
- [x] Enabled probes fail when no attach is observed, and write timeout
  artifacts instead of producing false green evidence.
- [x] The enabled default probe launched the latest/current official Fabric
  client, observed `client.attached`, and wrote `probe-result.json` with
  `status=ATTACHED`.
- [x] The enabled default probe captured per-client OpenAPI before stopping the
  launched client; the artifact reported client `official-probe`, Minecraft
  `26.2`, loader `FABRIC`, loader version `0.19.3`, driver
  `craftless-driver-fabric-official`, zero actions, and one runtime resource.
- [x] The probe runner tolerates expected child output-stream closure during
  shutdown without printing a reader-thread stack trace or writing a false
  reader failure into `client-command.log`.
- [x] Root and driver-local `AGENTS.md` files preserve the version-agnostic
  rule for future agents: shared Fabric attach/runtime/discovery/projection is
  the default, and per-version code is allowed only for documented
  Minecraft/Fabric/mapping/loader/bytecode divergence behind lane boundaries.
- [x] This phase adds no packaged 26.x driver manifest entry, no public
  gameplay API, no static gameplay catalog, no version-specific public route
  family, no survival shortcut, and no final latest/current support claim.

Verification:

- Red guard before implementation:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.official lane has opt in launch attach probe task without packaging support claim'`
  failed before the task existed.
- Green guard:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.official lane has opt in launch attach probe task without packaging support claim'`.
- Probe runner compile:
  `mise exec -- gradle :driver-fabric-official:compileTestKotlin`.
- Default skip task:
  `mise exec -- gradle :driver-fabric-official:officialFabricAttachProbe`,
  with `probe-result.json` containing `SKIPPED`.
- Controlled enabled failure:
  `CRAFTLESS_OFFICIAL_FABRIC_ATTACH_PROBE=1`
  `CRAFTLESS_OFFICIAL_ATTACH_PROBE_TIMEOUT_MS=1000`
  `CRAFTLESS_OFFICIAL_ATTACH_PROBE_CLIENT_COMMAND_JSON='["sh","-c","echo client=$CRAFTLESS_CLIENT_ID daemon=$CRAFTLESS_DAEMON_URL"]'`
  `mise exec -- gradle :driver-fabric-official:officialFabricAttachProbe`
  exited nonzero, wrote `TIMEOUT`, and captured the injected client/daemon
  environment in `client-command.log`.
- Real enabled default probe:
  `CRAFTLESS_OFFICIAL_FABRIC_ATTACH_PROBE=1`
  `CRAFTLESS_OFFICIAL_ATTACH_PROBE_TIMEOUT_MS=120000`
  `mise exec -- gradle :driver-fabric-official:officialFabricAttachProbe`
  passed, wrote `ATTACHED`, captured `client.created` and `client.attached`,
  and wrote a per-client OpenAPI artifact for official Minecraft `26.2`.
- OpenAPI artifact summary:
  `jq '{client:."x-craftless"."x-craftless-client-id", minecraft:."x-craftless"."x-craftless-minecraft-version", loader:."x-craftless"."x-craftless-loader", loaderVersion:."x-craftless"."x-craftless-loader-version", driver:."x-craftless"."x-craftless-driver", actions:(."x-craftless-actions"|length), resources:(."x-craftless-resources"|length)}' driver-fabric-official/build/craftless-official-attach-probe/client-openapi.json`
  reported `official-probe`, `26.2`, `FABRIC`, `0.19.3`,
  `craftless-driver-fabric-official`, `actions=0`, and `resources=1`.
- Lint and whitespace:
  `mise exec -- gradle lint` and `git diff --check`.
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-official-fabric-launch-attach-probe.md`.

## Phase 150: Official Fabric Runtime Metadata Discovery

- [x] Spec written:
  `docs/superpowers/specs/2026-06-28-150-official-fabric-runtime-metadata-discovery-design.md`.
- [x] Plan written:
  `docs/superpowers/plans/2026-06-28-150-official-fabric-runtime-metadata-discovery-plan.md`.
- [x] `OfficialFabricDriverBackend` no longer embeds
  `mods:official-lane-probe`, `registries:unavailable`, or
  `server-features:unavailable`.
- [x] `OfficialFabricDriverBackend` delegates runtime metadata to a provider.
  Phase 151 supersedes the original official-only provider with shared
  `driver-fabric-discovery` metadata primitives.
- [x] Snapshot provider tests prove installed-mod fingerprinting is
  deterministic, order-independent, and changes when a mod version changes.
- [x] The production official metadata provider reads Fabric Loader mod
  containers and uses their ids/versions as runtime metadata input.
- [x] Root and driver-local `AGENTS.md` files keep future agents aligned on the
  stable version-agnostic rule; per-phase history now belongs in
  `docs/superpowers/phase-index.md` and the checklist.
- [x] The real enabled official attach probe still observes `client.attached`.
- [x] The generated per-client OpenAPI artifact reports installed mods as a
  hashed live fingerprint such as `mods:6d85fb9272c1d2f5`, not the old
  `mods:official-lane-probe` placeholder.
- [x] Registry and server-feature metadata remain explicit non-gameplay
  discovery gaps: `registries:not-discovered` and
  `server-features:not-connected`.
- [x] This phase adds no packaged 26.x driver manifest entry, no public
  gameplay API, no static gameplay catalog, no version-specific public route
  family, no survival shortcut, and no final latest/current support claim.

Verification:

- Red guard before implementation:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.official lane has opt in launch attach probe task without packaging support claim'`
  failed while the official backend still embedded placeholder fingerprints.
- Provider red test before implementation:
  `mise exec -- gradle :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*'`
  failed before the provider types existed.
- Green guard:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.official lane has opt in launch attach probe task without packaging support claim'`.
- Provider tests:
  `mise exec -- gradle :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*'`.
- Real enabled default probe:
  `CRAFTLESS_OFFICIAL_FABRIC_ATTACH_PROBE=1`
  `CRAFTLESS_OFFICIAL_ATTACH_PROBE_TIMEOUT_MS=120000`
  `mise exec -- gradle :driver-fabric-official:officialFabricAttachProbe`
  passed, wrote `ATTACHED`, captured `client.created` and `client.attached`,
  and wrote a per-client OpenAPI artifact for official Minecraft `26.2`.
- OpenAPI artifact summary:
  `jq '{client:."x-craftless"."x-craftless-client-id", minecraft:."x-craftless"."x-craftless-minecraft-version", loader:."x-craftless"."x-craftless-loader", loaderVersion:."x-craftless"."x-craftless-loader-version", driver:."x-craftless"."x-craftless-driver", installedMods:."x-craftless"."x-craftless-installed-mods-fingerprint", registry:."x-craftless"."x-craftless-registry-fingerprint", serverFeatures:."x-craftless"."x-craftless-server-feature-fingerprint", actions:(."x-craftless-actions"|length), resources:(."x-craftless-resources"|length)}' driver-fabric-official/build/craftless-official-attach-probe/client-openapi.json`
  reported `official-probe`, `26.2`, `FABRIC`, `0.19.3`,
  `craftless-driver-fabric-official`, `mods:6d85fb9272c1d2f5`,
  `registries:not-discovered`, `server-features:not-connected`, `actions=0`,
  and `resources=1`.

## Phase 151: Shared Fabric Runtime Metadata Discovery

- [x] Spec written:
  `docs/superpowers/specs/2026-06-28-151-shared-fabric-runtime-metadata-discovery-design.md`.
- [x] Plan written:
  `docs/superpowers/plans/2026-06-28-151-shared-fabric-runtime-metadata-discovery-plan.md`.
- [x] New shared module `driver-fabric-discovery` is included in Gradle
  settings and has local `AGENTS.md` rules.
- [x] Both `driver-fabric` and `driver-fabric-official` depend on and include
  `driver-fabric-discovery`.
- [x] Shared tests prove snapshot metadata emission and deterministic,
  order-independent, change-sensitive fingerprinting.
- [x] The Yarn/remap Fabric lane no longer declares local
  `FabricRuntimeMetadataSnapshot`, `SnapshotFabricRuntimeMetadataProvider`, or
  Fabric Loader installed-mod helper copies.
- [x] The official lane no longer declares `OfficialFabricRuntimeMetadataProvider`
  or `OfficialFabricRuntimeMetadataSnapshot` copies.
- [x] The official backend uses shared `FabricRuntimeMetadataProvider`,
  `FabricLoaderRuntimeMetadataReader`, `FabricRuntimeMetadataSnapshot`, and
  `SnapshotFabricRuntimeMetadataProvider`.
- [x] The real enabled official attach probe still observes `client.attached`
  and reports a live `mods:` installed-mod fingerprint.
- [x] This phase adds no packaged 26.x driver manifest entry, no public
  gameplay API, no static gameplay catalog, no version-specific public route
  family, no survival shortcut, and no final latest/current support claim.

Verification:

- Red shared-boundary guard before implementation:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.official lane has opt in launch attach probe task without packaging support claim'`
  failed before `driver-fabric-discovery` existed.
- Shared provider red test before implementation:
  `mise exec -- gradle :driver-fabric-discovery:test` failed before
  `FabricRuntimeMetadataSnapshot`, `SnapshotFabricRuntimeMetadataProvider`, and
  `fabricRuntimeFingerprint` existed.
- Focused green tests:
  `mise exec -- gradle :driver-fabric-discovery:test :driver-fabric:test --tests '*FabricDriverModuleTest.official lane has opt in launch attach probe task without packaging support claim' :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*'`.
- Local CI:
  `mise run ci` completed successfully after this phase and the two local CI
  fixes it exposed.
- Real enabled official attach probe:
  `CRAFTLESS_OFFICIAL_FABRIC_ATTACH_PROBE=1`
  `CRAFTLESS_OFFICIAL_ATTACH_PROBE_TIMEOUT_MS=120000`
  `mise exec -- gradle :driver-fabric-official:officialFabricAttachProbe`.
  Observed `status=ATTACHED`, `installedMods=mods:6d85fb9272c1d2f5`,
  `actions=0`, and `resources=1`.
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-shared-fabric-runtime-metadata-discovery.md`.

## Phase 152: Shared Fabric Runtime Resource Projection

- [x] Spec written:
  `docs/superpowers/specs/2026-06-28-152-shared-fabric-runtime-resource-projection-design.md`.
- [x] Plan written:
  `docs/superpowers/plans/2026-06-28-152-shared-fabric-runtime-resource-projection-plan.md`.
- [x] `driver-fabric-discovery` owns `fabricRuntimeResourceNode` for shared
  Fabric runtime metadata resource projection.
- [x] Shared tests prove the runtime resource includes installed-mods,
  registry, server-feature, permissions, and caller-supplied lane evidence.
- [x] The Yarn/remap Fabric metadata capability probe uses the shared runtime
  resource projection helper.
- [x] The official backend uses the shared runtime resource projection helper
  and no longer imports `RuntimeResourceNode` directly.
- [x] The real enabled official attach probe still observes `client.attached`
  and reports live `mods:` metadata with `actions=0` and `resources=1`.
- [x] This phase adds no packaged 26.x driver manifest entry, no public
  gameplay API, no static gameplay catalog, no version-specific public route
  family, no survival shortcut, and no final latest/current support claim.

Verification:

- Red shared projection test before implementation:
  `mise exec -- gradle :driver-fabric-discovery:test :driver-fabric:test --tests '*FabricDriverModuleTest.official lane has opt in launch attach probe task without packaging support claim'`
  failed before `fabricRuntimeResourceNode` existed.
- Focused green tests:
  `mise exec -- gradle :driver-fabric-discovery:test :driver-fabric:test --tests '*FabricDriverModuleTest.official lane has opt in launch attach probe task without packaging support claim' :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*'`.
- Real enabled official attach probe:
  `CRAFTLESS_OFFICIAL_FABRIC_ATTACH_PROBE=1`
  `CRAFTLESS_OFFICIAL_ATTACH_PROBE_TIMEOUT_MS=120000`
  `mise exec -- gradle :driver-fabric-official:officialFabricAttachProbe`.
  Observed `status=ATTACHED`,
  `installedMods=mods:6d85fb9272c1d2f5`,
  `runtimeFingerprint=graph:755b3b5233a65773`, `actions=0`, and
  `resources=1`.
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-shared-fabric-runtime-resource-projection.md`.

## Phase 153: Shared Fabric Runtime Graph Composition

- [x] Spec written:
  `docs/superpowers/specs/2026-06-28-153-shared-fabric-runtime-graph-composition-design.md`.
- [x] Plan written:
  `docs/superpowers/plans/2026-06-28-153-shared-fabric-runtime-graph-composition-plan.md`.
- [x] `driver-fabric-discovery` owns shared
  `FabricRuntimeGraphFragment` and `fabricRuntimeGraph`.
- [x] `driver-fabric-discovery` owns `fabricRuntimeMetadataGraph` for
  metadata-only lanes.
- [x] Shared tests prove runtime graph fragments compose resources,
  operations, graph fingerprints, and duplicate-node validation.
- [x] The Yarn/remap Fabric lane keeps lane-specific probes but delegates
  fragment merging through the shared graph composer.
- [x] The official backend uses the shared metadata graph helper and no longer
  imports `RuntimeCapabilityGraph` directly.
- [x] Root and driver-local `AGENTS.md` files keep the stable shared-graph and
  version-agnostic guardrails; per-phase graph-composition history now belongs
  in `docs/superpowers/phase-index.md` and the checklist.
- [x] This phase adds no packaged 26.x driver manifest entry, no public
  gameplay API, no static gameplay catalog, no version-specific public route
  family, no survival shortcut, and no final latest/current support claim.

Verification:

- Red shared graph-composition test before implementation:
  `mise exec -- gradle :driver-fabric-discovery:test :driver-fabric:test --tests '*FabricDriverModuleTest.official lane has opt in launch attach probe task without packaging support claim'`
  failed before `fabricRuntimeGraph` and `FabricRuntimeGraphFragment` existed.
- Focused green tests:
  `mise exec -- gradle :driver-fabric-discovery:test :driver-fabric:test --tests '*FabricDriverModuleTest.official lane has opt in launch attach probe task without packaging support claim' :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*'`.
- Real enabled official attach probe:
  `CRAFTLESS_OFFICIAL_FABRIC_ATTACH_PROBE=1`
  `CRAFTLESS_OFFICIAL_ATTACH_PROBE_TIMEOUT_MS=120000`
  `mise exec -- gradle :driver-fabric-official:officialFabricAttachProbe`.
  Observed `status=ATTACHED`, `client=official-probe`,
  `installedMods=mods:6d85fb9272c1d2f5`,
  `runtimeFingerprint=graph:755b3b5233a65773`, `actions=0`, and
  `resources=1`.
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-shared-fabric-runtime-graph-composition.md`.

## Phase 154: Shared Fabric Registry Graph Projection

- [x] Spec written:
  `docs/superpowers/specs/2026-06-28-154-shared-fabric-registry-graph-projection-design.md`.
- [x] Plan written:
  `docs/superpowers/plans/2026-06-28-154-shared-fabric-registry-graph-projection-plan.md`.
- [x] `driver-fabric-discovery` owns shared
  `fabricRegistryGraphFragment`.
- [x] Shared tests prove registry graph projection emits resource `registry`
  and the existing Craftless-owned registry handles.
- [x] Shared tests prove unavailable registry discovery is represented with
  reason `registry-not-discovered`.
- [x] The Yarn/remap Fabric lane keeps registry fingerprint production in the
  lane but delegates registry graph resource/handle projection to the shared
  helper.
- [x] The official backend composes runtime metadata plus registry graph
  fragments through shared graph composition and still imports no
  `RuntimeCapabilityGraph`.
- [x] Root and driver-local `AGENTS.md` files keep stable shared projection
  guardrails; per-phase registry graph history now belongs in
  `docs/superpowers/phase-index.md` and the checklist.
- [x] This phase adds no packaged 26.x driver manifest entry, no public
  gameplay API, no static gameplay catalog, no version-specific public route
  family, no survival shortcut, and no final latest/current support claim.

Verification:

- Red shared registry projection test before implementation:
  `mise exec -- gradle :driver-fabric-discovery:test :driver-fabric:test --tests '*FabricDriverModuleTest.official lane has opt in launch attach probe task without packaging support claim'`
  failed before `fabricRegistryGraphFragment` existed.
- Focused green tests:
  `mise exec -- gradle :driver-fabric-discovery:test :driver-fabric:test --tests '*FabricDriverModuleTest.official lane has opt in launch attach probe task without packaging support claim' :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*'`.
- Real enabled official attach probe:
  `CRAFTLESS_OFFICIAL_FABRIC_ATTACH_PROBE=1`
  `CRAFTLESS_OFFICIAL_ATTACH_PROBE_TIMEOUT_MS=120000`
  `mise exec -- gradle :driver-fabric-official:officialFabricAttachProbe`.
  Observed `status=ATTACHED`, `client=official-probe`,
  `installedMods=mods:6d85fb9272c1d2f5`,
  `runtimeFingerprint=graph:c21fe6a0355116f6`, `actions=0`,
  `resources=2`, `handles=6`, and registry availability
  `unavailable` with reason `registry-not-discovered`.
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-shared-fabric-registry-graph-projection.md`.

## Phase 155: Shared Fabric Event Graph Projection

- [x] Spec written:
  `docs/superpowers/specs/2026-06-28-155-shared-fabric-event-graph-projection-design.md`.
- [x] Plan written:
  `docs/superpowers/plans/2026-06-28-155-shared-fabric-event-graph-projection-plan.md`.
- [x] `driver-fabric-discovery` owns shared `fabricEventGraphFragment`.
- [x] Shared tests prove event graph projection emits resource `event` and
  Craftless-owned event ids.
- [x] Shared tests prove unavailable event-source discovery is represented
  with reason `event-source-not-discovered` and fallback evidence
  `events:not-discovered`.
- [x] The Yarn/remap Fabric lane keeps Fabric API callback and mixin source
  evidence in the lane but delegates event resource/event projection to the
  shared helper.
- [x] The official backend composes runtime metadata, registry, and event
  graph fragments through shared graph composition and still imports no
  `RuntimeCapabilityGraph`.
- [x] Root and driver-local `AGENTS.md` files keep stable shared projection
  guardrails; per-phase event graph history now belongs in
  `docs/superpowers/phase-index.md` and the checklist.
- [x] This phase adds no packaged 26.x driver manifest entry, no public
  gameplay API, no static gameplay catalog, no version-specific public route
  family, no survival shortcut, no official-lane SSE completion claim, and no
  final latest/current support claim.

Verification:

- Red shared event projection test before implementation:
  `mise exec -- gradle :driver-fabric-discovery:test :driver-fabric:test --tests '*FabricDriverModuleTest.official lane has opt in launch attach probe task without packaging support claim'`
  failed before `fabricEventGraphFragment` existed.
- Focused green tests:
  `mise exec -- gradle :driver-fabric-discovery:test :driver-fabric:test --tests '*FabricDriverModuleTest.official lane has opt in launch attach probe task without packaging support claim' :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*'`.
- Real enabled official attach probe:
  `CRAFTLESS_OFFICIAL_FABRIC_ATTACH_PROBE=1`
  `CRAFTLESS_OFFICIAL_ATTACH_PROBE_TIMEOUT_MS=120000`
  `mise exec -- gradle :driver-fabric-official:officialFabricAttachProbe`.
  Observed `status=ATTACHED`, `client=official-probe`,
  `installedMods=mods:6d85fb9272c1d2f5`,
  `runtimeFingerprint=graph:d53a992b228132ce`, `actions=0`,
  `resources=3`, `handles=6`, `events=3`, and event availability
  `unavailable` with reason `event-source-not-discovered`.
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-shared-fabric-event-graph-projection.md`.

## Phase 156: Shared Fabric Client State Graph Projection

- [x] Spec written:
  `docs/superpowers/specs/2026-06-28-156-shared-fabric-client-state-graph-projection-design.md`.
- [x] Plan written:
  `docs/superpowers/plans/2026-06-28-156-shared-fabric-client-state-graph-projection-plan.md`.
- [x] `driver-fabric-discovery` owns shared
  `FabricClientStateGraphSnapshot` and `fabricClientStateGraphFragment`.
- [x] Shared tests prove a connected snapshot emits Craftless-owned
  client/player/inventory/recipe/world/entity/screen resources and
  inventory/recipe/world-block/entity handles as available.
- [x] Shared tests prove a disconnected snapshot marks client-state resources
  and handles unavailable with reason `client-not-connected`, while `screen`
  remains available.
- [x] The Yarn/remap Fabric lane keeps actual Minecraft client-thread state
  probing in the lane but delegates resource/handle projection to the shared
  helper.
- [x] The official backend composes runtime metadata, registry, event, and
  disconnected client-state graph fragments through shared graph composition
  and still imports no `RuntimeCapabilityGraph`.
- [x] Root and driver-local `AGENTS.md` files keep stable shared projection
  guardrails; per-phase client-state graph history now belongs in
  `docs/superpowers/phase-index.md` and the checklist.
- [x] This phase adds no packaged 26.x driver manifest entry, no public
  gameplay API, no static gameplay catalog, no version-specific public route
  family, no survival shortcut, no official-lane SSE completion claim, and no
  final latest/current support claim.

Verification:

- Focused green tests:
  `mise exec -- gradle :driver-fabric-discovery:test :driver-fabric:test --tests '*FabricDriverModuleTest.official lane has opt in launch attach probe task without packaging support claim' :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*'`.
- Real enabled official attach probe:
  `CRAFTLESS_OFFICIAL_FABRIC_ATTACH_PROBE=1`
  `CRAFTLESS_OFFICIAL_ATTACH_PROBE_TIMEOUT_MS=120000`
  `mise exec -- gradle :driver-fabric-official:officialFabricAttachProbe`.
  Observed `status=ATTACHED`, `client=official-probe`,
  `installedMods=mods:6d85fb9272c1d2f5`,
  `runtimeFingerprint=graph:3cc76876d5e4a673`, `actions=0`,
  `resources=10`, `handles=10`, `events=3`, and disconnected client-state
  resources/handles unavailable with reason `client-not-connected` while
  `screen` remains available.
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-shared-fabric-client-state-graph-projection.md`.

## Phase 157: Official Fabric Live Client State Probe

- [x] Spec written:
  `docs/superpowers/specs/2026-06-28-157-official-fabric-live-client-state-probe-design.md`.
- [x] Plan written:
  `docs/superpowers/plans/2026-06-28-157-official-fabric-live-client-state-probe-plan.md`.
- [x] `driver-fabric-official` owns a narrow
  `OfficialFabricClientStateProvider` boundary.
- [x] The production official provider reads `net.minecraft.client.Minecraft`
  through official/Mojang names and schedules cross-thread reads on the
  Minecraft client thread.
- [x] The official backend composes client-state graph projection from the
  lane-provided provider instead of a direct
  `FabricClientStateGraphSnapshot.disconnected()` call.
- [x] Unit tests prove the official backend uses an injected client-state
  snapshot to project client-state resources/handles while keeping operations
  empty.
- [x] Architecture guards prove the official backend no longer calls
  `FabricClientStateGraphSnapshot.disconnected()` directly, no longer calls
  itself a metadata-only backend, still uses shared graph projection, and still
  imports no `RuntimeCapabilityGraph`.
- [x] The real enabled official attach probe still observes `client.attached`.
- [x] The official OpenAPI still reports `actions=0`; title-screen
  client-state resources remain unavailable with reason `client-not-connected`.
- [x] This phase adds no packaged 26.x driver manifest entry, no public
  gameplay API, no static gameplay catalog, no version-specific public route
  family, no survival shortcut, no official-lane SSE completion claim, and no
  final latest/current support claim.

Verification:

- Focused red tests:
  `mise exec -- gradle :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*' :driver-fabric:test --tests '*FabricDriverModuleTest.official lane has opt in launch attach probe task without packaging support claim'`
  failed before `OfficialFabricClientStateProvider` and backend wiring existed.
- Focused green tests:
  `mise exec -- gradle :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*' :driver-fabric:test --tests '*FabricDriverModuleTest.official lane has opt in launch attach probe task without packaging support claim'`.
- Real enabled official attach probe:
  `CRAFTLESS_OFFICIAL_FABRIC_ATTACH_PROBE=1`
  `CRAFTLESS_OFFICIAL_ATTACH_PROBE_TIMEOUT_MS=120000`
  `mise exec -- gradle :driver-fabric-official:officialFabricAttachProbe`.
  Observed `status=ATTACHED`, `client=official-probe`,
  `installedMods=mods:6d85fb9272c1d2f5`,
  `runtimeFingerprint=graph:62818b62751e2d22`, `actions=0`,
  `resources=10`, `handles=10`, `events=3`, and title-screen client-state
  resources unavailable with reason `client-not-connected` while `screen`
  remains available.
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-official-fabric-live-client-state-probe.md`.

## Phase 158: Official Fabric Connected Client State Probe

- [x] Spec written:
  `docs/superpowers/specs/2026-06-28-158-official-fabric-connected-client-state-probe-design.md`.
- [x] Plan written:
  `docs/superpowers/plans/2026-06-28-158-official-fabric-connected-client-state-probe-plan.md`.
- [x] `driver-fabric-official` owns a narrow
  `OfficialFabricClientConnector` boundary plus
  `MinecraftOfficialFabricClientConnector`.
- [x] The production connector uses the official/Mojang-mapped lifecycle
  connect API only: `ConnectScreen.startConnecting`,
  `ServerAddress.parseString`, `ServerData`, `ServerData.Type.OTHER`, and
  `TitleScreen`.
- [x] `OfficialFabricDriverBackend.connect` delegates to the connector and
  reports observed lifecycle scheduling through the shared driver session
  contract.
- [x] The opt-in official attach probe can start a real local Minecraft `26.2`
  server, launch the official Fabric client, observe `client.attached`, call
  `POST /clients/{id}:connect`, and poll generated per-client OpenAPI until
  connected client-state resources become available.
- [x] The connected official OpenAPI still reports `actions=0` and therefore
  does not claim gameplay support.
- [x] Architecture guards prove the official lane still does not depend on
  `driver-fabric`, still does not package as a supported driver-mod manifest
  entry, and still does not copy `FabricClientGateway` or
  `FabricOperationAdapters`.
- [x] This phase adds no packaged 26.x driver manifest entry, no public
  gameplay API, no static gameplay catalog, no version-specific public route
  family, no survival shortcut, no official-lane SSE completion claim, and no
  final latest/current support claim.

Verification:

- Focused red checks:
  `mise exec -- gradle :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest.official backend connect delegates to lifecycle connector*'`
  failed before the connector seam existed with missing `clientConnector` and
  `OfficialFabricClientConnector`.
- Focused green checks:
  `mise exec -- gradle :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*'`,
  `mise exec -- gradle :driver-fabric-official:compileKotlin`,
  `mise exec -- gradle :driver-fabric-official:compileTestKotlin`, and
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.official lane has opt in launch attach probe task without packaging support claim*'`.
- Real enabled connected official attach probe:
  `CRAFTLESS_OFFICIAL_FABRIC_ATTACH_PROBE=1`
  `CRAFTLESS_OFFICIAL_ATTACH_PROBE_CONNECT=1`
  `CRAFTLESS_OFFICIAL_ATTACH_PROBE_TIMEOUT_MS=180000`
  `mise exec -- gradle :driver-fabric-official:officialFabricAttachProbe`.
  Observed `status=CONNECTED`, `client=official-probe`,
  `connectTarget=127.0.0.1:65150`, `actions=0`, `resources=10`,
  `handles=10`, `events=3`, and available connected resources
  `runtime`, `client`, `player`, `inventory`, `recipe`, `world`, `entity`,
  and `screen`.
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-official-fabric-connected-client-state-probe.md`.

## Phase 159: Official Fabric Connected Server Feature Metadata

- [x] Spec written:
  `docs/superpowers/specs/2026-06-28-159-official-fabric-connected-server-feature-metadata-design.md`.
- [x] Plan written:
  `docs/superpowers/plans/2026-06-28-159-official-fabric-connected-server-feature-metadata-plan.md`.
- [x] `driver-fabric-official` owns a narrow
  `OfficialFabricServerFeatureProvider` boundary plus
  `MinecraftOfficialFabricServerFeatureProvider`.
- [x] The production provider reads official/Mojang-mapped lifecycle metadata
  only: connection presence, server kind, local-server state, and enabled
  feature-set identity.
- [x] `officialFabricRuntimeMetadataProvider(...)` accepts lane-provided
  server-feature evidence and feeds it into the shared
  `FabricRuntimeMetadataSnapshot.serverFeatureFingerprint` path.
- [x] `OfficialFabricDriverBackend.runtimeGraph(...)` exposes the generated
  server-feature fingerprint on the shared runtime resource evidence while
  keeping `graph.operations` empty.
- [x] The enabled connected official attach probe generated OpenAPI with
  `x-craftless-server-feature-fingerprint=server-features:bd2c005ee588f506`,
  `actions=0`, `resources=10`, `handles=10`, and `events=3`.
- [x] This phase adds no packaged 26.x driver manifest entry, no public
  gameplay API, no static gameplay catalog, no version-specific public route
  family, no survival shortcut, no official-lane SSE completion claim, and no
  final latest/current support claim.

Verification:

- Focused red check:
  `mise exec -- gradle :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest.official runtime metadata uses lane server feature provider*'`
  failed before the provider seam existed with missing
  `serverFeatureProvider` and `OfficialFabricServerFeatureProvider`.
- Focused green check:
  `mise exec -- gradle :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*'`.
- Real enabled connected official attach probe:
  `CRAFTLESS_OFFICIAL_FABRIC_ATTACH_PROBE=1`
  `CRAFTLESS_OFFICIAL_ATTACH_PROBE_CONNECT=1`
  `CRAFTLESS_OFFICIAL_ATTACH_PROBE_TIMEOUT_MS=180000`
  `mise exec -- gradle :driver-fabric-official:officialFabricAttachProbe`.
  Observed `status=CONNECTED`, `client=official-probe`,
  `connectTarget=127.0.0.1:50188`, `actions=0`, `resources=10`,
  `handles=10`, `events=3`, and
  `serverFeatureFingerprint=server-features:bd2c005ee588f506`.
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-official-fabric-connected-server-feature-metadata.md`.

## Phase 160: Official Fabric Registry Metadata Probe

- [x] Spec written:
  `docs/superpowers/specs/2026-06-28-160-official-fabric-registry-metadata-probe-design.md`.
- [x] Plan written:
  `docs/superpowers/plans/2026-06-28-160-official-fabric-registry-metadata-probe-plan.md`.
- [x] Phase history is maintained in `docs/superpowers/phase-index.md`, not
  appended to root `AGENTS.md`.
- [x] `driver-fabric-official` owns a narrow
  `OfficialFabricRegistryProvider` boundary plus
  `MinecraftOfficialFabricRegistryProvider`.
- [x] The production provider reads official/Mojang-mapped registry keys from
  `BuiltInRegistries` for block, item, entity type, screen/menu, status
  effect, and game event metadata.
- [x] `officialFabricRuntimeMetadataProvider(...)` accepts lane-provided
  registry entries and feeds them into the shared
  `FabricRuntimeMetadataSnapshot.registryFingerprint` path.
- [x] `OfficialFabricDriverBackend.runtimeGraph(...)` marks the shared
  `registry` resource and registry handles available when the metadata
  fingerprint is discovered, while keeping `graph.operations` empty.
- [x] The enabled connected official attach probe generated OpenAPI with
  `x-craftless-registry-fingerprint=registries:6797dc89ef586485`,
  `registry.availability=available`, `actions=0`, `resources=10`,
  `handles=10`, and `events=3`.
- [x] This phase adds no packaged 26.x driver manifest entry, no public
  gameplay API, no static gameplay catalog, no version-specific public route
  family, no action adapter, no survival shortcut, no official-lane SSE
  completion claim, and no final latest/current support claim.

Verification:

- Focused red check:
  `mise exec -- gradle :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest.official runtime metadata uses lane registry provider*'`
  failed before the provider seam existed with missing `registryProvider` and
  `OfficialFabricRegistryProvider`.
- Focused graph red check:
  `mise exec -- gradle :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest.official backend projects client state from lane provider without adding operations*'`
  failed while the official backend still forced registry graph availability to
  false.
- Focused green check:
  `mise exec -- gradle :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*'`.
- Real enabled connected official attach probe:
  `CRAFTLESS_OFFICIAL_FABRIC_ATTACH_PROBE=1`
  `CRAFTLESS_OFFICIAL_ATTACH_PROBE_CONNECT=1`
  `CRAFTLESS_OFFICIAL_ATTACH_PROBE_TIMEOUT_MS=180000`
  `mise exec -- gradle :driver-fabric-official:officialFabricAttachProbe`.
  Observed `status=CONNECTED`, `client=official-probe`,
  `connectTarget=127.0.0.1:51113`, `actions=0`, `resources=10`,
  `handles=10`, `events=3`, `registryFingerprint=registries:6797dc89ef586485`,
  and `registryAvailability=available`.
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-official-fabric-registry-metadata-probe.md`.

## Phase 161: Official Fabric Event Source Metadata

- [x] Spec written:
  `docs/superpowers/specs/2026-06-28-161-official-fabric-event-source-metadata-design.md`.
- [x] Plan written:
  `docs/superpowers/plans/2026-06-28-161-official-fabric-event-source-metadata-plan.md`.
- [x] Phase history is maintained in `docs/superpowers/phase-index.md`, not
  appended to root `AGENTS.md`.
- [x] `driver-fabric-official` owns a narrow
  `OfficialFabricEventSourceProvider` boundary plus
  `MinecraftOfficialFabricEventSources`.
- [x] The production provider registers official Fabric client tick and play
  connection callbacks and emits Craftless-owned event-source evidence.
- [x] `OfficialFabricDriverBackend.runtimeGraph(...)` feeds lane-provided
  event-source evidence into the shared `fabricEventGraphFragment(...)` path.
- [x] The focused graph test proves the shared `event` resource and
  `event.lifecycle`, `event.action`, and `event.capability` nodes become
  available when evidence exists, while `graph.operations` remains empty.
- [x] The enabled connected official attach probe generated OpenAPI with
  `event.action=available`, `event.capability=available`,
  `event.lifecycle=available`, `actions=0`, `resources=10`, `handles=10`, and
  `events=3`.
- [x] This phase adds no packaged 26.x driver manifest entry, no public
  gameplay API, no static gameplay catalog, no version-specific public route
  family, no action adapter, no survival shortcut, no official-lane SSE
  completion claim, and no final latest/current support claim.

Verification:

- Focused red check:
  `mise exec -- gradle :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest.official backend projects client state from lane provider without adding operations*'`
  failed before the provider seam existed with missing `eventSourceProvider`
  and `OfficialFabricEventSourceProvider`.
- Focused green check:
  `mise exec -- gradle :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*'`.
- Real enabled connected official attach probe:
  `CRAFTLESS_OFFICIAL_FABRIC_ATTACH_PROBE=1`
  `CRAFTLESS_OFFICIAL_ATTACH_PROBE_CONNECT=1`
  `CRAFTLESS_OFFICIAL_ATTACH_PROBE_TIMEOUT_MS=180000`
  `mise exec -- gradle :driver-fabric-official:officialFabricAttachProbe`.
  Observed `status=CONNECTED`, `client=official-probe`,
  `connectTarget=127.0.0.1:52329`, `actions=0`, `resources=10`,
  `handles=10`, `events=3`, and available event nodes
  `event.action`, `event.capability`, and `event.lifecycle`.
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-official-fabric-event-source-metadata.md`.

## Phase 162: Official Fabric Connected SSE Evidence

- [x] Spec written:
  `docs/superpowers/specs/2026-06-28-162-official-fabric-connected-sse-evidence-design.md`.
- [x] Plan written:
  `docs/superpowers/plans/2026-06-28-162-official-fabric-connected-sse-evidence-plan.md`.
- [x] Phase history is maintained in `docs/superpowers/phase-index.md`, not
  appended to root `AGENTS.md`.
- [x] The official attach probe now fetches the existing public
  `GET /clients/{id}/events:stream` route and writes
  `client-events-stream.sse`.
- [x] `probe-result.json` records parsed `streamedEventTypes` from the SSE
  artifact.
- [x] The enabled connected official attach probe generated SSE evidence with
  `event: client.created`, `event: client.attached`, and
  `event: client.connected`.
- [x] The same connected probe preserved `actions=0`.
- [x] This phase adds no packaged 26.x driver manifest entry, no public
  gameplay API, no static gameplay catalog, no version-specific public route
  family, no action adapter, no survival shortcut, and no final latest/current
  support claim.

Verification:

- Red artifact check:
  `test -f driver-fabric-official/build/craftless-official-attach-probe/client-events-stream.sse`
  failed before implementation with exit code `1`.
- Focused green check:
  `mise exec -- gradle :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*'`.
- Real enabled connected official attach probe:
  `CRAFTLESS_OFFICIAL_FABRIC_ATTACH_PROBE=1`
  `CRAFTLESS_OFFICIAL_ATTACH_PROBE_CONNECT=1`
  `CRAFTLESS_OFFICIAL_ATTACH_PROBE_TIMEOUT_MS=180000`
  `mise exec -- gradle :driver-fabric-official:officialFabricAttachProbe`.
  Observed `status=CONNECTED`, `client=official-probe`,
  `connectTarget=127.0.0.1:53132`, streamed event types
  `client.created`, `client.attached`, and `client.connected`, and
  `actions=0`.
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-official-fabric-connected-sse-evidence.md`.

## Phase 163: Official Fabric Public Projection Endpoints

- [x] Spec written:
  `docs/superpowers/specs/2026-06-28-163-official-fabric-public-projection-endpoints-design.md`.
- [x] Plan written:
  `docs/superpowers/plans/2026-06-28-163-official-fabric-public-projection-endpoints-plan.md`.
- [x] Phase history is maintained in `docs/superpowers/phase-index.md`, not
  appended to root `AGENTS.md`.
- [x] The official attach probe now fetches the existing public
  `GET /clients/{id}/actions` and `GET /clients/{id}/resources` endpoints.
- [x] The probe writes `client-actions.json` and `client-resources.json`.
- [x] `probe-result.json` records `publicActionCount` and
  `publicResourceIds` from those endpoint bodies.
- [x] The enabled connected official attach probe generated projection
  evidence with `actions=0` and resource ids `runtime`, `registry`, `event`,
  `client`, `player`, `inventory`, `recipe`, `world`, `entity`, and `screen`.
- [x] This phase adds no packaged 26.x driver manifest entry, no public
  gameplay API, no static gameplay catalog, no version-specific public route
  family, no action adapter, no survival shortcut, and no final latest/current
  support claim.

Verification:

- Red artifact check:
  `test -f driver-fabric-official/build/craftless-official-attach-probe/client-actions.json && test -f driver-fabric-official/build/craftless-official-attach-probe/client-resources.json`
  failed before implementation with exit code `1`.
- Focused green check:
  `mise exec -- gradle :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*'`.
- Real enabled connected official attach probe:
  `CRAFTLESS_OFFICIAL_FABRIC_ATTACH_PROBE=1`
  `CRAFTLESS_OFFICIAL_ATTACH_PROBE_CONNECT=1`
  `CRAFTLESS_OFFICIAL_ATTACH_PROBE_TIMEOUT_MS=180000`
  `mise exec -- gradle :driver-fabric-official:officialFabricAttachProbe`.
  Observed public projection endpoint artifacts with `publicActionCount=0`
  and public resource ids `runtime`, `registry`, `event`, `client`, `player`,
  `inventory`, `recipe`, `world`, `entity`, and `screen`.
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-official-fabric-public-projection-endpoints.md`.

## Phase 164: Official Fabric JSON-RPC Query Evidence

- [x] Spec written:
  `docs/superpowers/specs/2026-06-28-164-official-fabric-json-rpc-query-evidence-design.md`.
- [x] Plan written:
  `docs/superpowers/plans/2026-06-28-164-official-fabric-json-rpc-query-evidence-plan.md`.
- [x] Phase history is maintained in `docs/superpowers/phase-index.md`, not
  appended to root `AGENTS.md`.
- [x] The official attach probe now queries the existing public
  `POST /clients/{id}:rpc` endpoint with JSON-RPC `query` targets `openapi`,
  `actions`, and `resources`.
- [x] The probe writes `client-rpc-openapi.json`,
  `client-rpc-actions.json`, and `client-rpc-resources.json`.
- [x] `probe-result.json` records `rpcQueryTargets`, `rpcActionCount`, and
  `rpcResourceIds` from those JSON-RPC response bodies.
- [x] The enabled connected official attach probe generated JSON-RPC query
  evidence with `rpcActionCount=0` and resource ids `runtime`, `registry`,
  `event`, `client`, `player`, `inventory`, `recipe`, `world`, `entity`, and
  `screen`.
- [x] This phase adds no packaged 26.x driver manifest entry, no public
  gameplay API, no static gameplay catalog, no version-specific public route
  family, no action adapter, no survival shortcut, and no final latest/current
  support claim.

Verification:

- Red artifact check:
  `test -f driver-fabric-official/build/craftless-official-attach-probe/client-rpc-openapi.json && test -f driver-fabric-official/build/craftless-official-attach-probe/client-rpc-actions.json && test -f driver-fabric-official/build/craftless-official-attach-probe/client-rpc-resources.json`
  failed before implementation with exit code `1`.
- Focused green check:
  `mise exec -- gradle :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*'`.
- Real enabled connected official attach probe:
  `CRAFTLESS_OFFICIAL_FABRIC_ATTACH_PROBE=1`
  `CRAFTLESS_OFFICIAL_ATTACH_PROBE_CONNECT=1`
  `CRAFTLESS_OFFICIAL_ATTACH_PROBE_TIMEOUT_MS=180000`
  `mise exec -- gradle :driver-fabric-official:officialFabricAttachProbe`.
  Observed `status=CONNECTED`, `client=official-probe`,
  `connectTarget=127.0.0.1:55789`, JSON-RPC query targets `openapi`,
  `actions`, and `resources`, `rpcActionCount=0`, and RPC resource ids
  `runtime`, `registry`, `event`, `client`, `player`, `inventory`, `recipe`,
  `world`, `entity`, and `screen`.
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-official-fabric-json-rpc-query-evidence.md`.

## Phase 165: Official Fabric JSON-RPC Subscription SSE Evidence

- [x] Spec written:
  `docs/superpowers/specs/2026-06-28-165-official-fabric-json-rpc-subscription-sse-evidence-design.md`.
- [x] Plan written:
  `docs/superpowers/plans/2026-06-28-165-official-fabric-json-rpc-subscription-sse-evidence-plan.md`.
- [x] Phase history is maintained in `docs/superpowers/phase-index.md`, not
  appended to root `AGENTS.md`.
- [x] The official attach probe now uses the existing public
  `POST /clients/{id}:rpc` endpoint for JSON-RPC `subscribe`,
  `subscriptions` query, and `unsubscribe`.
- [x] The official attach probe now fetches filtered SSE via the existing
  `GET /clients/{id}/events:stream?subscriptionId=...` route.
- [x] The probe writes `client-rpc-subscribe.json`,
  `client-events-subscription-stream.sse`, `client-rpc-subscriptions.json`,
  `client-rpc-unsubscribe.json`, and
  `client-rpc-subscriptions-after-unsubscribe.json`.
- [x] `probe-result.json` records `rpcSubscriptionId`,
  `rpcSubscriptionEventTypes`, `rpcSubscriptionCount`, and
  `rpcSubscriptionCountAfterUnsubscribe`.
- [x] The enabled connected official attach probe generated subscription
  evidence for `client.connected` with one active subscription, filtered SSE
  containing only `event: client.connected`, `unsubscribed=true`, and zero
  subscriptions after unsubscribe.
- [x] This phase adds no packaged 26.x driver manifest entry, no public
  gameplay API, no static gameplay catalog, no version-specific public route
  family, no action adapter, no survival shortcut, and no final latest/current
  support claim.

Verification:

- Red artifact check:
  `test -f driver-fabric-official/build/craftless-official-attach-probe/client-rpc-subscribe.json && test -f driver-fabric-official/build/craftless-official-attach-probe/client-events-subscription-stream.sse && test -f driver-fabric-official/build/craftless-official-attach-probe/client-rpc-subscriptions.json && test -f driver-fabric-official/build/craftless-official-attach-probe/client-rpc-unsubscribe.json && test -f driver-fabric-official/build/craftless-official-attach-probe/client-rpc-subscriptions-after-unsubscribe.json`
  failed before implementation with exit code `1`.
- Focused green check:
  `mise exec -- gradle :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*'`.
- Real enabled connected official attach probe:
  `CRAFTLESS_OFFICIAL_FABRIC_ATTACH_PROBE=1`
  `CRAFTLESS_OFFICIAL_ATTACH_PROBE_CONNECT=1`
  `CRAFTLESS_OFFICIAL_ATTACH_PROBE_TIMEOUT_MS=180000`
  `mise exec -- gradle :driver-fabric-official:officialFabricAttachProbe`.
  Observed `status=CONNECTED`, `client=official-probe`,
  `connectTarget=127.0.0.1:56484`, subscription id
  `subscription:official-probe:0001`, filter type `client.connected`,
  filtered SSE event type `client.connected`, active subscription count `1`,
  `unsubscribed=true`, post-unsubscribe subscription count `0`, and
  `rpcActionCount=0`.
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-official-fabric-json-rpc-subscription-sse-evidence.md`.

## Phase 166: Runtime Graph Default Action Projection

- [x] Spec written:
  `docs/superpowers/specs/2026-06-28-166-runtime-graph-default-action-projection-design.md`.
- [x] Plan written:
  `docs/superpowers/plans/2026-06-28-166-runtime-graph-default-action-projection-plan.md`.
- [x] Phase history is maintained in `docs/superpowers/phase-index.md`, not
  appended to root `AGENTS.md`.
- [x] `DriverSession.actions()` now defaults to sorted
  `runtimeGraph().operations.map { it.toDriverActionDescriptor() }`.
- [x] Shared projection helpers now live in `driver-api` for
  `RuntimeOperationNode`, `RuntimeSchema`, and `RuntimeAvailability`.
- [x] Fake sessions, prepared graph-empty sessions, and Fabric backend code no
  longer duplicate graph-to-action descriptor conversion where the shared
  projection is enough.
- [x] The Fabric module guardrail test now reads durable version-breadth rules
  from `docs/agent-operating-contract.md`, keeping root `AGENTS.md` short.
- [x] This phase adds no gameplay operation, no public route, no static action
  catalog, no action adapter, no scenario shortcut, and no official 26.x
  support claim.

Verification:

- Red contract check:
  `mise exec -- gradle :driver-api:test --tests '*DriverSessionContractTest*'`
  failed before implementation because `GraphOnlyDriverSession` did not
  implement abstract `actions()`.
- Focused green contract check:
  `mise exec -- gradle :driver-api:cleanTest :driver-api:test --tests '*DriverSessionContractTest*'`.
- Affected module check:
  `mise exec -- gradle :driver-api:test :testkit:test :driver-fabric:test :daemon:test :driver-fabric-official:test`.
- Latest official lane check:
  `mise run fabric-lane-check-latest-official`.
- Local CI:
  `mise run ci`.
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-runtime-graph-default-action-projection.md`.

## Phase 167: Backend Runtime Graph Action Default

- [x] Spec written:
  `docs/superpowers/specs/2026-06-28-167-backend-runtime-graph-action-default-design.md`.
- [x] Plan written:
  `docs/superpowers/plans/2026-06-28-167-backend-runtime-graph-action-default-plan.md`.
- [x] `DriverBackend.actions(clientId)` now defaults to sorted
  `runtimeGraph(clientId).operations.map { it.toDriverActionDescriptor() }`.
- [x] A graph-only backend test proves `BackendDriverSession.actions()` exposes
  runtime graph operations without requiring a separate backend action-list
  override.
- [x] `FabricDriverBackend` no longer owns a duplicate
  `actions(clientId)` graph-to-action override.
- [x] This phase adds no gameplay operation, no public route, no CLI command,
  no action adapter, no static action catalog, no scenario shortcut, no version
  lane, and no support claim.

Verification:

- Red contract check:
  `mise exec -- gradle :driver-runtime:test --tests '*BackendDriverSessionTest.driver backend default actions derive from runtime graph operations*'`
  failed before implementation with `NoSuchElementException` because
  `session.actions()` was empty.
- Focused green contract check:
  `mise exec -- gradle :driver-runtime:test --tests '*BackendDriverSessionTest.driver backend default actions derive from runtime graph operations*'`.
- Affected module check:
  `mise exec -- gradle :driver-runtime:test :driver-fabric:test`.
- Local CI:
  `mise run ci`.
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-backend-runtime-graph-action-default.md`.

## Phase 168: OpenAPI Route Authority

- [x] Spec written:
  `docs/superpowers/specs/2026-06-28-168-openapi-route-authority-design.md`.
- [x] Plan written:
  `docs/superpowers/plans/2026-06-28-168-openapi-route-authority-plan.md`.
- [x] `ClientSessionService.routesFor(clientId)` now projects client routes
  from `openApiFor(clientId)` instead of asking the driver for a second
  sorted action list.
- [x] A graph-backed driver regression proves `routesFor(clientId)` can expose
  `/clients/{id}/player:chat` from generated runtime graph OpenAPI even when
  `DriverSession.actions()` is deliberately unavailable.
- [x] The route projection filters the generated document to the concrete
  `/clients/{id}` surface while preserving action ids and route metadata from
  OpenAPI operation extensions.
- [x] This phase adds no gameplay operation, no public route shape, no CLI
  command, no action adapter, no static action catalog, no scenario shortcut,
  no version lane, and no support claim.

Verification:

- Red daemon regression:
  `mise exec -- gradle :daemon:test --tests '*ClientSessionServiceTest.client route list is projected from generated runtime graph openapi*'`
  failed before implementation because `routesFor(clientId)` called
  `DriverSession.actions()`.
- Focused green daemon regression:
  `mise exec -- gradle :daemon:test --tests '*ClientSessionServiceTest.client route list is projected from generated runtime graph openapi*'`.
- Client session regression:
  `mise exec -- gradle :daemon:test --tests '*ClientSessionServiceTest*'`.
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-openapi-route-authority.md`.
- Current local CI:
  `mise run ci`.

## Phase 169: Public-Agent OpenAPI Action Authority

- [x] Spec written:
  `docs/superpowers/specs/2026-06-28-169-public-agent-openapi-action-authority-design.md`.
- [x] Plan written:
  `docs/superpowers/plans/2026-06-28-169-public-agent-openapi-action-authority-plan.md`.
- [x] The public-agent gameplay runner now parses action ids and action
  argument metadata from generated per-client OpenAPI `x-craftless-actions`.
- [x] `/clients/{id}/actions` remains fetched and recorded as a projection
  artifact, but it is no longer the public-agent workflow authority for
  required primitive checks or argument support checks.
- [x] A focused regression proves the runner succeeds when generated OpenAPI
  has the complete action metadata but the `/actions` projection is empty.
- [x] This phase adds no gameplay operation, no public route shape, no CLI
  command, no action adapter, no static action catalog, no scenario shortcut,
  no version lane, and no support claim.

Verification:

- Red public-agent regression:
  `mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest.runner uses generated client openapi actions as authority over actions projection*'`
  failed before implementation because the runner blocked on the empty
  `/actions` projection.
- Focused green public-agent regression:
  `mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest.runner uses generated client openapi actions as authority over actions projection*'`.
- Public-agent class regression:
  `mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest*'`.
- Final local verification is recorded in
  `docs/superpowers/evidence/2026-06-28-public-agent-openapi-action-authority.md`.

## Final Completion Gate

- [~] Phase history is indexed in `docs/superpowers/phase-index.md` and
  detailed in the phase sections above. Historical phases establish direction
  and guardrails, but they do not by themselves close the product goal.
- [ ] Completion remains blocked until CL-01 through CL-08 in the Active
  Completion Board are checked with fresh evidence, including runnable support
  evidence for latest/current and representative older version lanes under the
  same public API/CLI gates.
- [x] `mise run lint` passes. Current 2026-06-28 evidence: `mise run ci`
  completed lint successfully during the latest local verification sweep.
- [x] `mise run architecture-check` passes. Current 2026-06-28 evidence:
  `mise run architecture-check` completed successfully before this checklist
  update.
- [x] `mise run ci` passes. Current 2026-06-28 evidence: `mise run ci`
  completed successfully during the latest local verification sweep.
- [x] CLI packaging succeeds. Current 2026-06-28 local evidence:
  `mise run package-cli` built `:cli:distZip`, `:cli:distTar`, refreshed
  `build/docker/craftless`, and the packaged binary returned
  `{"ok":true,...}` for `server start --once --port 0`.
- [x] Docker runtime smoke passes. Current 2026-06-28 local evidence:
  `docker build -t craftless:local .` succeeded, and
  `docker run --rm craftless:local /opt/craftless/bin/craftless server start --once --port 0 --workspace /tmp/craftless`
  returned `{"ok":true,...}` with a generated localhost server URL.
- [x] Install script smoke passes. Current 2026-06-28 local evidence:
  `install.sh` installed published `craftless 0.1.0` into a temp directory and
  that installed binary returned `ok=true` for `server start --once --port 0`.
- [x] Final real gameplay evidence is captured without server-provisioned
  inventory or manual movement for Craftless. Current 2026-06-28 no-hold run
  evidence under `driver-fabric/build/craftless-final-gameplay/artifacts/`
  fetched generated per-client OpenAPI/actions/resources, subscribed to SSE,
  collected materials, crafted and equipped a `Wooden Sword`, found Cows
  through generated `entity.query`, killed a Cow through generated
  `entity.attack`, and observed `Raw Beef`, `Leather`, and the Cow with
  `alive:false` through generated `entity.query`.
- [x] Phase 65 final gameplay evidence is accepted from public API/CLI
  artifacts without requiring human Minecraft chat confirmation.
- [~] Latest and representative older-version compatibility probes have current
  historical evidence. Latest `26.2` and older `1.20.6` probe records remain
  diagnostics. Phase 137 makes the representative older Fabric source lane
  compile, Phase 138 packages that lane as a selectable artifact, Phase 139
  proves supervisor selection, Phase 140 prevents false smoke evidence, and
  Phase 141 proves real Gradle-harness launch, attach, generated API, SSE, and
  diagnostic generated-action smoke for Minecraft `1.20.6`. Phase 142 proves
  installed packaged CLI launch, self-attach, generated OpenAPI, generated
  actions/resources, SSE events, and cleanup for Minecraft `1.20.6`. Phase
  144 makes missing latest/current driver lanes fail before heavy binary cache
  downloads. Phase 145 records the latest/current official-mapping probe
  blocker as `loom-remap-requires-mappings`, and Phase 146 removes that build
  blocker by compiling a separate non-remap official Fabric module boundary.
  Full product support still requires porting or extracting driver attach,
  runtime discovery/projection/invocation, generated OpenAPI/actions/resources,
  SSE, packaged launch, final compatibility audit, and honest final survival
  gameplay without server-provisioned inventory.
- [ ] Checklist, active code slice, evidence, and phase index are committed and
  pushed to `main` after the next CL item is verified.
