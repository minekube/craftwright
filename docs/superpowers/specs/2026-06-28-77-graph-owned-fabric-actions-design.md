# Phase 77: Graph-Owned Fabric Actions Design

## Goal

Make Fabric's public action descriptors come from the runtime capability graph
instead of from transitional `FabricActionBinding` descriptors.

## Context

Phase 76 proved the remaining completion blocker: Fabric still exposes public
gameplay breadth from a hand-written binding allowlist. The generated
per-client OpenAPI already prefers `RuntimeCapabilityGraph`, and the daemon
already invokes generated operations through `DriverOperationAdapters` before
falling back to legacy `invoke(...)`. The next aligned slice is to make the
Fabric driver API's `actions()` projection graph-owned as well.

This phase does not remove the transitional execution bindings. It demotes
them to private adapters used to execute graph-discovered operations while the
broader reflection/mapping/registry/callback/screen/handler discovery work
continues.

## Requirements

- `FabricDriverBackend.actions(clientId)` must project from
  `runtimeGraph(clientId).operations`.
- Public Fabric action descriptors returned by `actions()` must use
  `DriverActionSource.RUNTIME_PROBE`, not `DriverActionSource.BINDING`, even
  when a private binding adapter can execute the operation.
- Projection must preserve graph operation id, argument schemas, result
  schemas, availability, and availability reason.
- Existing private execution behavior must keep working for the current public
  gameplay evidence path.
- `DriverActionSource.BINDING` must not be the source of public Fabric gameplay
  action descriptors after this phase.
- The change must not add public gameplay actions, generated route families,
  static CLI catalogs, Fabric descriptor/binding pairs, scenario shortcuts, new
  compiled lanes, public version-specific APIs, or new Minecraft support
  claims.

## Non-Goals

- Do not remove `FabricActionBinding` in this phase.
- Do not remove `FabricActionDiscovery` in this phase.
- Do not implement broad Fabric API reflection discovery in this phase.
- Do not change final gameplay policy or add survival-specific product
  behavior.

## Verification

- Focused red/green test:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric public actions are projected from runtime graph instead of binding descriptors*'`
- Focused regression test:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric runtime discovery probes client state before advertising unavailable raycast*' --tests '*FabricDriverModuleTest.fabric runtime discovery exposes player query only from client state*' --tests '*FabricDriverModuleTest.fabric runtime discovery exposes inventory equip only from client state*'`
- `git diff --check`
- `mise run architecture-check`
- `mise run ci`
- GitHub Actions CI for pushed `main`
