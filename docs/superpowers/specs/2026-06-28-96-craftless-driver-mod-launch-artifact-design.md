# Craftless Driver Mod Launch Artifact Design

## Problem

The prepared-runtime path can now resolve Fabric API as a Fabric mod and copy
launch-plan mods into the instance `mods` directory, but it still has no
generic way to include the Craftless in-client driver mod itself. That means a
normal packaged `craftless server` client launch can start a prepared
Minecraft/Fabric runtime while still producing an unattached
`craftless-prepared-client-runtime` session with no live generated client API.

`daemon` must not depend directly on `driver-fabric`: the Fabric module depends
on shared daemon/protocol code and owns version-specific client internals.
Launch-time driver inclusion therefore needs to be configuration/distribution
driven, not a Gradle module dependency or a hand-coded version lane.

## Goals

- Add a generic daemon-side provider for local Craftless driver mod artifacts.
- Cache a configured Fabric driver mod as a `FABRIC_MOD` artifact under the
  workspace and include its handle in `CacheLaunchPlan.mods`.
- Keep the provider independent from `driver-fabric` implementation classes.
- Preserve the existing Fabric API metadata resolution and launch mod
  materialization flow.
- Make missing driver-mod configuration explicit through runtime metadata and
  tests, without claiming completion.

## Non-Goals

- Do not add public gameplay actions, generated route families, CLI gameplay
  catalogs, Fabric bindings, static action descriptors, or survival shortcuts.
- Do not make `daemon` depend on `driver-fabric`.
- Do not claim latest/current or representative older Minecraft version
  support from this wiring alone.
- Do not solve the live attach/protocol layer between the launched in-client
  driver and the supervisor.
- Do not mark the project complete.

## Acceptance Criteria

- `WorkspaceClientRuntimeDriverFactory.prepare` can consume a configured local
  Fabric driver mod path, copy it into the workspace cache, add it as a
  `FABRIC_MOD` artifact, and include it in the launch plan mods list.
- A focused daemon test fails before implementation and passes after
  implementation.
- The process launcher continues to materialize all launch mods into instance
  files through the existing generic path.
- The implementation uses no static gameplay descriptors and no version-specific
  product API.
- Local daemon tests, ktlint, detekt, and diff whitespace checks pass.
