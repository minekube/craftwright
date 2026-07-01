# Fabric Loader Runtime Matrix Design

## Goal

Move `GET /versions/support-targets` closer to the full Fabric compatibility
goal by making unsupported Fabric Loader identities explicit instead of
collapsing each unsupported game target into one generic row.

## Current Gap

Phase 200 exposed `runtimeTargets`, but those rows were still derived only from
packaged Craftless driver mods. That proved the supported packaged rows, but it
did not let an API caller see that Fabric advertises many loader versions for a
game target and that Craftless only has driver evidence for a subset.

## Design

- Continue to use Fabric's global loader metadata from
  `GET https://meta.fabricmc.net/v2/versions/loader` as the discoverable loader
  identity set.
- For every Fabric game target returned by Fabric metadata, emit one
  `runtimeTargets` row per discovered Fabric Loader version.
- Mark a row supported only when the configured Craftless driver mod manifest
  has a Fabric driver entry for the same Minecraft version and loader version.
- Mark unsupported rows with a machine-readable reason:
  - `NO_DRIVER_MOD` when the Minecraft game target has no Craftless driver lane
    at all.
  - `NO_COMPATIBLE_DRIVER_MOD` when the game target has a Craftless driver lane
    but not for that loader identity.
- Preserve manifest-only supported rows when a driver mod has a loader identity
  not present in Fabric's loader list, so configured support is not hidden.

## Non-Goals

This phase does not claim that every Fabric Loader version launches. It makes
each discovered unsupported loader identity visible and actionable so the
remaining work can prove or reject concrete combinations.
