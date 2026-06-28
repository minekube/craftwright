# Phase 79: Graph-Owned Fabric Invoke Design

## Goal

Make Fabric's generic `invoke(...)` compatibility path dispatch through runtime graph
operations and private operation adapters instead of through
`FabricActionDiscovery`.

## Context

Phases 77 and 78 moved public action descriptors and current bootstrap
operation schemas to `RuntimeCapabilityGraph.operations`. However,
`FabricDriverBackend.invoke(...)` still looks up `discoveredActions(clientId)`
and invokes the returned binding directly. That leaves a public-compatible
invocation path coupled to the transitional action discovery descriptor path.

Generated per-client OpenAPI already prefers `operationAdapters()` when an
operation is present in the runtime graph. This phase aligns the older
`DriverSession.invoke(...)` compatibility path with the same graph-owned
source of truth.

## Requirements

- `FabricDriverBackend.invoke(clientId, invocation)` must find the operation
  in `runtimeGraph(clientId).operations`.
- If the graph operation is missing, return `UNSUPPORTED` with the existing
  unsupported-action style message.
- If the graph operation is unavailable, return `UNSUPPORTED` with the graph
  availability reason.
- If the graph operation is available and an operation adapter exists, dispatch
  via `operationAdapters(clientId).invoke(DriverOperationInvocation(...))`.
- If the graph operation is available but no adapter exists, return
  `UNSUPPORTED` using the operation adapter id as evidence.
- Injected or default `FabricActionDiscovery` must not control generic
  `invoke(...)` success, availability, or unsupported behavior.
- Transitional `FabricActionBinding` implementations may remain private
  operation-adapter implementations.
- The change must not add public gameplay actions, generated route families,
  static CLI catalogs, Fabric descriptor/binding pairs, scenario shortcuts, new
  compiled lanes, public version-specific APIs, or new Minecraft support
  claims.

## Non-Goals

- Do not remove `FabricActionBinding` entirely.
- Do not remove `FabricActionDiscovery` entirely in this phase.
- Do not change generated OpenAPI route generation.
- Do not implement broad Fabric API reflection discovery in this phase.
- Do not add gameplay affordances or survival-specific behavior.

## Verification

- Red/green focused test:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric backend dispatch does not depend on fabric action discovery*' --tests '*FabricDriverModuleTest.fabric compatibility invoke dispatches unavailable operations from runtime graph*' --tests '*FabricDriverModuleTest.fabric compatibility invoke adapters come from private binding map*'`
- Focused unavailable regression:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric runtime discovery probes client state before advertising unavailable raycast*'`
- Full Fabric regression:
  `mise exec -- gradle :driver-fabric:test`
- `git diff --check`
- `mise run architecture-check`
- `mise run ci`
- GitHub Actions CI for pushed `main`
