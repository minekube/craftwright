# Navigation Operation Id Source Ownership Design

## Problem

Navigation and task operation ids are still repeated outside their discovery
source. `FabricNavigationDiscovery` defines the graph operations, while
`FabricDriverBackend` dispatches on raw strings such as `navigation.plan`, and
`FabricClientSmokeController` hard-codes navigation action ids in its public
agent readiness gate.

That keeps another small static catalog alive. Navigation ids should be owned
by the Fabric navigation discovery layer and consumed from constants wherever
backend dispatch or evidence harnesses need to refer to those current
transitional operations.

## Goals

- Introduce internal navigation/task operation-id constants in
  `FabricNavigationDiscovery.kt`.
- Use those constants in navigation graph discovery, backend dispatch, and the
  Fabric smoke public-agent required-action gate.
- Add a source guard that fails if backend or smoke code reintroduces duplicated
  quoted navigation/task operation-id literals.
- Keep operation ids, adapter keys, generated OpenAPI, and invocation behavior
  unchanged.

## Non-Goals

- Do not add gameplay actions, route families, CLI commands, generated aliases,
  Fabric execution adapters, version lanes, or support claims.
- Do not remove navigation or task adapters in this phase.
- Do not change pathfinder plan id formats such as
  `navigation.plan.reflective.0001`.
- Do not claim the broader generated-discovery exit is complete.

## Acceptance Criteria

- `FabricDriverBackend.kt` no longer contains quoted `navigation.plan`,
  `navigation.follow`, `navigation.stop`, `task.run`, or `task.status` literals.
- `FabricClientSmokeController.kt` no longer contains quoted `navigation.plan`
  or `navigation.follow` literals.
- Navigation discovery remains the source for those operation ids.
- Existing navigation/graph invocation tests still pass.
- AGENTS, checklist, plan, and evidence record Phase 89 and keep the broader
  generated-discovery blocker active.
