# Shared Fabric Runtime Resource Projection Design

## Problem

`driver-fabric-discovery` now owns shared Fabric Loader runtime metadata
snapshots and fingerprints. The next duplication is runtime graph projection:

- the Yarn/remap Fabric lane builds a `runtime` resource in
  `FabricRuntimeMetadataCapabilityProbe`;
- the latest/current official lane hand-builds its own metadata-only `runtime`
  resource in `OfficialFabricDriverBackend`.

That keeps official runtime graph evidence thinner than the remap lane and
invites future per-lane drift. Runtime metadata projection is generic Fabric
discovery plumbing, not gameplay and not a per-version public API.

## Goal

Move the `runtime` resource projection for Fabric runtime metadata into
`driver-fabric-discovery` so both lanes use the same function to expose:

- installed-mods fingerprint;
- registry fingerprint;
- server-feature fingerprint;
- permissions fingerprint;
- lane/status/java evidence supplied by the caller.

The official lane should still remain metadata-only for gameplay, but its
generated per-client OpenAPI resource graph should carry the same shared
runtime metadata evidence shape as the Yarn/remap lane.

## Non-Goals

- Do not add public gameplay actions.
- Do not add static action descriptors or action catalogs.
- Do not add packaged `26.2` driver manifest support.
- Do not move Minecraft game-class registry, server feature, or gameplay
  operation probes into the shared module.
- Do not claim latest/current gameplay support.

## Design

Add `fabricRuntimeResourceNode(metadata, sourceEvidence)` to
`driver-fabric-discovery`. It returns a `RuntimeResourceNode` with id
`runtime`, available state, and source evidence containing the metadata
fingerprints plus caller-supplied lane evidence.

`FabricRuntimeMetadataCapabilityProbe` should call this helper instead of
constructing the node itself.

`OfficialFabricDriverBackend.runtimeGraph()` should call the same helper with
its metadata and lane-specific source evidence:

- `runtime-lane=latest-current-official`;
- `runtime-status=metadata-only`;
- `runtime-java=java:25`.

## Acceptance

- Shared tests prove `fabricRuntimeResourceNode` emits the standard metadata
  evidence and preserves caller-supplied lane evidence.
- Architecture guard proves `OfficialFabricDriverBackend` uses
  `fabricRuntimeResourceNode` and no longer constructs `RuntimeResourceNode`
  directly.
- Focused Fabric/official tests pass.
- The real enabled official attach probe still observes `client.attached`.
- The generated official per-client OpenAPI resource projection still reports
  `resources=1`, `actions=0`, and live `mods:` metadata.
- No public gameplay action, packaged 26.x manifest entry, static gameplay
  catalog, scenario shortcut, or latest/current support claim is added.
