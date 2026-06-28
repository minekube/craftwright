# v0.1.1 Release Install Evidence Design

## Problem

Phase 103 fixed the installed CLI distribution so install-script and reusable
GitHub Action users get `mods/craftless-driver-fabric.jar` automatically.
The repository now tags `v0.1.1` for that fix, but public docs and checklist
evidence still contain active `v0.1.0` examples from before the driver-mod
distribution closure.

That creates a real usability mismatch: users following the README Action
example could pin a release that does not include the installed driver-mod
distribution fix.

## Goals

- Update active public examples to `v0.1.1` where they are meant to represent
  the current recommended release.
- Keep historical evidence files unchanged when they intentionally document
  older `v0.1.0` runs.
- Verify the `v0.1.1` GitHub Release publishes tar, zip, and checksum assets.
- Verify `install.sh` can install `v0.1.1` from GitHub Releases and the
  installed binary can start the supervisor.
- Verify the installed `v0.1.1` tar contains
  `mods/craftless-driver-fabric.jar`.

## Non-Goals

- Do not add gameplay actions, static route families, CLI gameplay catalogs,
  Fabric action bindings, scenario shortcuts, or version support claims.
- Do not change install behavior beyond release evidence and documentation.
- Do not wait for remote CI when local checks already cover the code path; use
  remote state only as release-publication evidence.

## Acceptance Criteria

- README install and reusable GitHub Action examples point at `v0.1.1`.
- Distribution tests prevent the README from drifting back to old pinned
  release examples.
- Checklist Phase 25 and final gate record current `v0.1.1` release/install
  evidence.
- Evidence file records the release workflow result, published assets,
  install-script smoke, and installed archive driver-mod path.
- Local gates pass and changes are pushed to `main`.
