# Packaged Driver Mod Distribution Design

## Problem

Phase 96 lets a running daemon include a configured Craftless Fabric driver mod
as a launch artifact, but the packaged CLI/Docker flow still does not supply
that configured path automatically. A user can start `craftless server`, create
a client, and get a prepared Fabric runtime without the in-client Craftless
driver unless they already know to set `CRAFTLESS_FABRIC_DRIVER_MOD`.

This keeps the normal product entrypoint behind an undocumented manual wiring
step.

## Goals

- Make the CLI server start path pass its environment into the daemon's
  configured driver mod provider.
- Make `mise run package-cli` build the Fabric remapped driver jar and copy it
  into the Docker/runtime distribution context.
- Make the Docker image set `CRAFTLESS_FABRIC_DRIVER_MOD` to the packaged
  driver mod path.
- Keep the daemon independent from `driver-fabric` and avoid adding gameplay
  catalogs or version-specific public APIs.

## Non-Goals

- Do not add public gameplay actions, static descriptors, generated route
  families, CLI gameplay catalogs, Fabric bindings, or survival shortcuts.
- Do not make `cli` or `daemon` compile against `driver-fabric`.
- Do not claim full live attach completion from packaging alone.
- Do not mark the project complete.

## Acceptance Criteria

- A CLI test proves `server start` forwards a supplied
  `CRAFTLESS_FABRIC_DRIVER_MOD` environment value into the daemon provider.
- A repository policy test proves `package-cli` builds `:driver-fabric:remapJar`
  and stages a deterministic `craftless-driver-fabric.jar` in the Docker
  context.
- `Dockerfile` sets `CRAFTLESS_FABRIC_DRIVER_MOD` to the staged driver jar.
- `mise run package-cli` succeeds and the staged driver jar exists.
- Focused CLI/protocol tests and package smoke checks pass locally.
