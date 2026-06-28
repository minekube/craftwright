# Reflective Fabric World-Change Callback Design

## Problem

The representative older `1.20.6` compile probe from Phase 134 showed the
current driver source is blocked by multiple typed `1.21.x` APIs. One blocker is
the compile-time import of Fabric API `ClientWorldEvents`, which is optional
lifecycle event plumbing rather than core gameplay API breadth.

Keeping that direct type import makes older compatible lanes fail before the
driver can even reach the larger typed Minecraft API differences.

## Goals

- Remove the compile-time dependency on
  `net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents`.
- Register the world-change callback reflectively when the runtime Fabric API
  exposes `ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE`.
- Preserve current callback registration for tick, entity load/unload, join,
  and disconnect events.
- Update the older-lane probe so `ClientWorldEvents` is no longer an expected
  blocker.

## Non-Goals

- Do not add or remove public gameplay actions/resources/routes.
- Do not claim older `1.20.6` support is complete.
- Do not change Fabric dependency defaults.
- Do not remove the callback evidence id; only make that callback optional when
  the runtime Fabric API lacks the event class.

## Design

`FabricEventCallbacks.register()` keeps direct registrations for stable event
types that still compile in the current and older lane. The world-change event
uses a private helper:

1. Load `ClientWorldEvents` and nested `AfterClientWorldChange` by name.
2. Read the static `AFTER_CLIENT_WORLD_CHANGE` event field.
3. Create a Java dynamic proxy for the listener interface.
4. Invoke the Fabric `Event.register(listener)` method reflectively.
5. Return `false` when any class/member is unavailable.

The helper records `craftless-callback-client-world-change` when the proxy is
invoked. Missing optional event support does not throw during registration.

## Acceptance Criteria

- A red source-level test fails before implementation because
  `FabricEventCallbacks.kt` imports or references `ClientWorldEvents`.
- After implementation, the source-level test passes and current callback
  source evidence tests still pass.
- `mise run fabric-lane-check-older` exits with
  `status=source-compatibility-blocked` and no longer expects
  `ClientWorldEvents` as a blocker.
- `mise run package-cli`, `git diff --check`, and `mise run ci` pass locally.
