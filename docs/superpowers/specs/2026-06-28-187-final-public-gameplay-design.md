# Final Public Gameplay Design

## Problem

CL-01 through CL-06 prove the generated API architecture, multi-version
packaging, external-user surface, and local release gates. The remaining product
proof is behavioral: a Craftless-controlled Minecraft client must perform
honest survival gameplay through public generated API/CLI surfaces only.

Previous product probes invoked generated operations, but they did not prove a
full survival loop with state changes such as chat, inventory changes, block
changes, crafting/equipping, and entity interaction.

## Goal

Close CL-07 by running a final public gameplay probe that:

- uses the packaged Craftless CLI and supervisor API as an external user would;
- creates/connects a real client to a real Minecraft server;
- fetches generated per-client OpenAPI and action/resource projections;
- subscribes to public SSE or JSON-RPC subscription events before acting;
- invokes only generated operations discovered from the connected client;
- writes chat;
- observes player/world/entity and inventory state;
- mines or interacts with blocks and verifies the observed result;
- proves item pickup through inventory state change after a world action;
- crafts and equips an item through generated recipe/inventory operations;
- interacts with or attacks an entity and verifies the observed result;
- records artifacts under
  `driver-fabric/build/craftless-final-gameplay/artifacts/`.

## Non-Goals

- Do not add `task.survival.*`, `kill.cow`, `find.tree`, `craft.sword`, or any
  other static scenario shortcut.
- Do not use `/give`, creative inventory, preloaded inventory, direct Fabric
  internals, direct driver internals, or server-provisioned item state.
- Do not require human Minecraft chat confirmation.
- Do not require pathfinding if generated `navigation.*` actions are
  unavailable. Navigation is optional evidence; nearby generated query targets
  are sufficient if all required gameplay state transitions are proven.
- Do not close CL-08 from gameplay evidence alone.

## Current Generated Primitive Baseline

The representative older packaged lane currently exposes these generated
operations after connection:

- `player.chat`
- `player.query`
- `player.look`
- `player.raycast`
- `inventory.query`
- `inventory.equip`
- `recipe.query`
- `recipe.craft`
- `world.block.query`
- `world.block.break`
- `world.block.interact`
- `entity.query`
- `entity.attack`
- `navigation.plan`
- `navigation.follow`
- `navigation.stop`

The final probe must discover this from live OpenAPI. The list above is only
planning context, not a static authority. If the live client lacks a required
generic primitive, write `missing-generic-primitive:<id>` to the evidence and
do not add a scenario shortcut.

## Acceptance

- A rerunnable final gameplay probe exists and is invoked by a `mise` task.
- The probe captures connected per-client OpenAPI, actions, resources, SSE or
  subscription output, public action log, state JSONL, and server log.
- The probe rejects any `task.*` action as final gameplay proof.
- The probe records the exact generated action id and arguments for each
  gameplay step.
- The probe verifies accepted invocations from observed public state, not from
  accepted status alone.
- The evidence file
  `docs/superpowers/evidence/2026-06-28-final-public-gameplay.md` links the
  artifacts and summarizes each verified gameplay requirement.
- The checklist moves CL-07 to `[x]` only after the evidence proves all CL-07
  requirements.
