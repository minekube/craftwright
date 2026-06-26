# Legacy Survival Task API Removal Design

## Purpose

Phase 29 removes the diagnostic `task.survival.*` path from active Fabric
behavior. The survival scenario remains an acceptance test, but it must be
performed by an external public agent or adaptive CLI that composes generated
actions such as navigation, block query/break, recipe query/craft, inventory,
entity query/attack, chat, and events.

## Removed Shape

These must not remain active product behavior:

- `task.survival.honest-cow-hunt`;
- survival-specific resource handles such as `resource:survival:*`;
- a Fabric survival task executor that breaks logs, crafts weapons, equips, or
  attacks entities as a hidden macro;
- smoke harness calls that invoke `task.run` with a survival scenario id.

Historical specs and plans may mention the old diagnostic harness for
traceability. Current code, README, generated public behavior, and final
gameplay evidence must not depend on it.

## Kept Shape

`task.run` and `task.status` may remain as generic future task affordances only
when they do not contain a scenario catalog. Until a real generic task graph
executor exists, Fabric must return machine-readable unsupported results such
as `task-executor-unavailable`.

The public-agent runner in `testkit` remains the durable completion path. It may
use agent policy to choose useful categories or item labels, but it must only
invoke generated actions.

## Acceptance

- Focused tests prove `task.run` does not execute or echo
  `task.survival.honest-cow-hunt`.
- The Fabric survival executor and survival graph code are removed.
- The smoke controller no longer runs a survival task branch or writes
  `survival-task-results.jsonl`.
- Current checklist and AGENTS instructions name the old path as removed.
- Final completion still requires live public-agent gameplay and Robin's
  Minecraft chat confirmation.
