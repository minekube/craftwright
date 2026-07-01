# Wildcard Fabric Loader Matrix Design

Date: 2026-07-02
Phase: 203

## Problem

Craftless driver manifests allow a Fabric driver row to omit `loaderVersion`.
That row is a wildcard lane in the create-client path: an explicit requested
Fabric Loader version may use it when the Minecraft target and other runtime
identity fields match.

`GET /versions/support-targets` must use the same compatibility semantics.
Before this phase, the matrix grouped driver rows only by exact loader version,
so a wildcard lane could appear as a separate `loaderVersion = null` supported
runtime row while every discovered concrete Fabric Loader row was marked
unsupported. That contradicts the public create-client behavior and weakens the
full Fabric matrix proof.

## Contract

- Exact driver rows remain the preferred support proof for a discovered Fabric
  Loader version.
- A wildcard driver row supports each discovered Fabric Loader version for the
  same Minecraft target when no exact row exists.
- Wildcard rows must not create an extra null-loader runtime target when
  concrete Fabric Loader versions are discoverable.
- Matrix rows continue to expose the concrete requested loader identity while
  preserving the matching driver row as `driverMod`.

## Design

`VersionDiscoveryService.runtimeTargets` separates exact driver rows from
wildcard rows. For each discovered Fabric Loader version it selects:

1. the first exact driver row for that loader version; otherwise
2. the first wildcard driver row for the Minecraft target; otherwise
3. an unsupported row with the target-level reason.

Manifest-only rows are still emitted for configured exact loader versions that
are not present in the discovered Fabric Loader list. Wildcard manifest rows
are excluded from this manifest-only path because their support is projected
onto discovered loader identities.
