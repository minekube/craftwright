# Parameterized Fabric Smoke Client Command Design

## Problem

`fabricClientSmoke` can be invoked with `craftless.fabric.*` Gradle properties
so the smoke metadata and server version point at an older or future compiled
lane. Its default inner action command still launches
`:driver-fabric:runClient` without those lane properties. That can make a
supposed older-lane smoke launch the default current lane and produce misleading
compatibility evidence.

## Decision

Build the default Fabric smoke action command from the active compiled lane
properties. When `CRAFTLESS_SMOKE_ACTION_COMMAND_JSON` is not supplied, the
generated command must pass the same `craftless.fabric.*` Gradle properties to
the inner `:driver-fabric:runClient` invocation that the outer smoke task uses
for metadata and runtime-lane evidence.

This is smoke harness correctness. It does not add gameplay APIs, route
families, public version-specific contracts, or runtime support claims.

## Non-Goals

- Do not add public gameplay action descriptors or CLI gameplay catalogs.
- Do not add another compiled lane module.
- Do not claim the representative older lane launches successfully until the
  real smoke is run and evidence is recorded.
- Do not remove the explicit `CRAFTLESS_SMOKE_ACTION_COMMAND_JSON` override.

## Verification

- A Fabric module test must fail before implementation because the build script
  does not include lane property propagation in the default smoke command.
- The green test must prove the default action command includes all active
  `craftless.fabric.*` lane properties needed for `runClient`.
- Focused driver-fabric tests and `git diff --check` must pass.
