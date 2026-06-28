# Invoke Fallback Naming Removal Design

## Problem

The runtime graph now owns generic action invocation, but active source and
governance text still uses stale "old invoke" terminology for parts of that
path. That wording makes the durable `DriverSession.invoke(...)` contract look
like an old compatibility escape hatch instead of the generic dispatch
surface.

## Goals

- Remove stale old-invoke wording from active Kotlin source, active tests,
  AGENTS/checklist text, and active spec/plan docs.
- Rename test-only stale fallback counters/messages to
  `fallbackInvokeCount` and `fallback invoke should not run`.
- Keep `DriverSession.invoke(...)` as the stable generic driver invocation
  method.
- Keep historical evidence files factual; they may describe past red/green
  commands.

## Non-Goals

- Do not rename the public `invoke(...)` method.
- Do not change runtime graph dispatch behavior.
- Do not add public gameplay actions, route families, CLI catalogs, Fabric
  bindings, scenario shortcuts, version lanes, or support claims.
- Do not rewrite historical evidence records solely for wording.

## Acceptance Criteria

- A protocol policy test fails before implementation when active code/docs
  contain stale old-invoke spellings.
- After implementation, the policy test passes for active Kotlin source,
  AGENTS/checklist text, and active specs/plans.
- Focused daemon and Fabric tests that cover graph-owned dispatch continue to
  pass under their renamed test names.
- `git diff --check` and `mise run ci` pass locally.
