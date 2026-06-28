# Reflective Recipe Bridge Design

## Problem

After the optional event callback and movement input shims, the representative
older Fabric lane was blocked by direct dependencies on current recipe display,
recipe lookup, recipe-click, and crafting screen handler APIs. Those APIs
changed shape across Minecraft versions.

## Decision

Remove direct recipe-display and recipe-click type imports from the Fabric
backend. Keep Craftless-owned recipe JSON projection, but resolve recipe book
entries, handles, craftability, output slots, and click/display calls
reflectively against the running client.

The bridge may use stable Minecraft concepts such as player inventory, client
recipe book, screen handler, slot, and item stack. It must not use typed
current-only recipe display imports or an accessor mixin that forces current
recipe map field types into every compiled lane.

## Non-Goals

- Do not add new public recipe actions or static recipe catalogs.
- Do not claim older runtime gameplay support is complete.
- Do not implement a version-specific source set in this phase.
- Do not preserve the typed recipe-book accessor mixin.

## Verification

- A source guard must fail before implementation because backend/projection
  sources and mixin config name current-only recipe display types.
- The source guard must pass after implementation.
- `mise run fabric-lane-check-older` must write `status=compiled`.
- Full local CI must pass before pushing.
