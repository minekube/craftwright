# Shared Fabric Runtime Graph Composition Design

## Problem

The latest/current official lane and the Yarn/remap lane now share Fabric
runtime metadata and runtime resource projection. The next reusable boundary is
graph composition itself.

Today `driver-fabric` owns `FabricCapabilityGraphFragment` and the fragment
merge logic that builds a `RuntimeCapabilityGraph`. The official lane still
constructs its metadata-only graph directly. This keeps the official lane on a
parallel graph assembly path even though graph fragments and graph merging are
not Minecraft-version-specific.

## Goal

Move generic Fabric runtime graph fragment composition into
`driver-fabric-discovery`:

- a shared `FabricRuntimeGraphFragment` type;
- a shared `fabricRuntimeGraph(clientId, fragments)` composer;
- a shared `fabricRuntimeMetadataGraph(clientId, metadata, sourceEvidence)`
  convenience function for metadata-only lanes.

`driver-fabric` should keep its lane-specific probe interfaces, gateway,
registry probes, client-state probes, and operation adapters, but its default
discovery should merge fragments through the shared composer.

`driver-fabric-official` should use the shared metadata graph helper instead of
constructing `RuntimeCapabilityGraph` directly.

## Non-Goals

- Do not add public gameplay actions.
- Do not add static action descriptors or action catalogs.
- Do not add packaged `26.2` driver manifest support.
- Do not move Minecraft game-class probes into `driver-fabric-discovery`.
- Do not claim latest/current gameplay support.

## Design

Create `FabricRuntimeGraphFragment.kt` in `driver-fabric-discovery`. It should
contain the shared fragment model and composer. The composer should rely on
existing `RuntimeCapabilityGraph` validation for duplicate ids and invalid
references.

In `driver-fabric`, replace the local `FabricCapabilityGraphFragment` data
class with a typealias to `FabricRuntimeGraphFragment` so lane probe source can
continue to read naturally while the durable fragment model lives in the
shared module.

In `driver-fabric-official`, replace the direct `RuntimeCapabilityGraph`
construction with `fabricRuntimeMetadataGraph`.

## Acceptance

- Shared tests prove `fabricRuntimeGraph` composes resources, operations,
  handles, and events from multiple fragments.
- Shared tests prove duplicate node ids are still rejected by graph validation.
- Architecture guard proves `driver-fabric` no longer declares a local
  `FabricCapabilityGraphFragment` data class.
- Architecture guard proves `OfficialFabricDriverBackend` uses
  `fabricRuntimeMetadataGraph` and no longer imports `RuntimeCapabilityGraph`.
- Focused Fabric/official tests pass.
- The real enabled official attach probe still observes `client.attached`.
- The official OpenAPI artifact still reports `actions=0`, `resources=1`, and
  live `mods:` metadata.
- No public gameplay action, packaged 26.x manifest entry, static gameplay
  catalog, scenario shortcut, or latest/current support claim is added.
