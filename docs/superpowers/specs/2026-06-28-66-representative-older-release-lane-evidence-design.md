# Phase 66: Representative Older Release Lane Evidence Design

## Goal

Record a real older Minecraft release as compatibility evidence without
claiming Craftless can run that Fabric client lane yet.

## Context

Phase 50 made the latest `26.2` lane explicit and unsupported when no matching
Fabric client runtime exists. The completion gate also needs representative
older-version evidence so multi-version work does not only look forward to the
latest release. Unknown-version fallback is too vague for this: a known older
release should carry real Mojang metadata such as its Java requirement and a
machine-readable unavailable reason.

At the time this phase was written, the Mojang manifest reports Minecraft
`1.20.6` as a release with Java runtime component `java-runtime-delta` and
major version `21`.

## Requirements

- Preserve the current compiled `1.21.6` lane as the only supported Fabric
  client lane.
- Add a representative older release lane for `1.20.6`.
- Keep the older lane `UNSUPPORTED` with provider
  `no-compatible-client-lane` and reason `runtime-lane-missing`.
- Record Java major version `21` from Mojang metadata.
- Keep unknown versions on the generic unsupported fallback.
- Update evidence and checklist wording so this is not confused with working
  older-version Fabric support.

## Non-Goals

- Do not add `1.20.6` Fabric client support.
- Do not add a new compiled Loom lane.
- Do not add public gameplay actions, route families, CLI gameplay catalogs,
  Fabric descriptor/binding pairs, scenario shortcuts, or public
  version-specific APIs.
- Do not mark Craftless complete.

## Verification

- The compatibility matrix test proves `1.20.6` resolves to the older-release
  unsupported lane with Java 21 and `runtime-lane-missing`.
- Current 26.x/latest evidence remains unchanged.
- `git diff --check`, `mise run architecture-check`, and remote CI pass before
  claiming the phase complete.
