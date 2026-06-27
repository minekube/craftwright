# Material Reach Evidence Design

## Intent

The 2026-06-27 final gameplay rerun reached live generated navigation, but the
process-external public agent attempted `world.block.break` after
`navigation.follow` reported success even though public `player.query` evidence
showed the client remained far outside block-break reach. The generated action
then failed with a max-distance input error.

This phase improves external public-agent policy only. It must not add
`find.tree`, `mine.log`, `collect.wood`, a survival macro, or any product
gameplay action.

## Product Rules

- Keep material targets sourced from generated `world.block.query` handles and
  positions.
- Keep movement sourced from generated `navigation.plan` and
  `navigation.follow`.
- Treat `navigation.follow` success as insufficient proof for a subsequent
  target-limited action when public player state contradicts reachability.
- Before calling generated `world.block.break`, require public `player.query`
  position evidence within the same reach budget used for the break request.
- If public state proves the block remains out of reach, block with
  `insufficient-public-evidence:navigation.follow.succeeded` and do not send
  the non-idempotent break request.

## Evidence

Tests and live artifacts must show:

- a successful generated navigation response is not trusted blindly before
  block breaking;
- `world.block.break` is not invoked when public player position is outside
  the break reach budget;
- unrelated combat, recipe, and pickup scenarios still compose through generic
  generated actions;
- no scenario shortcut appears in public-agent request bodies;
- final gameplay either reaches `publicAgentState=RAN` or records a precise
  generic evidence blocker.
