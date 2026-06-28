# Official World Time Invocation Design

## Problem

Phase 179 made the official 26.x/latest-current lane project
`world.time.query` from shared Fabric client-state discovery, but invoking the
generated operation still returned unsupported. That left latest/current with
a visible generated primitive but no execution proof.

## Design

Add a narrow official-lane execution provider for the already-generated
`world.time.query` operation.

The backend must:

- look up the requested operation in its generated runtime graph;
- reject unavailable operations using the graph availability reason;
- dispatch by the private adapter key, not by adding a static public action
  catalog;
- read official Minecraft clock values on the Minecraft client thread;
- return the same Craftless result shape as the current lane:
  `time` and `time-of-day`.

The official provider is lane-local because Minecraft 26.x official mappings
use clock APIs that differ from the Yarn/remap lane.

## Non-Goals

- Do not package the official 26.x lane as supported.
- Do not claim CL-03 is complete.
- Do not add static CLI commands, daemon routes, or scenario shortcuts.
- Do not copy the Yarn/remap gameplay gateway into the official lane.
- Do not implement broader gameplay primitives in this phase.

## Acceptance

- A focused official-lane test first fails because `world.time.query`
  invocation is unsupported.
- The official backend accepts `DriverActionInvocation("world.time.query")`
  when the generated operation is available.
- The result contains `time` and `time-of-day`.
- `:driver-fabric-official:test` passes.
- `mise run fabric-lane-check-latest-official` reports `status=compiled`.
- `mise run architecture-check` passes.
