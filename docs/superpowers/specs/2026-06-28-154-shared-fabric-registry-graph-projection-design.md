# Shared Fabric Registry Graph Projection Design

## Problem

The latest/current official Fabric lane now shares runtime metadata discovery,
runtime resource projection, and runtime graph composition with the Yarn/remap
lane. Its generated per-client OpenAPI still reports only the `runtime`
resource because registry graph projection remains embedded in the Yarn/remap
capability probe.

Registry status is not a gameplay action and is not version-specific by
itself. The durable system needs shared graph projection for registry evidence
so every Fabric lane can expose whether registry discovery is available,
unavailable, or still not connected without copying a per-version graph
builder.

## Goal

Move Fabric registry graph projection into `driver-fabric-discovery`:

- a shared `fabricRegistryGraphFragment(metadata, available)` helper;
- shared Craftless-owned registry handle ids for the existing registry
  summary surface;
- availability derived from the lane's actual registry fingerprint/status;
- Yarn/remap lane using the shared helper instead of hand-building the
  registry resource and handles;
- official lane composing runtime metadata plus registry graph fragments
  through the shared graph composer.

The official lane may expose an unavailable `registry` resource when it has
not discovered registries yet. That is progress because the generated OpenAPI
now communicates the missing capability as runtime graph evidence instead of
omitting the resource entirely.

## Non-Goals

- Do not add public gameplay actions.
- Do not add static action descriptors or action catalogs.
- Do not package or claim the `26.2` official lane as supported.
- Do not move Minecraft game-class registry inspection into
  `driver-fabric-discovery`.
- Do not expose raw Minecraft/Fabric registry names as public API.
- Do not claim latest/current gameplay support.

## Design

Create `FabricRegistryGraph.kt` in `driver-fabric-discovery`.

The helper should return a `FabricRuntimeGraphFragment` containing:

- resource `registry`;
- handles `registry.block`, `registry.item`, `registry.entity`,
  `registry.screen`, `registry.effect`, and `registry.event`;
- `RuntimeSourceEvidence("registry", metadata.registryFingerprint)`;
- `RuntimeAvailability.available()` only when the caller marks registry
  discovery available and the metadata fingerprint is not a known
  not-discovered marker;
- `RuntimeAvailability.unavailable("registry-not-discovered")` otherwise.

`driver-fabric` should keep Minecraft game-class registry inspection and
fingerprint production in its lane. The remap registry probe should call the
shared helper with `available = true`.

`driver-fabric-official` should continue to be metadata-only for gameplay, but
its `runtimeGraph` should compose:

1. the shared runtime metadata fragment;
2. the shared registry graph fragment with `available = false`.

That should increase official generated OpenAPI resources from `1` to `2`
while actions remain `0`.

## Acceptance

- Shared tests prove the registry fragment emits resource `registry` and
  handles `registry.block`, `registry.item`, `registry.entity`,
  `registry.screen`, `registry.effect`, and `registry.event` when
  metadata says registries are available.
- Shared tests prove the registry fragment marks resource and handles
  unavailable with reason `registry-not-discovered` when metadata says
  `registries:not-discovered`.
- Architecture guard proves the Yarn/remap lane no longer hand-builds
  `RuntimeResourceNode(id = "registry")`.
- Architecture guard proves the official backend uses
  `fabricRegistryGraphFragment` and still does not import
  `RuntimeCapabilityGraph`.
- Focused Fabric/discovery/official tests pass.
- The real enabled official attach probe still observes `client.attached`.
- The official OpenAPI artifact reports `actions=0` and `resources=2`, with
  registry availability unavailable rather than a gameplay support claim.
- No public gameplay action, packaged 26.x manifest entry, static gameplay
  catalog, scenario shortcut, or latest/current support claim is added.
