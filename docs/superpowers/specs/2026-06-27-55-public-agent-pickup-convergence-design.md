# Phase 55: Public Agent Pickup Convergence Design

## Goal

Make the public-agent material pickup loop converge on observed dropped items
when generated navigation cannot start near the drop, including elevated drops
that require a generated jump movement.

## Context

The Phase 54 final-gameplay rerun fixed the helper process timeout boundary and
let the public-agent run continue long enough to expose the next blocker. The
agent broke a real log through generated `world.block.break`, observed an
`Oak Log` item through generated `entity.query`, but blocked with
`insufficient-public-evidence:inventory.query.log`. Generated
`navigation.follow` repeatedly returned `navigation-did-not-start`, and the
existing fallback `player.move` nudged toward the drop without jumping for a
drop above the player.

## Requirements

- Stay outside the product API. This is public-agent policy composed from
  generated actions, not a new Craftless gameplay action.
- When a material drop is visible and generated navigation fails, the public
  agent must use bounded repeated `player.query`, `player.look`, `player.move`,
  `entity.query`, and `inventory.query` to approach the drop.
- Fallback generated `player.move` must request `jump=true` when the public
  target position is meaningfully higher than the public player position.
- Each pickup attempt must re-check public inventory evidence before deciding
  whether to continue.
- If the drop remains visible, the agent should keep using its latest public
  position rather than a stale original position.
- The loop must remain bounded and return a blocker when public evidence never
  proves pickup.
- Do not add `collect.wood`, `pickup.log`, survival macros, static action
  descriptors, new public routes, new compiled lanes, or new Minecraft support
  claims.

## Design

Use the existing bounded public-agent pickup loop and make its generated
movement target-aware in the vertical axis:

1. Query `player.query` for the current position before fallback movement.
2. Look at the latest public drop position with generated `player.look`.
3. Run generated `player.move` with `forward=true`.
4. Set `jump=true` when the drop y coordinate is above the player by a small
   threshold.
5. Re-check public inventory evidence and continue the bounded pickup loop if
   the item is not yet proven collected.

This helper remains external policy inside `PublicAgentGameplayRunner`. It
does not add or change generated action descriptors. It only improves how the
acceptance agent composes existing generated primitives.

## Verification

- A focused public-agent test proves elevated visible material drops cause
  generated fallback pickup movement to include `jump=true`.
- Existing public-agent material pickup tests still pass.
- `git diff --check`, focused tests, `mise run lint`,
  `mise run architecture-check`, and `mise run ci` pass before this phase is
  claimed complete.
