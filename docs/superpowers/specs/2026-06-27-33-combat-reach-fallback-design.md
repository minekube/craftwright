# Combat Reach Fallback Design

## Intent

The 2026-06-27 final gameplay rerun reached generated public combat
navigation, but the process-external public agent blocked with
`insufficient-public-evidence:entity.query.attack-target.reachable`. Generated
`navigation.follow` reported success, while follow-up public `player.query`
and `entity.query` evidence still placed the target outside the generated
attack reach budget.

This phase improves external public-agent policy only. It must not add
`find.cow`, `kill.cow`, `hunt.animal`, a survival macro, or any new product
gameplay action.

## Product Rules

- Keep combat targets sourced from generated `entity.query` handles and
  positions.
- Keep long movement sourced from generated `navigation.plan` and
  `navigation.follow`.
- Treat `navigation.follow` success as insufficient proof for
  `entity.attack` when public state still contradicts reachability.
- If `player.move` is discovered, allow one bounded generic nudge toward the
  current public entity position after generated navigation reports success
  but public state still proves the target is outside attack reach.
- Re-query public player and entity state after the nudge before any
  `entity.attack`.
- If `player.move` is not discovered, or the target remains outside reach
  after bounded movement, block with
  `insufficient-public-evidence:entity.query.attack-target.reachable`.

## Evidence

Tests and live artifacts must show:

- a successful generated navigation response is not trusted blindly before
  attacking;
- `entity.attack` is not invoked until public state proves the target is
  attack-reachable;
- the optional fallback uses generated `player.move`, not a combat shortcut;
- absence of `player.move` preserves the reach-evidence blocker instead of
  turning optional recovery into required API breadth;
- no scenario shortcut appears in public-agent request bodies;
- final gameplay either reaches `publicAgentState=RAN` or records a precise
  generic evidence blocker.
