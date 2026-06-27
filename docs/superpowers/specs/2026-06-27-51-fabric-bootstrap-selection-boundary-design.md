# Phase 51: Fabric Bootstrap Selection Boundary Design

## Goal

Move Fabric client startup selection behind a stable internal Craftless
bootstrap selector so the public Fabric entrypoint does not import the current
version-scoped implementation package directly.

## Context

Phase 48 made `fabric.mod.json` point at the stable
`com.minekube.craftless.driver.fabric.CraftlessFabricClientEntrypoint`, but the
entrypoint still imports `v1_21_6.FabricCurrentLaneBootstrap` directly. That is
better than exposing the versioned entrypoint in Fabric metadata, but it still
makes the stable Fabric-facing boundary know the current compiled provider
package.

Craftless still has one compiled Fabric/Loom lane today. This phase does not
add a second lane or dynamic runtime selection. It introduces the internal
selection seam that future lanes can plug into without changing the Fabric mod
entrypoint.

## Requirements

- Add a stable internal bootstrap contract in the non-versioned Fabric package.
- Add a stable internal selector/registry in the non-versioned Fabric package.
- Make the stable Fabric entrypoint call the selector only.
- Keep current `1.21.6` startup behavior behind the version-scoped bootstrap.
- Keep the selector honest: it may select only the current compiled lane today.
- Add tests that prevent the stable entrypoint from importing
  `com.minekube.craftless.driver.fabric.v1_21_6` or
  `FabricCurrentLaneBootstrap` directly.
- Add tests that prove the selector exposes the current compiled lane metadata
  without initializing Minecraft.

## Non-Goals

- Do not add Minecraft `26.2` Fabric client support.
- Do not add a new compiled Loom lane.
- Do not move Mixins, accessors, or bytecode-sensitive classes out of the
  version-scoped package.
- Do not add public gameplay actions, generated route families, CLI gameplay
  catalogs, Fabric descriptor/binding pairs, scenario shortcuts, or public
  version-specific APIs.
- Do not mark Craftless complete.

## Verification

- Focused Fabric tests fail before the selector exists and pass after the
  boundary is implemented.
- `:driver-fabric:test`, `mise run lint`, `mise run architecture-check`, and
  `mise run ci` pass before this phase is claimed complete.
