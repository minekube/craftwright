# Runtime Block Query Design

## Intent

Expose nearby block resources from the running client so an external agent can
discover material sources through the generated per-client OpenAPI. This is a
generic perception primitive, not a survival shortcut.

## Product Rules

- Do not add `find.tree`, `mine.log`, `obtain.wood`, or other scenario actions.
- Do not add a new hand-written Fabric action descriptor/binding pair merely to
  grow the public action catalog.
- Project `world.block.query` from the runtime capability graph and expose it
  through generated OpenAPI/actions/resources.
- Execute it through a graph operation adapter keyed by the runtime operation,
  so `POST /clients/{id}:run` remains the public invocation path.
- Return Craftless-owned payloads: block handles, positions, category labels,
  distance, count, origin, and bounded query inputs.

## Behavior

`world.block.query` is available only when the client has a connected player
and world. It accepts:

- `radius`: positive bounded number;
- `limit`: bounded integer result count;
- `category`: optional Craftless-owned category such as `log`, `block`,
  `fluid`, `air`, `non-air`, or `any`.

The result is a bounded object projection with opaque block handles and
positions. It must not expose Fabric, Yarn, intermediary, raw Minecraft class,
or registry implementation names as public contracts.

## Acceptance

- Runtime graph includes `world.block.query` and `world.block.handle`.
- Generated per-client OpenAPI and `/clients/{id}/actions` include the action
  from the graph.
- `POST /clients/{id}:run` invokes the graph adapter.
- The public gameplay agent progresses past
  `missing-generic-primitive:world.block.query` without calling
  `task.survival.*`.
