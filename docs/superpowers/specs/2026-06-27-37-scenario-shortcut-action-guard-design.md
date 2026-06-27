# Phase 37: Scenario Shortcut Action Guard Design

## Problem

The project guardrails forbid turning survival acceptance steps into durable
public actions such as `find.tree`, `mine.log`, `craft.sword`, or `kill.cow`.
Phase 36 rejected the removed `task.survival.*` namespace, but the shared
public action-id validator still accepted those scenario-shaped shortcut ids.
That left a regression path where a future static descriptor could pass
protocol validation before a policy test or review caught it.

## Design

Reject known scenario shortcut action ids in the shared Craftless action-id
validator used by OpenAPI action descriptors, runtime graph operation nodes,
handles, events, generated aliases, and resource descriptors.

This is a protocol guardrail only:

- it does not add generated gameplay actions;
- it does not remove generic primitives such as `recipe.craft`,
  `inventory.equip`, `world.block.break`, or `entity.attack`;
- it does not inspect agent policy artifacts;
- it keeps public gameplay breadth dependent on runtime graph discovery and
  generated per-client OpenAPI.

## Acceptance

- `OpenApiAction(id = "find.tree")` fails validation.
- `OpenApiAction(id = "mine.log")` fails validation.
- `OpenApiAction(id = "craft.sword")` fails validation.
- `OpenApiAction(id = "kill.cow")` fails validation.
- `RuntimeOperationNode(id = <shortcut>)` fails validation for the same
  shortcut ids.
- Existing generic actions remain valid and the protocol suite passes.
