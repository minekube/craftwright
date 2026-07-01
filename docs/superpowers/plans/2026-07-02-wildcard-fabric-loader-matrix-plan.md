# Wildcard Fabric Loader Matrix Plan

Date: 2026-07-02
Phase: 203

## Checklist

- [x] Add a failing HTTP regression test for a Fabric driver manifest row with
  no `loaderVersion`.
- [x] Prove the current bug: the matrix emits an extra null-loader row instead
  of projecting support onto discovered concrete loader rows.
- [x] Update the runtime-target projection so exact loader rows win and
  wildcard driver rows support discovered loader identities.
- [x] Keep the exposed runtime row loader version concrete while the matching
  `driverMod.loaderVersion` remains null.
- [x] Run focused daemon tests, full daemon tests, lint, repository CI, commit,
  push, and watch main CI.
