# Craftless Phase Index

This file is the maintained phase-history index for Craftless. Keep root
`AGENTS.md` stable: it should describe durable repository rules and point here,
not grow with every completed phase.

## Maintenance Rules

- Add each new phase here when creating its spec and plan.
- Keep status and detailed verification evidence in
  `docs/project-completion-checklist.md`.
- Keep design and implementation details in `docs/superpowers/specs/` and
  `docs/superpowers/plans/`.
- Keep evidence in `docs/superpowers/evidence/`.
- Do not use this file to justify static gameplay APIs. Phase entries must keep
  the same system-level boundary: generated runtime graph/OpenAPI first,
  narrow lane adapters only for proven divergence, no scenario shortcuts.

## Current Phase Tail

- Phase 136: reflective movement input shim.
- Phase 137: reflective recipe bridge.
- Phase 138: packaged representative older Fabric lane.
- Phase 139: packaged older Fabric lane selection smoke.
- Phase 140: parameterized Fabric smoke client command.
- Phase 141: representative older Fabric real-client smoke.
- Phase 142: installed packaged older Fabric live attach.
- Phase 143: installed latest-release alias compatibility probe.
- Phase 144: latest driver lane preflight.
- Phase 145: latest official-mapping lane probe.
- Phase 146: latest official Fabric lane boundary.
- Phase 147: shared Fabric attach boundary.
- Phase 148: official Fabric runtime dependency packaging.
- Phase 149: official Fabric launch attach probe.
- Phase 150: official Fabric runtime metadata discovery.
- Phase 151: shared Fabric runtime metadata discovery.
- Phase 152: shared Fabric runtime resource projection.
- Phase 153: shared Fabric runtime graph composition.
- Phase 154: shared Fabric registry graph projection.
- Phase 155: shared Fabric event graph projection.
- Phase 156: shared Fabric client-state graph projection.
- Phase 157: official Fabric live client-state probe.
- Phase 158: official Fabric connected client-state probe.
- Phase 159: official Fabric connected server-feature metadata.
- Phase 160: official Fabric registry metadata probe.
- Phase 161: official Fabric event-source metadata.
- Phase 162: official Fabric connected SSE evidence.
- Phase 163: official Fabric public projection endpoints.
- Phase 164: official Fabric JSON-RPC query evidence.
- Phase 165: official Fabric JSON-RPC subscription SSE evidence.
- Phase 166: runtime graph default action projection.
- Phase 167: backend runtime graph action default.
- Phase 168: OpenAPI route authority.
- Phase 169: public-agent OpenAPI action authority.
- Phase 170: active docs and agent onboarding alignment.
- Phase 171: daemon OpenAPI graph-only authority.
- Phase 172: remote driver action graph authority.
- Phase 173: public-agent actions projection optional.
- Phase 174: Fabric execution adapter naming.
- Phase 175: bootstrap resource derivation.
- Phase 176: bootstrap adapter key separation.
- Phase 177: client-state operation discovery.
- Phase 178: static gameplay guard closure.
- Phase 179: official client-state world time operation.
- Phase 180: official world time invocation.
- Phase 181: packaged official latest Fabric lane.
- Phase 182: packaged latest-current attach artifacts.
- Phase 183: latest-current generated primitive smoke.
- Phase 184: representative older product lane.
- Phase 185: user-facing usability docs.
- Phase 186: final local release gates.
- Phase 187: final public gameplay.
- Phase 188: tiny-agent lifecycle defaults.

## Current Direction

