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
Continue by moving official 26.x support through shared Fabric
discovery/projection/invocation, packaging, adaptive CLI/API smoke, and honest
gameplay evidence. Do not copy the Yarn/remap gameplay gateway into the
official module and do not add static gameplay catalogs.
