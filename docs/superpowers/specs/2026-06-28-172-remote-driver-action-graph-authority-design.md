# Remote Driver Action Graph Authority Design

## Problem

`HttpDriverSession.actions()` still fetched `GET /actions` from an attached
driver loopback endpoint.

That made the attach transport another action-list authority path. Even after
daemon OpenAPI generation moved to `runtimeGraph()`, a remote attached driver
session could still depend on a separate action descriptor endpoint for
`DriverSession.actions()`.

Craftless is trying to remove that shape: action descriptors are projections
of the runtime capability graph and generated per-client OpenAPI, not a
separate remote session contract.

## Design

`HttpDriverSession` should inherit the shared `DriverSession.actions()`
default. That default fetches `runtimeGraph()` and projects graph operations
into `DriverActionDescriptor` compatibility views.

The remote attach transport remains lifecycle/control oriented:

1. `snapshot`
2. `connect`
3. `runtime-metadata`
4. `runtime-graph`
5. `invoke`
6. `stop`
7. `events`

If a loopback endpoint still exposes `/actions`, it is only a projection/debug
convenience. It must not be required for public gameplay, generated OpenAPI,
CLI metadata, public-agent workflows, or remote `DriverSession.actions()`.

## Non-Goals

- Do not add or remove public daemon route families.
- Do not add gameplay operations.
- Do not change Fabric gameplay adapters.
- Do not remove `DriverSession.actions()` as a compatibility projection.
- Do not claim CL-01 is fully complete.
- Do not make a new Minecraft version support claim.

## Acceptance

- A remote HTTP driver session with a populated `runtime-graph` endpoint and a
  failing `/actions` endpoint still returns projected action descriptors from
  `remote.actions()`.
- The failing `/actions` endpoint is not called by `HttpDriverSession.actions()`.
- Existing Fabric self-attach tests continue to pass.
- Production `HttpDriverSession` has no direct `get("actions")` override.