The latest/current official lane has launch, attach, connected client-state,
connected server-feature, registry, event-source metadata, public SSE
lifecycle, projection endpoint, JSON-RPC query, and JSON-RPC subscription
filter evidence.
The shared JVM driver contract now derives `DriverSession.actions()` from
runtime graph operations by default, so graph-backed sessions do not need a
second hand-written action-list projection path.
The shared backend contract now also defaults `DriverBackend.actions(clientId)`
to runtime graph projection, which removes another empty-list fallback and
lets Fabric use shared runtime behavior instead of a duplicate override.
Client-specific route projections now derive from the generated per-client
OpenAPI document, so graph-backed sessions no longer need a separate action
list merely to expose alias routes through `routesFor(clientId)`.
The public-agent gameplay runner now uses generated per-client OpenAPI
`x-craftless-actions` as its action metadata authority, with `/actions`
remaining a projection artifact instead of the agent workflow source of truth.
Active README, roadmap/checklist, and repo-local public gameplay agent skill
docs now describe the same external-agent workflow: generated per-client
OpenAPI is authority; `/actions` and `/resources` are projection evidence;
SSE/public state proves results; scenario actions, internals, and
server-provisioned inventory do not count as product proof.
Daemon per-client OpenAPI generation now always comes from
`DriverSession.runtimeGraph()`. Descriptor-only `DriverSession.actions()`
cannot publish public OpenAPI actions, resources, alias routes, CLI metadata,
or agent workflow metadata when the runtime graph is empty.
Remote attached `HttpDriverSession.actions()` now uses the shared
runtime-graph projection default instead of fetching a separate loopback
`/actions` endpoint.
The public-agent runner treats `/clients/{id}/actions` as optional projection
evidence; generated per-client OpenAPI `x-craftless-actions` remains the
gameplay authority even when the projection endpoint is absent.
The private Fabric execution layer no longer uses stale `FabricActionBinding`
naming; these classes are execution adapters, while CL-02 remains open for the
real bootstrap operation-definition exit.
Transitional Fabric bootstrap operation definitions no longer hand-maintain
public resource ownership. Runtime operation resources are derived from
operation ids, and Fabric client-state discovery now emits the derived
`world.block` and `world.time` resources needed by graph validation.
Private Fabric adapter keys are no longer stored inside bootstrap operation
definitions. A separate private operation-id to adapter-key map now feeds both
runtime graph projection and backend execution-adapter registration.
`world.time.query` now comes from Fabric client-state discovery instead of the
bootstrap operation definition list. The node carries `client-state` source
evidence while the private execution adapter remains unchanged.
CL-02 is now closed in the active completion checklist. CL-02f has concrete
policy coverage for static gameplay regressions:
private Fabric execution adapters cannot own public operation id literals or
schemas, production CLI/daemon sources cannot own static gameplay catalogs or
alias route families, scenario shortcut ids remain rejected, and daemon event
normalization cannot synthesize gameplay action ids.
The official 26.x/latest-current lane now projects the existing
`world.time.query` operation from shared Fabric client-state discovery, so it
no longer reports zero runtime operations when a world is observed.
The official lane now also invokes generated `world.time.query` through an
internal 26.x world-time provider and private adapter-key dispatch.
The packaged CLI/Docker distribution now includes a `26.2` official Fabric
driver manifest entry and staged
`mods/fabric-26.2/craftless-driver-fabric-official.jar`, built through the
Java 25 mise runtime. This removes the previous packaged manifest miss for
`latest-release` resolving to `26.2`.
The packaged latest-current probe now resolves `latest-release` to Minecraft
`26.2`, selects a mise-managed Java 25 runtime, creates and attaches the
official Fabric client through the packaged CLI/supervisor API, connects to a
real local Minecraft server, and captures connected generated OpenAPI,
actions/resources, SSE, JSON-RPC query, and JSON-RPC subscription artifacts.
The native launch bug was fixed in shared cache preparation by using one
aggregate native directory for `${natives_directory}` while keeping classpath
and library artifacts separate.
CL-03 is now closed. The packaged latest-current probe selects
`world.time.query` from live per-client OpenAPI `x-craftless-actions`, invokes
it through public JSON-RPC `method=invoke`, invokes it again through the
adaptive packaged CLI, and captures both transcripts with `ACCEPTED` results.
CL-04 is now closed. The packaged representative older probe creates,
attaches, and connects Minecraft `1.20.6` through the packaged CLI/supervisor
path, captures generated OpenAPI, projections, SSE, JSON-RPC query and
subscription artifacts, then selects generated non-`task.*` `entity.query`
from live `x-craftless-actions` and invokes it through both JSON-RPC and the
adaptive packaged CLI with `ACCEPTED` results.
CL-05 is now closed. External-user usability evidence proves install script
smoke, Docker runtime smoke, adaptive CLI group help, README/roadmap freshness,
stale wording guards, reusable GitHub Action documentation, and agent onboarding
without static gameplay SDK wording or scenario shortcuts.
Continue with CL-06: final local release-quality gates must rerun lint,
architecture checks, CI, packaging, Docker/install smokes, latest/current
packaged lane probe, representative older packaged lane probe, and
`git diff --check` from the current worktree before any final gameplay claim.
CL-06 is now closed. Local release gates passed after CL-05, including lint,
architecture checks, CI, package build, Docker smoke, install smoke,
latest/current packaged lane probe, representative older packaged lane probe,
and `git diff --check`.
CL-07 is now closed. The final packaged public gameplay probe drives a real
Minecraft `1.21.6` client through generated per-client OpenAPI and JSON-RPC
invoke only, with server provisioning disabled. It sends chat, discovers and
collects a runtime log, crafts and equips planks from a discovered live recipe,
uses a plank through `world.block.interact`, selects a same-level runtime entity
from public player/entity positions, navigates to it, and attacks it with
`hit:true`. The probe keeps `/actions` and `/resources` as projection evidence,
rejects `task.*`/scenario shortcuts, and records final evidence in
`docs/superpowers/evidence/2026-06-28-final-public-gameplay.md`.
Continue with CL-08: rerun focused final guards after the CL-07 edits, then
commit, push `main`, and verify the worktree is clean.
Phase 188 is a post-completion usability slice. Client creation now accepts an
omitted profile, derives a safe offline name from the client id, records
presentation intent on client responses, defaults API-first clients to
non-visible muted presentation, and exposes only concrete lifecycle CLI flags
such as `--visible` and `--audio`. This does not add gameplay roles, static
gameplay catalogs, scenario shortcuts, or generated-action alternatives.
