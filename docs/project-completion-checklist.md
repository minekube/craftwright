# Craftless Project Completion Checklist

This is the active completion red line. Craftless is not complete until every
unchecked item below is checked with evidence and Robin confirms completion in
Minecraft chat during the final gameplay session.

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

## Phase 1: Truth And Guardrails

- [x] `AGENTS.md`, roadmap, and this checklist define the real completion path.
- [x] Architecture checks fail if new public gameplay breadth appears only as a
  hand-written descriptor/binding pair.
- [x] README and docs no longer imply Craftless is complete before runtime
  graph, SSE stream, and final gameplay evidence are done.

Verification:

- `git diff --check`
- `rg -n "Craftless is complete|hand-written public gameplay|runtime capability graph|SSE|Robin.*Minecraft" AGENTS.md README.md docs -S`

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
- [x] Generated aliases, argument schemas, result schemas, resource metadata,
  handle metadata, event stream metadata, and fingerprints come from graph
  projection.
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
- [x] Invocation results validate against graph-projected result schemas and
  publish correlated SSE events for generic graph invocations. Current evidence
  covers schema validation, session events, JSON-RPC correlation ids, and
  operation-typed SSE payloads.

Verification:

- `mise exec -- gradle :driver-api:test :driver-fabric:test :daemon:test`

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
- [ ] Craftless joins a server, fetches graph-backed OpenAPI, subscribes to SSE,
  writes chat, observes world/inventory state, equips a tool, mines, places or
  builds a small structure, and records evidence.
- [ ] A public-agent gameplay runner uses only the generated per-client
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
  `player.look`, and `entity.attack`; it mined a log, recovered material into
  public inventory state, equipped it, placed/interacted with a block, attacked
  passive entities, and finally observed `entity.handle-11` with `alive:false`
  plus `Feather` and `Raw Chicken` drops. The final Robin-observed gameplay
  gate is still open.
- [ ] Robin joins or observes the server session after a macOS `say` prompt.
- [ ] Issues found during the gameplay session are fixed and reverified.
- [ ] Robin writes in Minecraft chat that the goal may be completed.

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
- [!] Craftless obtains weapon materials through ordinary survival gameplay,
  crafts or obtains a weapon legitimately, finds a cow, navigates to it without
  manual movement, kills it, writes chat, and records SSE/artifact evidence.
  Current evidence rejects the earlier false success and server-provisioned
  item path. The external public-agent no-hold smoke now proves ordinary
  material collection, placement, chat evidence, generated navigation, and
  generic combat outcome evidence without static scenario APIs; it still has
  not proven a legitimately crafted/obtained weapon, the exact cow/beef/leather
  acceptance variant, or Robin's in-game completion confirmation.
- [!] The final survival proof is reproduced by an external public-agent runner
  over generated OpenAPI/SSE/CLI/skills, not by hard-coding the scenario as a
  durable public `task.survival.*` API. The previous
  `missing-generic-primitive:world.block.query` blocker is resolved by Phase 12;
  remaining work is to compose navigation, mining, inventory/crafting, combat,
  chat, and state/event verification through the public API.

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
  `FabricActionBindings.kt` descriptor.
- [x] The public-agent no-hold gameplay run invokes `inventory.query`,
  `world.block.query`, and `entity.query` from the generated action catalog and
  records `publicAgentState=RAN` without calling `task.survival.*`.
- [x] A higher-level public agent policy starts using these generic primitives
  to query log material sources and derive navigation goals without adding
  scenario actions.
- [~] Public-agent policy continues from navigation into mining/collection,
  inventory/equip, placement, chat, and generic combat evidence. Crafting,
  exact final survival acceptance, and Robin's in-game confirmation remain
  open.

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

Verification:

- `mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest*'`
- `CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 mise exec -- gradle :driver-fabric:fabricFinalGameplay`

## Phase 14: Public-Agent Material Collection

- [x] Spec and plan exist for public-agent material collection without adding
  `mine.log`, `collect.wood`, `find.tree`, or any scenario-specific action.
- [x] Public-agent runner invokes `player.query`, derives `player.look` from
  public positions, invokes `player.raycast`, invokes `world.block.break`, and
  re-queries `inventory.query`.
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

- `mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest*'`
- `CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 mise exec -- gradle :driver-fabric:fabricFinalGameplay`

## Phase 15: Public-Agent Material Exploration

- [x] Spec and plan exist for bounded public-agent material exploration without
  adding `find.tree`, `mine.log`, `collect.wood`, `craft.sword`, `kill.cow`, or
  `task.survival.*`.
- [x] Public-agent runner retries material search by composing generated
  `player.query`, `navigation.plan`, `navigation.follow`, and
  `world.block.query` actions when the first local material query is empty.
- [~] Focused test evidence covers the empty-local-query exploration path. The
  latest live no-hold run did not need exploration because local
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
- [x] Live no-hold evidence shows targeted break data and collected inventory
  state. Current evidence: `world.block.query` selected
  `world.block:57:77:-292`; `world.block.break` returned the same handle and
  `inventory.query` showed `Oak Log`.

Verification:

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
- [x] Live no-hold evidence reaches `inventory.equip` for collected material
  and verifies follow-up `inventory.query` selected-slot state before
  placement. The final project still remains open on broader survival
  acceptance and Robin confirmation, not on material equip.

