# Driver API Module Instructions

`driver-api/` owns the stable JVM contract between daemon/runtime code and any
in-client automation implementation.

## Scope

- `DriverSession` and stable driver-facing DTOs.
- Action descriptors and invocation results.

## Rules

- Keep the public driver contract small and descriptor-driven: runtime metadata,
  action discovery, generic action invocation, events, session state, and
  lifecycle.
- Do not grow one stable Kotlin method per Minecraft action as the public API.
  Internal convenience methods are acceptable only when they do not leak into
  daemon routes, CLI commands, or public docs as the action model.
- Action IDs and DTOs must be Craftless-owned.
- Preserve typed `JsonElement` action args.

## Verification

```sh
mise exec -- gradle :driver-api:test
```
