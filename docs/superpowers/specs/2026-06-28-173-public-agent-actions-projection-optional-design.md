# Public Agent Actions Projection Optional Design

## Problem

`PublicAgentGameplayRunner` already used generated per-client OpenAPI
`x-craftless-actions` as the action metadata authority, but it still required
`GET /clients/{id}/actions` to return a successful body before gameplay could
continue.

That left one remaining action-list authority dependency in the public-agent
workflow. `/actions` is allowed as a projection/evidence endpoint, but public
gameplay must not require it when generated OpenAPI already contains the live
action metadata.

## Design

Keep fetching `/actions` as an optional projection artifact, but treat failure
as an empty projection artifact:

1. Fetch supervisor OpenAPI.
2. Fetch generated per-client OpenAPI.
3. Try to fetch `/clients/{id}/actions`.
4. If the action projection returns non-2xx or an IO failure, store `[]`.
5. Fetch SSE evidence.
6. Use only generated OpenAPI `x-craftless-actions` for required primitive
   checks and invocation planning.

This preserves useful projection artifacts without making them part of the
public-agent authority chain.

## Non-Goals

- Do not remove the daemon `/actions` projection route.
- Do not remove the action projection artifact.
- Do not add gameplay operations.
- Do not change survival scenario composition.
- Do not touch Fabric bootstrap bindings; that is CL-02.
- Do not make a new Minecraft version support claim.

## Acceptance

- Public-agent gameplay succeeds when generated OpenAPI contains actions and
  `/actions` returns an empty list.
- Public-agent gameplay also succeeds when generated OpenAPI contains actions
  and `/actions` returns 404.
- The recorded action projection body is `[]` when the projection endpoint is
  absent.
- CLI and HTTP remote guards prevent new `/actions` authority fetches.
- The remaining production `/actions` hits are projection/debug routes,
  compatibility projections from runtime graph, or CL-02 Fabric bootstrap
  work.
