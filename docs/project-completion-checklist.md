# Craftless Project Completion Checklist

This is the active completion red line. Craftless is not complete until every
unchecked item below is checked with Codex-verifiable evidence. Human
Minecraft chat confirmation is optional diagnostic evidence and is not required
for completion.

Legend:

- `[ ]` not done
- `[~]` in progress
- `[x]` done with evidence
- `[!]` blocked

## Current Baseline

- [x] Repository is named Craftless and uses `com.minekube.craftless`.
- [x] Tooling is pinned through `mise`.
- [x] JVM HTTP surfaces use Ktor Server/Client.
- [x] CLI binary is `craftless`.
- [x] Stable supervisor OpenAPI exists at `GET /openapi.json`.
- [x] Per-client OpenAPI exists at `GET /clients/{id}/openapi.json`.
- [x] Generic invocation exists at `POST /clients/{id}:run`.
- [x] Current CLI and helper consumers use live per-client OpenAPI metadata for
  existing action dispatch, generated help, tools export, resources, JSON-RPC
  query, and cache revalidation.
- [x] Current Fabric smoke proves a real client can launch, join a local
  server, chat, move, observe inventory, equip an iron sword, invoke block
  interactions, and write evidence artifacts. This is diagnostic smoke only:
  the earlier iron sword was server-provisioned and does not count as final
  completion evidence.
- [x] Root `AGENTS.md` now states that existing hand-written gameplay bindings
  are transitional bootstrap/evidence code, not the durable API shape.

## Required Specs And Plans

- [x] Spec exists: `docs/superpowers/specs/2026-06-26-01-truth-and-guardrails-design.md`.
- [x] Plan exists: `docs/superpowers/plans/2026-06-26-01-truth-and-guardrails-plan.md`.
- [x] Spec exists: `docs/superpowers/specs/2026-06-26-02-runtime-capability-graph-design.md`.
- [x] Plan exists: `docs/superpowers/plans/2026-06-26-02-runtime-capability-graph-plan.md`.
- [x] Spec exists: `docs/superpowers/specs/2026-06-26-03-fabric-discovery-probes-design.md`.
- [x] Plan exists: `docs/superpowers/plans/2026-06-26-03-fabric-discovery-probes-plan.md`.
- [x] Spec exists: `docs/superpowers/specs/2026-06-26-04-projection-openapi-design.md`.
- [x] Plan exists: `docs/superpowers/plans/2026-06-26-04-projection-openapi-plan.md`.
- [x] Spec exists: `docs/superpowers/specs/2026-06-26-05-generic-invocation-design.md`.
- [x] Plan exists: `docs/superpowers/plans/2026-06-26-05-generic-invocation-plan.md`.
- [x] Spec exists: `docs/superpowers/specs/2026-06-26-06-sse-json-rpc-consumers-design.md`.
- [x] Plan exists: `docs/superpowers/plans/2026-06-26-06-sse-json-rpc-consumers-plan.md`.
- [x] Spec exists: `docs/superpowers/specs/2026-06-26-07-final-gameplay-completion-design.md`.
- [x] Plan exists: `docs/superpowers/plans/2026-06-26-07-final-gameplay-completion-plan.md`.
- [x] Spec exists: `docs/superpowers/specs/2026-06-26-08-honest-survival-navigation-design.md`.
- [x] Plan exists: `docs/superpowers/plans/2026-06-26-08-honest-survival-navigation-plan.md`.
- [x] Spec exists: `docs/superpowers/specs/2026-06-26-09-pathfinder-backed-execution-design.md`.
- [x] Plan exists: `docs/superpowers/plans/2026-06-26-09-pathfinder-backed-execution-plan.md`.
- [x] Spec exists: `docs/superpowers/specs/2026-06-26-10-survival-task-execution-design.md`.
- [x] Plan exists: `docs/superpowers/plans/2026-06-26-10-survival-task-execution-plan.md`.
- [x] Spec exists for the public-agent final gameplay path: generated
  OpenAPI + SSE/JSON-RPC + adaptive CLI + agent skill compose the survival
  scenario outside the driver, without adding `task.survival.*` as durable
  product API.
- [x] Plan exists for the public-agent final gameplay path:
  `docs/superpowers/plans/2026-06-26-11-public-agent-gameplay-plan.md`.
