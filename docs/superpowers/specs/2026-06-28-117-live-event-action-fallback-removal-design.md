# Live Event Action Fallback Removal Design

## Problem

Live event normalization still maps raw session event types to concrete
gameplay action ids:

- `type == "chat"` falls back to `player.chat`;
- `type == "movement"` falls back to `player.move`.

Normal action execution already supplies `operationId`, so these fallbacks are
unnecessary. Keeping them creates a small static gameplay catalog leak in the
transport layer and can make non-action legacy events look like generated
runtime actions.

## Goals

- Remove concrete gameplay action-id fallback mapping from live event
  normalization.
- Keep action invocation events typed by the supplied `operationId`.
- Keep raw non-action session events as system events unless their type is
  already a valid Craftless action id.
- Add a repository policy guard so the daemon cannot reintroduce
  `chat -> player.chat` or `movement -> player.move` fallback literals.

## Non-Goals

- Do not remove generated action events from `POST /clients/{id}:run`,
  JSON-RPC invoke, or generated alias routes.
- Do not add new gameplay actions, generated route families, CLI gameplay
  catalogs, Fabric gameplay bindings, scenario shortcuts, or version-specific
  APIs.
- Do not change the SSE or JSON-RPC wire formats.

## Acceptance Criteria

- A source-level architecture test fails before implementation because
  `LocalSessionApiServer` contains the static fallback mappings.
- After implementation, live event type normalization uses:
  - the event `type` if it is already a Craftless action id;
  - otherwise `system.error` for errors;
  - otherwise `operationId` if present;
  - otherwise `system.event`.
- Existing SSE and JSON-RPC action event tests still pass because they carry
  explicit `operationId`.
- AGENTS/checklist/evidence record Phase 117 and keep generated gameplay
  breadth owned by the runtime graph.
