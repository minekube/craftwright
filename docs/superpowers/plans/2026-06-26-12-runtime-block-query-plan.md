# Runtime Block Query Plan

## Architecture

Add block perception through the runtime capability graph and graph operation
adapter. Keep it out of the transitional hand-written Fabric action descriptor
allowlist.

## Steps

1. Add red tests for `world.block.query` as a runtime graph operation and
   `world.block.handle` as a runtime handle.
2. Add a red adapter test that invokes the graph operation through
   `DriverOperationAdapters`, not through a static binding.
3. Implement graph projection in `FabricClientStateCapabilityProbe`.
4. Implement `fabric.world-block-query` in `FabricDriverBackend` with bounded
   radius/limit, optional category filtering, Craftless-owned payload names,
   and client-thread execution.
5. Verify focused Fabric tests.
6. Run the public-agent no-hold gameplay check and record the next blocker or
   success evidence.
7. Run `mise run ci` and `mise run architecture-check`.
8. Commit and push directly to `main`.

## Guardrails

- Do not add `world.block.query` to
  `docs/architecture/transitional-fabric-action-allowlist.txt`.
- Do not add scenario actions such as `find.tree`, `kill.cow`, or
  `craft.sword`.
- If the public agent blocks again, report
  `missing-generic-primitive:<name>` and improve the generic system next.