- [x] Spec exists: `docs/superpowers/specs/2026-06-26-15-public-agent-material-exploration-design.md`.
- [x] Plan exists: `docs/superpowers/plans/2026-06-26-15-public-agent-material-exploration-plan.md`.
- [x] Spec exists: `docs/superpowers/specs/2026-06-26-16-targetable-block-break-design.md`.
- [x] Plan exists: `docs/superpowers/plans/2026-06-26-16-targetable-block-break-plan.md`.
- [x] Spec exists: `docs/superpowers/specs/2026-06-26-17-public-agent-action-timeout-design.md`.
- [x] Plan exists: `docs/superpowers/plans/2026-06-26-17-public-agent-action-timeout-plan.md`.
- [x] Spec exists: `docs/superpowers/specs/2026-06-26-18-public-agent-material-equip-design.md`.
- [x] Plan exists: `docs/superpowers/plans/2026-06-26-18-public-agent-material-equip-plan.md`.
- [x] Spec exists: `docs/superpowers/specs/2026-06-26-19-sustained-block-break-design.md`.
- [x] Plan exists: `docs/superpowers/plans/2026-06-26-19-sustained-block-break-plan.md`.
- [x] Spec exists: `docs/superpowers/specs/2026-06-26-20-public-agent-material-pickup-design.md`.
- [x] Plan exists: `docs/superpowers/plans/2026-06-26-20-public-agent-material-pickup-plan.md`.
- [x] Spec exists: `docs/superpowers/specs/2026-06-26-21-public-agent-drop-perception-design.md`.
- [x] Plan exists: `docs/superpowers/plans/2026-06-26-21-public-agent-drop-perception-plan.md`.
- [x] Spec exists: `docs/superpowers/specs/2026-06-26-22-bounded-material-exploration-design.md`.
- [x] Plan exists: `docs/superpowers/plans/2026-06-26-22-bounded-material-exploration-plan.md`.
- [x] Spec exists: `docs/superpowers/specs/2026-06-26-27-java-runtime-resolution-design.md`.
- [x] Plan exists: `docs/superpowers/plans/2026-06-26-27-java-runtime-resolution-plan.md`.
- [x] Spec exists: `docs/superpowers/specs/2026-06-26-28-generic-recipe-crafting-design.md`.
- [x] Plan exists: `docs/superpowers/plans/2026-06-26-28-generic-recipe-crafting-plan.md`.
- [x] Spec exists: `docs/superpowers/specs/2026-06-26-29-legacy-survival-task-api-removal-design.md`.
- [x] Plan exists: `docs/superpowers/plans/2026-06-26-29-legacy-survival-task-api-removal-plan.md`.
- [x] Spec exists: `docs/superpowers/specs/2026-06-27-37-scenario-shortcut-action-guard-design.md`.
- [x] Plan exists: `docs/superpowers/plans/2026-06-27-37-scenario-shortcut-action-guard-plan.md`.
- [x] Spec exists: `docs/superpowers/specs/2026-06-27-38-combat-miss-retry-design.md`.
- [x] Plan exists: `docs/superpowers/plans/2026-06-27-38-combat-miss-retry-plan.md`.
- [x] Spec exists: `docs/superpowers/specs/2026-06-27-39-fabric-library-replacement-design.md`.
- [x] Plan exists: `docs/superpowers/plans/2026-06-27-39-fabric-library-replacement-plan.md`.
- [x] Spec exists: `docs/superpowers/specs/2026-06-27-40-rule-selected-native-libraries-design.md`.
- [x] Plan exists: `docs/superpowers/plans/2026-06-27-40-rule-selected-native-libraries-plan.md`.
- [x] Spec exists: `docs/superpowers/specs/2026-06-27-41-launch-argument-placeholders-design.md`.
- [x] Plan exists: `docs/superpowers/plans/2026-06-27-41-launch-argument-placeholders-plan.md`.
- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-42-standard-asset-object-layout-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-42-standard-asset-object-layout-plan.md`.
- [x] Spec exists:
  `docs/superpowers/specs/2026-06-27-43-client-logging-config-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-27-43-client-logging-config-plan.md`.
- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-65-codex-evidence-completion-gate-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-65-codex-evidence-completion-gate-plan.md`.
- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-66-representative-older-release-lane-evidence-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-66-representative-older-release-lane-evidence-plan.md`.
- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-67-final-gameplay-codex-evidence-default-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-67-final-gameplay-codex-evidence-default-plan.md`.
- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-68-full-codex-evidence-gate-refresh-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-68-full-codex-evidence-gate-refresh-plan.md`.
- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-69-readme-roadmap-evidence-alignment-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-69-readme-roadmap-evidence-alignment-plan.md`.
- [x] Spec exists:
  `docs/superpowers/specs/2026-06-28-70-public-agent-operational-workflow-design.md`.
- [x] Plan exists:
  `docs/superpowers/plans/2026-06-28-70-public-agent-operational-workflow-plan.md`.

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
  machine-readable reasons across the Fabric backend, the reusable fake driver
  session, and the temporary HMC bridge backend. The old shared
  exception-only chat validator has been removed from `driver-api`.
- [x] Invocation results validate against graph-projected result schemas and
  publish correlated SSE events for generic graph invocations. Current evidence
  covers schema validation, session events, JSON-RPC correlation ids, and
  operation-typed SSE payloads.

Verification:

