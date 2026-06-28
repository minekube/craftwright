# Runtime Graph Default Action Projection Design

## Goal

Make the stable JVM driver contract derive `DriverSession.actions()` from
`runtimeGraph().operations` by default, using one shared driver-api projection
helper instead of duplicate per-driver action-list conversions.

## Problem

Craftless already treats the runtime capability graph as the source of truth
for generated per-client OpenAPI. Several driver/test implementations still
override `actions()` and manually map runtime operations into
`DriverActionDescriptor`s. That keeps a stale action-list seam alive and
duplicates graph-to-action schema conversion.

This does not by itself add latest/current official gameplay support, but it
removes a structural reason for future agents to add separate action catalogs
instead of runtime graph operations.

## Design

- Add shared driver-api projection helpers:
  - `RuntimeOperationNode.toDriverActionDescriptor()`
  - `RuntimeSchema.toDriverActionArgument()`
  - `RuntimeSchema.toDriverActionResultDescriptor()`
  - `RuntimeSchema.toDriverActionResultProperty()`
  - `RuntimeAvailability.toDriverActionAvailability()`
- Make `DriverSession.actions()` default to sorted
  `runtimeGraph().operations.map { it.toDriverActionDescriptor() }`.
- Remove local duplicate conversions and redundant `actions()` overrides where
  the runtime graph is already authoritative.
- Keep `HttpDriverSession.actions()` as an override because it is an external
  attached-driver protocol call.
- Do not add any runtime operations, gameplay action ids, adapters, CLI
  commands, route families, or scenario shortcuts.

## Boundaries

- No new gameplay action.
- No new public route.
- No static action catalog.
- No official 26.x support claim.
- No change to generated OpenAPI semantics except eliminating duplicate local
  conversion paths behind the driver contract.

## Acceptance

- A red driver-api test fails before implementation because a minimal
  `DriverSession` with only `runtimeGraph()` does not get default action
  descriptors.
- The driver-api test passes after implementation and verifies:
  - action id comes from `RuntimeOperationNode.id`;
  - source is `RUNTIME_PROBE`;
  - unavailable runtime availability maps to `UNAVAILABLE` plus reason;
  - nested argument/result schemas are preserved.
- Duplicate conversion helpers are removed from Fabric and testkit where the
  shared helper can be used.
- Focused `driver-api`, `testkit`, Fabric latest-official checks, local CI, and
  `git diff --check` pass through mise.
