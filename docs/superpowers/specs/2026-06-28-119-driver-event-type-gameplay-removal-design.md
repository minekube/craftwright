# Driver Event Type Gameplay Removal Design

## Problem

After removing result-level event classification, the stable driver event enum
still exposes gameplay-specific values:

- `DriverEventType.CHAT`
- `DriverEventType.MOVEMENT`

Those values make the driver contract look like it owns static gameplay event
categories. Public action events are already represented by generated
operation ids in daemon session/live events. The driver event enum should stay
limited to lifecycle and system error events.

## Goals

- Remove `CHAT` and `MOVEMENT` from `DriverEventType`.
- Stop fake/loopback driver sessions from recording accepted gameplay action
  events into raw `DriverEvent` lists.
- Preserve lifecycle driver events such as client created, connected, stopped,
  and error.
- Keep generated action event evidence in daemon session events and SSE paths,
  not in the raw driver event enum.

## Non-Goals

- Do not remove local server evidence types such as chat/movement; those
  describe server log evidence, not the stable driver action API.
- Do not add a replacement gameplay event enum or string catalog.
- Do not add gameplay actions, route families, CLI gameplay catalogs, Fabric
  bindings, scenario shortcuts, version-specific APIs, or support claims.

## Acceptance Criteria

- A focused driver API contract test fails before implementation because
  `DriverEventType` still includes `CHAT` and `MOVEMENT`.
- After implementation, `DriverEventType.values()` contains lifecycle/system
  values only.
- Fake driver and loopback self-attach tests no longer expect raw driver chat
  or movement events for accepted action invocations.
- Error and lifecycle event tests continue to pass.
- AGENTS/checklist/evidence record Phase 119 and keep action events
  operation-id-owned at daemon/live-event level.
