# Smoke Bootstrap Action Id Source Ownership Design

## Problem

`FabricClientSmokeController` still repeats bootstrap gameplay action ids in
diagnostic action calls and public-agent primitive checks. Those ids are already
owned by `FabricBootstrapOperationIds`; the smoke harness should consume that
source instead of carrying another action list.

This is evidence-harness code, not public API, but it still matters because the
final gameplay evidence must prove the generated API path rather than teaching
future work to add or maintain static action catalogs.

## Goals

- Replace quoted bootstrap action ids in `FabricClientSmokeController.kt` with
  `FabricBootstrapOperationIds` constants.
- Add a source guard that fails if the smoke controller reintroduces those
  quoted bootstrap action-id literals.
- Keep generated OpenAPI, invocation behavior, smoke action ordering, and
  evidence artifacts unchanged.

## Non-Goals

- Do not add gameplay actions, route families, CLI commands, generated aliases,
  Fabric execution adapters, version lanes, or support claims.
- Do not rewrite the smoke controller into a full external agent in this phase.
- Do not remove the transitional bootstrap operation definitions.
- Do not claim the broader generated-discovery exit is complete.

## Acceptance Criteria

- `FabricClientSmokeController.kt` no longer contains quoted bootstrap action
  ids such as `player.chat`, `entity.query`, or `world.block.break`.
- Smoke action calls and required primitive checks use
  `FabricBootstrapOperationIds` constants.
- Existing smoke helper tests and full local gates still pass.
- AGENTS, checklist, plan, and evidence record Phase 90 and keep the broader
  generated-discovery blocker active.
