# Shared Fabric Attach Boundary Design

## Problem

Phase 146 proved that the latest/current Minecraft 26.x official Fabric lane
can compile as a separate non-remap module. That module currently has only a
minimal entrypoint. The verified Yarn/remap lane already has useful Fabric
self-attach and Ktor loopback infrastructure, but that code lives inside
`driver-fabric/`, so the official lane cannot reuse it without depending on
the Yarn/remap module or copying the implementation.

Copying attach/runtime code into `driver-fabric-official/` would harden the
project around per-version driver trees. That violates the Craftless design:
shared resolver, runtime graph, projection, invocation, Ktor transport, and
generated OpenAPI are the default; per-version code exists only for proven
Minecraft, Fabric API, mappings, loader, or bytecode-signature divergence.

## Goal

Extract Fabric self-attach and loopback transport into a neutral shared module
that both `driver-fabric` and `driver-fabric-official` can use. Wire the
official lane entrypoint to start shared attach with a minimal metadata-only
backend so the latest/current lane moves from compile-only boundary toward
real runtime attach without adding gameplay bindings or support claims.

## Non-Goals

- Do not package the 26.x official lane as a supported driver artifact.
- Do not add a 26.x entry to `driver-mods.json`.
- Do not clone Yarn/remap gameplay bindings into `driver-fabric-official/`.
- Do not add public gameplay actions, route families, CLI gameplay catalogs,
  scenario shortcuts, or static descriptor lists.
- Do not make `driver-fabric-official` depend on `driver-fabric`.
- Do not claim latest/current gameplay support until launch, self-attach,
  generated OpenAPI/actions/resources, SSE, packaged distribution, and public
  API/CLI gameplay evidence all pass.

## Design

Create a shared internal module, tentatively `driver-fabric-attach`, that owns:

- Fabric attach environment parsing;
- Fabric driver self-attach startup;
- Ktor loopback routes for the stable `DriverSession` contract;
- attach replacement calls back to the daemon;
- lifecycle/event transport that does not depend on Yarn/remap classes.

`driver-fabric` keeps its current behavior by depending on the shared attach
module and passing its existing real Fabric backend/session factory.

`driver-fabric-official` depends on the shared attach module plus stable
runtime contracts. Its entrypoint starts shared attach with a minimal
metadata-only backend. That backend may expose lifecycle/runtime metadata and
zero gameplay actions until generic discovery/projection work is ported or
shared. Empty gameplay support is acceptable at this phase only because the
phase proves attach reuse, not support breadth.

Any official-lane code that touches Minecraft/Fabric APIs must be isolated
behind lane-local adapters. Ktor transport, session replacement, action
descriptor shape, invocation transport, events, OpenAPI projection contracts,
and public naming stay shared.

## Acceptance

- `settings.gradle.kts` includes the shared Fabric attach module.
- `driver-fabric` depends on the shared attach module and no longer owns the
  self-attach/loopback source files directly.
- `driver-fabric-official` depends on the shared attach module and does not
  depend on `driver-fabric`.
- Existing Yarn/remap attach tests move to or cover the shared module.
- The official lane entrypoint calls the shared attach startup path.
- `mise run fabric-lane-check-latest-official` still writes `status=compiled`.
- The phase adds no packaged 26.x driver manifest entry and no public gameplay
  action catalog.
