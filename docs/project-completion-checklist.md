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
  existing action dispatch, generated help, tools export, resources, and cache
  revalidation.
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

- [~] Fabric probes fill graph nodes from loader/mod metadata, registries,
  callbacks, screens, handlers, world/entity/inventory/client state, and
  permissions.
- [x] Minecraft client state access stays on the client thread.
- [x] Probes emit private evidence and graph nodes, not public OpenAPI
  descriptors directly.
- [x] Current bootstrap gameplay affordances are represented as graph nodes.

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
- [~] Unavailable, permission, schema, stale-handle, and runtime-mismatch
  errors are machine-readable. Current evidence covers unavailable and schema
  failures; permission, stale-handle, and runtime-mismatch codes remain.
- [x] Invocation results validate against graph-projected result schemas and
  publish correlated SSE events for generic graph invocations. Current evidence
  covers schema validation, session events, JSON-RPC correlation ids, and
  operation-typed SSE payloads.

Verification:

- `mise exec -- gradle :driver-api:test :driver-fabric:test :daemon:test`

## Phase 6: SSE, JSON-RPC, And Adaptive Consumers

- [x] Daemon exposes SSE streams for supervisor and per-client live events.
- [~] Daemon exposes HTTP POST JSON-RPC-style control for invoke, subscribe,
  unsubscribe, and query. Current evidence covers invoke correlation and
  generic acknowledgements for the non-invoke methods; persistent subscription
  state remains future hardening.
- [x] Event filters work server-side and client-side.
- [~] CLI can watch live events and invoke/query using live OpenAPI/stream
  metadata. Current evidence covers live event watching and invocation; query
  remains a generic RPC follow-up.
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
- [ ] Craftless obtains weapon materials through ordinary survival gameplay,
  crafts or obtains a weapon legitimately, finds a cow, navigates to it without
  manual movement, kills it, writes chat, and records SSE/artifact evidence.

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
