# Resolved Driver Mod Lane Request Design

## Problem

Phase 111 made cache preparation resolve `latest-release` and
`latest-snapshot` to concrete Mojang version ids before cache handles are
built. The prepared client runtime path still asks the driver-mod provider
with the original `CreateClientRequest.version`. For an alias request, a
manifest-backed driver-mod provider would look for `latest-release` instead of
the concrete runtime lane that cache preparation actually prepared.

This is a runtime artifact selection problem, not a gameplay API problem.

## Goals

- Pass the prepared concrete Minecraft version to
  `ClientRuntimeDriverModProvider`.
- Keep the resolved Fabric Loader version in the same provider request.
- Preserve exact-version behavior.
- Preserve the original client creation request as user intent for the rest of
  the supervisor lifecycle.

## Non-Goals

- Do not add new compiled Fabric lanes.
- Do not claim latest/current or older aliases are runnable.
- Do not add public gameplay actions, generated route families, CLI gameplay
  catalogs, Fabric gameplay bindings, scenario shortcuts, or public
  version-specific APIs.
- Do not redesign instance file naming in this phase.

## Acceptance Criteria

- A focused daemon test fails before implementation when a client created with
  `version=latest-release` asks the driver-mod provider for `latest-release`.
- After implementation, that provider request uses the prepared concrete
  Minecraft version `1.21.6` and resolved loader version `0.17.2`.
- Exact version requests keep the same provider request behavior.
- AGENTS/checklist/evidence record Phase 112 and keep runnable latest/older
  support open.
