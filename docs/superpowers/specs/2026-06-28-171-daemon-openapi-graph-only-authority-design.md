# Daemon OpenAPI Graph-Only Authority Design

## Problem

`ClientSessionService.openApiFor(clientId)` still had a legacy fallback: if a
driver returned an empty `RuntimeCapabilityGraph`, the daemon converted
`DriverSession.actions()` into public OpenAPI actions, resources, and alias
routes.

That made `DriverSession.actions()` an independent public authority. It
preserved the stale descriptor/action-list path that Craftless is removing:
public gameplay APIs must come from the runtime capability graph and
generated per-client OpenAPI, not from a second hand-maintained action list.

## Design

The daemon must always build per-client OpenAPI from `driver.runtimeGraph()`.

`DriverSession.actions()` may remain as a compatibility projection in the
internal driver contract, but it must not publish public OpenAPI actions,
resources, aliases, routes, CLI metadata, or agent workflow metadata when the
runtime graph is empty.

The daemon-level behavior is:

1. Fetch the client and driver session.
2. Fetch `driver.runtimeGraph()`.
3. Build runtime metadata from the graph fingerprint and driver metadata.
4. Return `OpenApiDocument.fromRuntimeGraph(graph, extensions)`.
5. Derive `routesFor(clientId)` from that generated document.

Result schema projection must preserve graph result payload requiredness so
daemon result validation remains driven by generated OpenAPI metadata.

## Non-Goals

- Do not add gameplay operations.
- Do not add route families.
- Do not remove the internal `DriverSession.actions()` projection.
- Do not change Fabric gameplay adapters in this slice.
- Do not make a new Minecraft version support claim.

## Acceptance

- A descriptor-only driver session with a non-empty `actions()` list and an
  empty `runtimeGraph()` publishes no public gameplay actions, resources, or
  action aliases.
- Existing graph-backed daemon, route, alias, generic invocation, SSE, and
  JSON-RPC tests continue to pass.
- Descriptor-backed test fixtures that need public actions mirror those
  descriptors into a runtime graph instead of relying on the removed daemon
  fallback.
- Generated OpenAPI result metadata preserves required `data` payloads from
  runtime graph result schemas.
