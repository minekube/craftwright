# Public Agent Material Navigation Design

## Intent

Move the public-agent final gameplay runner from passive perception into the
first composed gameplay step: discover material blocks, plan navigation toward
one, follow the plan, and record evidence. This remains an external consumer
of Craftless generated APIs, not a new product action.

## Product Rules

- Do not add `find.tree`, `mine.log`, `craft.sword`, `kill.cow`, or any
  scenario-specific public action.
- Do not call `task.survival.*` from the public-agent runner.
- Do not call Fabric internals, driver internals, server commands, or manual
  operator movement.
- Invoke only generated actions through `POST /clients/{id}:run` after
  fetching `/openapi.json`, `/clients/{id}/openapi.json`,
  `/clients/{id}/actions`, and `/clients/{id}/events:stream`.

## Behavior

The public agent performs this generic policy:

1. Query inventory.
2. Query nearby logs with `world.block.query` using `category = "log"`,
   bounded `radius`, and bounded `limit`.
3. If no log block is returned, stop with
   `insufficient-public-evidence:world.block.query.log`.
4. Convert the nearest returned block position into a generic
   `navigation.plan` goal.
5. Invoke `navigation.plan`; if no plan id is returned, stop with
   `insufficient-public-evidence:navigation.plan`.
6. Invoke `navigation.follow` with the returned plan id.
7. Query entities after the navigation request to keep perception evidence in
   the artifact stream.

This phase does not mine, craft, equip, fight, or claim survival completion.
It proves the agent can compose public discovery, resource projection, and
navigation primitives. Later phases use the same pattern for mining,
inventory/crafting, combat, chat, and final evidence.

## Evidence

Artifacts must show:

- action sequence includes `inventory.query`, `world.block.query`,
  `navigation.plan`, `navigation.follow`, and `entity.query`;
- `world.block.query` request contains `category = "log"`;
- `navigation.plan` request is derived from a returned block position;
- `task.survival.*` is absent;
- blockers are explicit when public evidence is missing.
