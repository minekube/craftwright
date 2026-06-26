# Honest Survival Navigation Design

**Goal:** Make the final Craftless gameplay gate honest survival automation:
Craftless must obtain equipment through normal gameplay, navigate the world,
find a cow, and kill it without server-side item provisioning or static
gameplay shortcuts.

**Architecture:** Craftless adds a discovered navigation and task-execution
layer above the runtime capability graph. The layer may use an internal
pathfinder backend such as Baritone when it is present in the client, and it
may borrow algorithms from SwarmBot, but public APIs remain Craftless-owned
graph projections generated from the running client.

**Prior Art:**

- Baritone (`https://github.com/cabaletta/baritone`) is the mature JVM/Fabric
  pathfinding reference. Relevant ideas: goal types, segmented A*, loaded
  chunk cutoff, incremental cost backoff, block-breaking and block-placing
  costs, tool-aware routing, path splicing, and optional Fabric artifacts for
  current Minecraft versions.
- SwarmBot (`https://github.com/SwarmBotMC/SwarmBot`) is useful as a lower
  level bot/task reference. Relevant ideas: explicit movement costs, parkour
  toggles, world-block storage, progressor/goal traits, task composition,
  mine/navigate/bridge/attack tasks, and protocol-level separation of state,
  physics, and actions.

## Problem Found In The Live Gate

The first final gameplay session proved launch, join, OpenAPI, SSE, chat,
inventory query, equip, movement, block break, and block interaction plumbing.
It did not prove honest gameplay because the testkit server provisioned an iron
sword with a server command. A second no-cheat session proved that Craftless can
join with an empty inventory, but also proved the current system cannot yet
collect wood, craft a sword, navigate to a cow, and attack it.

Completion must therefore not rely on:

- server `give` commands;
- pre-seeded inventory;
- manual operator movement for Craftless;
- Baritone chat commands exposed as public Craftless API;
- static public actions such as `kill.cow`, `craft.sword`, or
  `find.tree`.

## Public Shape

The live per-client OpenAPI may expose generated actions/resources such as
these only when discovered from the running runtime graph:

- `navigation.plan`: plan a route to a goal using available navigation engines.
- `navigation.follow`: execute a planned route with live progress events.
- `navigation.stop`: cancel navigation.
- `task.run`: run a named or generated task graph, such as a survival
  acquisition graph, when the runtime exposes enough affordances.
- `task.status`: query a running task.
- `world.entities.query`: query visible projected entity summaries.
- `world.blocks.query`: query visible projected block summaries.
- `inventory.craft`: craft from the player's real inventory and nearby crafting
  context when the runtime discovers a valid recipe/screen path.

Those identifiers are examples of Craftless-owned projections. They must come
from discovery and projection, not from a static route catalog. The supervisor
API remains stable and does not grow gameplay-specific routes.

## Internal Layers

1. **Observation graph**
   - Discovers entity summaries, block samples, visible chunk boundaries,
     inventory contents, recipe availability, tool suitability, and player
     state.
   - Emits private source evidence for Fabric/Minecraft internals.

2. **Navigation engine abstraction**
   - Represents goals, costs, plans, route segments, and progress events.
   - Supports internal backend discovery. A Baritone adapter is allowed if the
     Baritone API is available in the client classpath.
   - Provides an internal fallback for simple local movement only when the
     observed world is sufficient.

3. **Task graph**
   - Orchestrates discovered capabilities into durable tasks.
   - For the first honest survival task: observe nearby logs, navigate to a
     tree, break logs, craft planks/sticks/wooden sword or better available
     weapon, find a cow, navigate near it, equip the weapon, attack until
     killed, and record evidence.

4. **Event stream**
   - Streams navigation and task progress through SSE using Craftless-owned
     event names.
   - Emits enough artifacts to explain each decision: observations, selected
     goal, planned segments, invoked operations, inventory transitions, entity
     target, combat result, and failures.

## Baritone Integration Rule

Baritone may be used as an optional internal navigation backend, not as the
public API:

- detect Baritone through runtime classpath/reflection or mod metadata;
- adapt Craftless navigation goals to Baritone API calls internally;
- project available navigation affordances as Craftless actions/resources;
- do not expose `#goto`, `#mine`, Baritone package names, Baritone settings, or
  Baritone commands as public contracts;
- keep licensing and dependency choices explicit before bundling any Baritone
  artifact.

The first implementation may start by cloning/studying Baritone and adding an
adapter seam. Bundling Baritone into the Fabric driver requires a separate
dependency/license decision.

## SwarmBot Integration Rule

SwarmBot should be treated as algorithmic prior art rather than a dependency:

- copy no code into Craftless without an explicit license review;
- model its useful concepts in Craftless terms: movement costs, progressors,
  goal checks, world-block storage, physics-aware moves, and task composition;
- do not add protocol-bot concepts to the public API, because Craftless drives
  real Minecraft Java clients.

## Honest Survival Acceptance Scenario

The final gameplay session must prove:

1. Craftless joins a real local or agreed server with empty or ordinary
   survival inventory.
2. Craftless captures generated per-client OpenAPI, actions/resources, SSE, and
   runtime graph artifacts.
3. Craftless observes world state and entities through graph-discovered
   resources.
4. Craftless obtains weapon materials through real gameplay, not server
   provisioning.
5. Craftless crafts or otherwise legitimately obtains a weapon.
6. Craftless finds a cow through observed entity state.
7. Craftless navigates to the cow without manual operator movement.
8. Craftless equips the weapon and kills the cow.
9. Craftless writes chat and streams progress/failure events.
10. Robin joins or observes and writes in Minecraft chat that the goal may be
    completed.

## Verification

- Unit tests for navigation/task protocol models and graph validation.
- Fabric tests for discovery/projection of navigation and task affordances.
- Adapter tests that prove Baritone names do not leak into public OpenAPI.
- Slice tests for SSE progress events and `task.run`/`task.status` invocation.
- Opt-in no-cheat gameplay run that fails if server item provisioning occurs.
- Final live run with Robin's Minecraft chat confirmation.

