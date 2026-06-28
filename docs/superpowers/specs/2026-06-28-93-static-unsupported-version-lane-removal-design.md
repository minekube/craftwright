# Static Unsupported Version Lane Removal Design

## Problem

Craftless still carries static unsupported entries for latest `26.2` and
representative older `1.20.6` in the default Fabric compatibility matrix. Those
entries were useful as diagnostic evidence, but keeping them in product runtime
code is a static version catalog. It is not the generated/provider-backed
support model the project is supposed to converge on.

Historical probe evidence can remain in evidence files. The product runtime
matrix should only list real provider-backed lanes. Unknown or not-yet-backed
versions should resolve through one generic unsupported path until a provider
or generated runtime-lane resolver can support them.

## Goals

- Remove hard-coded latest and older unsupported lanes from the default Fabric
  compatibility matrix.
- Make `26.2`, `1.20.6`, and any other non-provider-backed version resolve
  through generic unsupported version handling.
- Remove Gradle smoke-lane special casing for `26.2`; the smoke harness should
  only special-case the compiled lane and otherwise emit generic unsupported
  lane evidence unless explicit runtime-lane JSON is supplied.
- Update current docs/README/checklist wording so static unsupported lane ids
  are historical diagnostics, not active product runtime design.
- Keep final completion blocked on real runnable latest/current and older lane
  support.

## Non-Goals

- Do not add latest/current or older-version support in this phase.
- Do not delete historical evidence files that recorded the old probe outputs.
- Do not add public version-specific APIs, route families, generated aliases,
  CLI catalogs, Fabric gameplay bindings, or scenario shortcuts.
- Do not mark the project complete.

## Acceptance Criteria

- `defaultFabricCompatibilityMatrix()` no longer contains `latest-release-26-2`
  or `older-release-1-20-6`.
- `26.2` and `1.20.6` resolve to generic unsupported lanes with
  `unsupported-version`.
- `driver-fabric/build.gradle.kts` no longer has a `when (version) { "26.2" ->`
  smoke-lane branch.
- Current README/checklist language does not present static latest/older
  unsupported lanes as active product runtime entries.
- Local focused and full `driver-fabric` tests pass.
