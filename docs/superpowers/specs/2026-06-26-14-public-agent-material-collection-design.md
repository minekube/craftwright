# Public Agent Material Collection Design

## Intent

Extend the public-agent gameplay runner from navigation to the first real
collection attempt. The agent should use generated actions to face a discovered
log block, raycast, invoke generic block breaking, and verify inventory state.

## Product Rules

- Do not add `mine.log`, `collect.wood`, `find.tree`, `craft.sword`,
  `kill.cow`, or any other scenario-specific public action.
- Do not call `task.survival.*` from the public-agent runner.
- Do not call Fabric internals, driver internals, server commands, or manual
  operator movement.
- Do not claim mining success from an accepted action alone. Verify state from
  public inventory/block/entity evidence.

## Behavior

The runner continues the Phase 13 policy and then:

1. Query `player.query` after navigation succeeds.
2. Compute a generic look yaw/pitch from the public player position to the
   public block position returned by `world.block.query`.
3. Invoke `player.look`.
4. Invoke `player.raycast` for target evidence.
5. Invoke `world.block.break`.
6. Re-query `inventory.query`.
7. If inventory does not show a collected log item, stop with
   `insufficient-public-evidence:inventory.query.log`.
8. Re-query `entity.query` only after inventory evidence is present.

This phase may expose that `world.block.break` needs richer generic semantics,
such as target handles, duration, repeated breaking progress, pickup evidence,
or block-state verification. If so, the next phase must improve the generic
runtime graph/action system rather than adding a scenario shortcut.

## Evidence

Artifacts must show:

- generated action sequence includes `player.query`, `player.look`,
  `player.raycast`, `world.block.break`, and final `inventory.query`;
- `player.look` arguments are derived from public positions;
- blocked runs preserve action evidence before the blocker;
- success requires final inventory evidence for a log item;
- `task.survival.*` is absent.
