# Phase 48: Stable Fabric Entrypoint Boundary Design

## Goal

Keep Fabric mod metadata pointed at a stable Craftless entrypoint while current
compiled-lane bootstrap logic stays internal to the version-specific provider
package.

## Context

Phase 47 made Fabric resource metadata derive from Gradle compiled-lane values,
but `fabric.mod.json` still pointed directly at the `v1_21_6` entrypoint class.
That leaks the current compiled implementation family into the mod entrypoint
boundary and makes future version-lane selection harder to isolate.

The durable shape is a stable Fabric entrypoint that initializes Craftless and
delegates to the selected internal runtime/provider lane. This phase keeps the
current lane selected because only one compiled Fabric/Loom lane exists today.
It does not claim new Minecraft version support.

## Requirements

- Add a stable non-versioned Craftless Fabric client entrypoint class.
- Update `fabric.mod.json` to point at the stable entrypoint.
- Move current `1.21.6` initialization code behind an internal current-lane
  bootstrap object.
- Keep bytecode-sensitive mixin/accessor package names version-scoped.
- Preserve current runtime behavior and smoke startup behavior.
- Do not add public gameplay action ids, generated route families, CLI
  gameplay catalogs, Fabric descriptor/binding pairs, scenario shortcuts, a new
  compiled lane, or public version-specific APIs.

## Non-Goals

- Do not implement dynamic multi-lane selection in this phase.
- Do not move mixins or accessors out of the version-scoped package.
- Do not change Minecraft, Yarn, Fabric Loader, Fabric API, or Java versions.
- Do not mark Craftless complete.

## Verification

- A focused Fabric metadata test proves `fabric.mod.json` points at the stable
  entrypoint and no longer references the `v1_21_6` entrypoint class.
- `:driver-fabric:test` passes.
- `mise run lint`, `mise run architecture-check`, and `mise run ci` pass before
  claiming this phase complete.
