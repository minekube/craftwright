# Bounded Material Exploration Design

## Intent

Make public-agent material search more reliable when the initial
`world.block.query` returns no material. The latest live no-hold run blocked
while following a long exploration waypoint after several empty log queries.

This phase improves external agent policy only. It must not add `find.tree`,
`collect.wood`, `mine.log`, or any other product shortcut.

## Product Rules

- Keep `world.block.query` bounded by the generated API.
- Explore by composing `player.query`, `navigation.plan`,
  `navigation.follow`, and `world.block.query`.
- Prefer smaller overlapping movement steps instead of larger scans on the
  client thread.
- Preserve explicit blockers when generated navigation fails or no public
  material evidence is found.

## Behavior

When the local material query is empty:

1. Query player position.
2. Generate overlapping material-search waypoints around the origin.
3. Keep each waypoint within a short movement step from nearby explored space.
4. Query material after each navigation follow.
5. Stop at the first public material target.
6. Return `insufficient-public-evidence:world.block.query.log` if bounded
   exploration finds none.

The first exploration step should be 24 blocks, not 48, because the block query
radius is 32 and overlapping smaller searches reduce long-path failures.

## Evidence

Tests and live artifacts must show:

- exploration uses generated navigation and block queries only;
- first exploration waypoint is a shorter overlapping step;
- no scenario shortcut strings are introduced;
- live blockers, if any, identify a generic primitive or evidence gap.