Verification:

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
- [x] Live no-hold evidence reaches inventory material proof after pickup
  movement/drop perception, including `inventory.query` with collected log
  material, followed by generated `inventory.equip`, selected-slot
  verification, placement, and combat progression. Focused regression evidence
  now covers continuing bounded material exploration when navigation to an
  initially discovered material target fails.

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
- [x] Live no-hold evidence runs `entity.query` after material break, uses
  public drop perception when block-target pickup navigation is insufficient,
  and reaches inventory material proof without a pickup shortcut.

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
- [x] Live no-hold evidence progressed through the material path without the
  previous exploration timeout, with `world.block.query`,
  `navigation.plan/follow`, `world.block.break`, `entity.query`,
  `inventory.query`, `inventory.equip`, selected-slot verification,
  `world.block.interact`, and generic combat progression. Focused regression
  evidence covers retrying material exploration when a discovered target is not
  navigable.

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
- [x] Focused driver and public-agent tests pass.
- [x] Recent live no-hold evidence reaches generic attack invocation through
  generated `entity.attack` after material pickup and placement. Focused
  public-agent tests require post-attack entity or inventory proof, bounded
  generated-navigation exploration when no target is visible, vertically
  reachable target preference, fresh close-range target revalidation before
  attack, a bounded pause between unproven attacks, and target refresh between
  unproven attacks. Latest live no-hold evidence records generated combat
  outcome proof: `entity.attack` hit a passive entity, a follow-up
  `entity.query` reported `entity.handle-11` with `alive:false`, and public
  entity perception showed `Feather` and `Raw Chicken` drops. The final
  project remains open on exact final survival acceptance and Robin
  confirmation.

Verification:

- `mise exec -- gradle :driver-fabric:test --tests '*FabricCapabilityProbeTest*' --tests '*FabricDriverModuleTest.fabric backend invokes entity attack through runtime graph adapter'`
- `mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest*'`

## Phase 24: Targetable Block Interact

- [x] Spec and plan exist for targetable generic block interaction without
  adding `build.house`, `place.log`, structure macros, or other scenario
  shortcuts.
- [x] `world.block.interact` accepts public block handles or positions plus a
  side, invokes the Fabric client-thread interaction manager, and returns
  `accepted` plus state-change evidence.
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
- `mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest*'`
- `CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS=90000 CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS=120000 mise exec -- gradle :driver-fabric:fabricFinalGameplay`

## Phase 25: Distribution Usability

- [x] Spec and plan exist for release, install, Docker, GitHub Action, and
  README quickstart surfaces.
- [x] Release workflow builds the CLI distribution with mise/Gradle, uploads
  GitHub Release artifacts and checksums, and pushes a GHCR runtime image.
- [x] Docker image copies an already-built Craftless CLI distribution and does
  not build the project inside Docker.
- [x] Install script installs `craftless` from GitHub Releases without
  requiring users to clone this repository.
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
  plus at least one simulated or additional lane.
  Evidence:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricCompatibilityMatrixTest*' --tests '*FabricCurrentLaneRuntimeProviderTest*'`
  and `mise exec -- gradle :testkit:test --tests '*LocalMinecraftServerSmokeTest*'`.
- [x] Runtime probe metadata records version/provider support and unavailable
  reasons without leaking Fabric/Yarn/Minecraft names into public API.
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
- [~] Runtime graph discovery declares the Craftless-owned `recipe` resource,
  `recipe.handle`, `recipe.query`, and `recipe.craft`. `recipe.query`,
  `recipe`, and `recipe.handle` become available only when the live client
  reports recipe-book state; otherwise they remain unavailable with
  `recipe-discovery-unavailable`. `recipe.craft` becomes available only when
  the live client also reports a craft execution context; otherwise it remains
  unavailable with `recipe-context-unavailable` or another machine-readable
  runtime reason.
- [~] `recipe.query` has a guarded Fabric operation adapter that projects live
  recipe-book display entries into opaque Craftless recipe handles, public
  output/ingredient labels, categories, craftability, and query filters.
  Broader live recipe requirements, screen/handler permission details, stale
  handle validation, and real craft execution remain open.
- [~] `recipe.craft` has public handle/count validation, stale-handle
  validation, live craftability checks, guarded Fabric client-thread execution
  through `clickRecipe`, and before/after inventory fingerprints. Broader
  screen/handler coverage, asynchronous post-server inventory confirmation,
  and live survival evidence remain open.
- [x] Public-agent composition uses generated recipe actions when available to
  craft useful outputs, then verifies inventory state through `inventory.query`
  in focused fake-server evidence.
- [ ] Live no-hold final gameplay evidence shows generic recipe/crafting
  progress without `craft.sword`, `craft.planks`, `craft.table`,
  `make.weapon`, `kill.cow`, or `task.survival.*`.

Verification:

- `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest*'`
- `mise exec -- gradle :driver-fabric:test --tests '*recipe*'`
- `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric backend crafts a discovered recipe handle through runtime graph adapter*'`
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
- [ ] Final live gameplay still must prove the scenario through generated
  public actions, SSE events, adaptive consumers, and Robin's Minecraft chat
  confirmation.

Verification:

- `mise exec -- gradle :driver-fabric:test --tests '*FabricNavigationDiscoveryTest.fabric backend task adapter refuses legacy survival tasks*'`
- `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest*' --tests '*FabricNavigationDiscoveryTest*'`
- `mise run ci`

## Final Completion Gate

- [ ] All phases above are checked with current evidence.
- [ ] `mise run lint` passes.
- [ ] `mise run architecture-check` passes.
- [ ] `mise run ci` passes.
- [ ] Final real gameplay evidence is captured without server-provisioned
  inventory or manual movement for Craftless.
- [ ] Robin confirms in Minecraft chat that the goal may be completed.
- [ ] Changes are committed and pushed to `main`.
