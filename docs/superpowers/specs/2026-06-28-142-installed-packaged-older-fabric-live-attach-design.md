# Installed Packaged Older Fabric Live Attach Design

## Problem

Phase 141 proved the representative older Fabric lane through the Gradle smoke
harness. The product path still needs proof that the installed packaged CLI
distribution can run the same older lane: packaged `craftless server start`
must auto-discover packaged `driver-mods.json`, select the `1.20.6` driver
artifact, launch a real client from `craftless clients create`, self-attach the
driver, and expose generated per-client API surfaces.

## Decision

Build the packaged CLI distribution with `mise run package-cli`, then use the
packaged binary from `build/docker/craftless/bin/craftless` for the whole
smoke:

1. Start the packaged supervisor with `craftless server start`.
2. Create a real offline `1.20.6` Fabric client with loader version `0.19.3`.
3. Fetch generated OpenAPI, actions, resources, and event stream with the
   packaged CLI/API surface.
4. Record the runtime metadata and evidence artifacts.

This phase uses the packaged product surface, not Gradle `runClient`, and it
must not add public gameplay API breadth or scenario shortcuts.

## Non-Goals

- Do not claim final honest survival gameplay completion.
- Do not require a local multiplayer server join.
- Do not add public gameplay actions, static route families, CLI gameplay
  catalogs, or version-specific public APIs.
- Do not publish a new release from this phase.

## Verification

- `mise run package-cli` must pass before the packaged smoke.
- The packaged binary must start a supervisor and create a `1.20.6` Fabric
  client through `clients create`.
- Evidence must show `client.attached`, generated OpenAPI, generated actions,
  generated resources, and events for the attached client.
- Evidence must show the older lane runtime identity: Minecraft `1.20.6`,
  Fabric Loader `0.19.3`, Java `21`, and mappings fingerprint
  `craftless-fabric-bindings-1-20-6`.
- The evidence must explicitly state that this is packaged live attach proof,
  not final survival gameplay.