- `mise exec -- gradle :driver-api:test :driver-fabric:test :daemon:test`
- `mise exec -- gradle :driver-fabric:test --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricDriverModuleTest.fabric backend reports missing player chat message as action failure' --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricDriverModuleTest.fabric backend reports blank player chat message as action failure' --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricDriverModuleTest.fabric backend rejects raw minecraft command strings as chat action input'`
- `mise exec -- gradle :testkit:test --tests 'com.minekube.craftless.testkit.FakeDriverSessionTest' :driver-runtime:test --tests 'com.minekube.craftless.driver.runtime.BackendDriverSessionTest.hmc bridge backend adapts the temporary bridge to runtime backend actions'`

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
- [~] Issues found during final gameplay are fixed and reverified. Final
  completion still requires refreshing the full Codex evidence gate after the
  latest changes.
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
  contained `Raw Beef` and `Leather`. The remaining final gates are Robin's
  held multiplayer observation, any fixes found there, and Robin's explicit
  Minecraft chat confirmation; those are tracked in Phase 7 and the final
  completion gate.
- [x] The final survival proof is reproduced by an external public-agent runner
  over generated OpenAPI/SSE/CLI/skills, not by hard-coding the scenario as a
  durable public `task.survival.*` API. The previous
  `missing-generic-primitive:world.block.query` blocker is resolved by Phase
  12; the latest public-agent evidence composes navigation, mining,
  inventory/crafting, combat, chat, and state/event verification through the
  public API. Robin-confirmed multiplayer completion remains open in Phase 7
  and the final completion gate.

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
  later public-agent and final gameplay evidence. Robin's in-game confirmation
  remains open in Phase 7 and the final completion gate.

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
  session used by daemon/CLI/testkit consumers, plus the temporary HMC bridge
  backend before bridge commands are invoked.

Verification:

- `mise exec -- gradle :driver-fabric:test --tests 'com.minekube.craftless.driver.fabric.v1_21_6.FabricDriverModuleTest.fabric backend returns machine readable movement failure before scheduling gateway'`
- `mise exec -- gradle :testkit:test --tests 'com.minekube.craftless.testkit.FakeDriverSessionTest'`
- `mise exec -- gradle :driver-runtime:test --tests 'com.minekube.craftless.driver.runtime.BackendDriverSessionTest.hmc bridge backend adapts the temporary bridge to runtime backend actions'`
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
  placement. The final project still remains open on broader survival
  acceptance and Robin confirmation, not on material equip.

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
  open on Robin's chat confirmation.

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
  is not final project completion because Robin-confirmed multiplayer gameplay
  is still outstanding.

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
  Current published release evidence: `v0.1.0` is published with
  `craftless-0.1.0.tar`, `craftless-0.1.0.zip`, and `SHA256SUMS`; the latest
  successful `release` workflow runs for tag `v0.1.0` completed on 2026-06-26.
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
  Refreshed 2026-06-28 evidence: running `install.sh` with temporary
  `CRAFTLESS_INSTALL_DIR` and `CRAFTLESS_HOME` installed `craftless 0.1.0`, and
  the installed binary returned `ok=true` for `server start --once --port 0`.
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
  public actions, SSE events, adaptive consumers, and Robin's Minecraft chat
  confirmation. Focused evidence now covers generated station-backed recipe
  composition without `craft.sword` or station shortcuts. Current live
  no-hold evidence covers public material pickup, station placement/opening,
  generic combat, and public combat loot pickup without `task.survival.*`.
  Current held evidence covers honest weapon acquisition/composition as
  required by the survival scenario. The legacy API removal phase is complete;
  Robin's held multiplayer observation, any fixes found there, and Robin's
  explicit Minecraft chat confirmation remain open in Phase 7 and the final
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
  `2026-06-27 18:44:16 CEST` without a Robin chat line containing
  `goal may be completed`, so final completion remains open.
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

## Final Completion Gate

- [~] All implementation phases above have current Phase 68 evidence; the
  broader project goal remains active until every generic-discovery,
  multi-version, transport, CLI, docs, and gameplay requirement is proven by a
  completion audit.
- [x] `mise run lint` passes. Current 2026-06-28 evidence: `mise run ci`
  completed lint successfully before this checklist update.
- [x] `mise run architecture-check` passes. Current 2026-06-28 evidence:
  `mise run architecture-check` completed successfully before this checklist
  update.
- [x] `mise run ci` passes. Current 2026-06-28 evidence: `mise run ci`
  completed successfully before this checklist update.
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
- [x] Latest and representative older-version compatibility probes have current
  evidence. Latest `26.2` and older `1.20.6` currently resolve as explicit
  unsupported lanes with current Mojang metadata and matrix/probe evidence.
- [x] Changes are committed and pushed to `main`. This entry is current only
  after the checklist update that changes it is also pushed.
