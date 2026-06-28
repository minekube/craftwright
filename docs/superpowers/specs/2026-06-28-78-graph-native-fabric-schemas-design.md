# Phase 78: Graph-Native Fabric Schemas Design

## Goal

Make Fabric runtime graph operation schemas owned by the graph discovery layer,
not by transitional `FabricActionBinding` descriptors.

## Context

Phase 77 made `FabricDriverBackend.actions(clientId)` project public action
descriptors from `RuntimeCapabilityGraph.operations`. That removed bindings as
the public descriptor source, but the Fabric client-state probe still builds
some operation argument/result schemas by looking up `FabricActionBinding`
descriptors or bootstrap descriptor functions. That keeps public schemas coupled
to the transitional binding catalog.

This phase cuts that coupling. Runtime graph probes may still list the current
bootstrap operations while broader reflection, mappings, registries, screens,
handlers, callbacks, permissions, and mod discovery mature, but the graph
schema shape must be represented as `RuntimeSchema` in the graph layer.
Bindings remain private execution adapters only.

## Requirements

- `FabricCapabilityProbeContext` must not accept or retain
  `FabricActionBinding` maps for schema construction.
- `FabricClientStateCapabilityProbe` must create operation argument/result
  schemas from graph-local `RuntimeSchema` helpers.
- `player.chat` and `player.move` schemas must remain present after removing
  the binding descriptor fallback.
- Existing graph operation ids, adapters, availability, and generated action
  projection behavior must remain stable.
- Transitional execution bindings may remain private adapters for
  `DriverOperationAdapters`.
- The change must not add public gameplay actions, generated route families,
  static CLI catalogs, Fabric descriptor/binding pairs, scenario shortcuts, new
  compiled lanes, public version-specific APIs, or new Minecraft support
  claims.

## Non-Goals

- Do not remove `FabricActionBinding` entirely.
- Do not remove `FabricActionDiscovery` entirely.
- Do not implement broad Fabric API reflection discovery in this phase.
- Do not add new gameplay affordances or survival-specific behavior.
- Do not change the supervisor OpenAPI, generated invocation path, or final
  gameplay policy.

## Verification

- Red/green focused test:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricCapabilityProbeTest.fabric capability probe context does not receive action bindings for graph schemas*'`
- Focused graph schema regression:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricCapabilityProbeTest.fabric graph schemas stay available without binding descriptor fallback*'`
- Full Fabric regression:
  `mise exec -- gradle :driver-fabric:test`
- `git diff --check`
- `mise run architecture-check`
- `mise run ci`
- GitHub Actions CI for pushed `main`
