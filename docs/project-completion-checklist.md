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
  daemon URL into that external runner while the client is connected; completing
  the live survival proof through generated primitives is still open. Latest
  no-hold live evidence shows the public agent progressed through
  `inventory.query`, `world.block.query`, and `entity.query`; the diagnostic
  internal survival harness still fails before material collection is complete.
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
- [x] Internal honest survival task graph describes observation, navigation,
  collection, inventory, crafting, entity observation, and combat steps without
  server shortcuts or public static gameplay actions.
- [!] Craftless obtains weapon materials through ordinary survival gameplay,
  crafts or obtains a weapon legitimately, finds a cow, navigates to it without
  manual movement, kills it, writes chat, and records SSE/artifact evidence.
  Current evidence rejects the earlier false success: stricter live runs now
  require inventory proof and currently fail at `material-collect-timeout`, with
  post-task `inventory.query` showing no collected logs, weapon, beef, or
  leather.
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
- [ ] Public-agent policy continues from navigation into mining/collection,
  inventory/crafting/equip, combat, chat, and final survival evidence with
  Robin.

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
- [!] Live no-hold evidence is blocked before equip by
  later survival primitives, not by material equip. Current live no-hold
  evidence reaches `inventory.equip` for collected `Oak Log` in slot 1 and
  verifies follow-up `inventory.query` with `selected-slot = 1`.

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
- [!] Inventory proof after the changed block is still blocked by public pickup
  and later survival breadth in some seeds; the generic block break primitive
  itself is no longer the current blocker. Current live no-hold evidence shows
  material proof and equip after a changed block path.

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
  movement. Current evidence: `inventory.query` shows `Oak Log` count 2 in slot
  1 after `world.block.break` and pickup navigation.

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
- [x] Live no-hold evidence runs `entity.query` after the material break and
  pickup movement, then verifies material inventory and equip state. Current
  run did not need material-drop navigation because inventory pickup succeeded
  before a matching log drop entity was observed.

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
  previous exploration timeout. Current public-agent state is `RAN`, with
  `world.block.query`, `navigation.plan/follow`, `world.block.break`,
  `entity.query`, `inventory.query`, `inventory.equip`, and selected-slot
  verification.

Verification:

- `mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest*'`
- `CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS=90000 CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS=120000 mise exec -- gradle :driver-fabric:fabricFinalGameplay`

Verification:

- `mise exec -- gradle :protocol:test :driver-api:test :driver-fabric:test`
- `mise run architecture-check`
- No-cheat live gameplay run with empty or ordinary survival inventory.

## Final Completion Gate

- [ ] All phases above are checked with current evidence.
- [ ] `mise run lint` passes.
- [ ] `mise run architecture-check` passes.
- [ ] `mise run ci` passes.
- [ ] Final real gameplay evidence is captured without server-provisioned
  inventory or manual movement for Craftless.
- [ ] Robin confirms in Minecraft chat that the goal may be completed.
- [ ] Changes are committed and pushed to `main`.
