# Official Fabric JSON-RPC Query Evidence Design

## Goal

Make the latest/current official Fabric attach probe capture public
`POST /clients/{id}:rpc` JSON-RPC `query` evidence from a real connected
official client, proving generated OpenAPI projections are available through
the transport path intended for generated clients and agents.

## Problem

The official 26.x lane now records launch, attach, connected client-state,
connected server-feature, registry, event-source, public SSE lifecycle, and
public REST projection endpoint evidence. It still does not prove that the same
connected official client can answer JSON-RPC `query` requests for live
per-client projections.

Without this evidence, the latest/current lane has weaker proof for the
documented control model: HTTP POST JSON-RPC-style requests for client-to-server
control and query, with SSE used for outbound event streams.

## Design

- Reuse the existing daemon JSON-RPC endpoint:
  - `POST /clients/{id}:rpc`
- Reuse existing JSON-RPC `query` targets:
  - `openapi`
  - `actions`
  - `resources`
- Extend only the official attach probe evidence harness.
- Write raw JSON-RPC response bodies to:
  - `client-rpc-openapi.json`
  - `client-rpc-actions.json`
  - `client-rpc-resources.json`
- Record a compact summary in `probe-result.json`:
  - `rpcQueryTargets`
  - `rpcActionCount`
  - `rpcResourceIds`
- Preserve `actions=0`: this phase proves the transport over generated live
  projections, not official gameplay breadth.

## Boundaries

- No static gameplay actions.
- No action descriptors, operation adapters, invocation adapters, CLI gameplay
  commands, route families, or scenario shortcuts.
- No copied Yarn/remap gameplay gateway.
- No new product endpoint; use the existing daemon JSON-RPC route.
- No official driver packaging/support claim.
- No final latest/current gameplay support claim.
- No JSON-RPC target that bypasses the generated per-client OpenAPI projection
  model.

## Acceptance

- A red artifact check fails before implementation because the connected
  official probe does not write JSON-RPC query artifacts.
- The connected official attach probe writes all three JSON-RPC response
  artifacts.
- The JSON-RPC `actions` response has `result=[]`.
- The JSON-RPC `resources` response has resource ids including `runtime`,
  `registry`, `event`, `client`, `player`, `inventory`, `world`, and `entity`.
- `probe-result.json` includes `rpcQueryTargets`, `rpcActionCount=0`, and
  `rpcResourceIds`.
- Focused official tests, latest official lane check, local CI, and
  `git diff --check` pass through mise.
