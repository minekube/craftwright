# Official Fabric JSON-RPC Subscription SSE Evidence Design

## Goal

Make the latest/current official Fabric attach probe capture public JSON-RPC
`subscribe`/`unsubscribe` evidence and prove the returned subscription id can
filter `GET /clients/{id}/events:stream` SSE output for the connected official
client.

## Problem

The official 26.x lane now proves connected SSE lifecycle streaming and
JSON-RPC `query` access to live OpenAPI projections. It still does not prove
the intended generated-client control pattern that combines:

- HTTP `POST /clients/{id}:rpc` for subscription control;
- `GET /clients/{id}/events:stream?subscriptionId=...` for filtered
  server-to-client events;
- JSON-RPC `query` target `subscriptions` for discoverable active filters;
- JSON-RPC `unsubscribe` for cleanup.

Without this evidence, latest/current transport proof is missing the bridge
between JSON-RPC control and SSE delivery.

## Design

- Reuse the existing daemon JSON-RPC endpoint:
  - `POST /clients/{id}:rpc`
- Reuse the existing SSE endpoint:
  - `GET /clients/{id}/events:stream?subscriptionId={subscriptionId}`
- Extend only the official attach probe evidence harness.
- Subscribe to the Craftless-owned lifecycle event type `client.connected`.
- Write raw response artifacts:
  - `client-rpc-subscribe.json`
  - `client-events-subscription-stream.sse`
  - `client-rpc-subscriptions.json`
  - `client-rpc-unsubscribe.json`
  - `client-rpc-subscriptions-after-unsubscribe.json`
- Record compact summary fields in `probe-result.json`:
  - `rpcSubscriptionId`
  - `rpcSubscriptionEventTypes`
  - `rpcSubscriptionCount`
  - `rpcSubscriptionCountAfterUnsubscribe`
- Preserve `actions=0`: this phase proves transport filtering and lifecycle
  event delivery, not official gameplay breadth.

## Boundaries

- No static gameplay actions.
- No action descriptors, operation adapters, invocation adapters, CLI gameplay
  commands, route families, or scenario shortcuts.
- No copied Yarn/remap gameplay gateway.
- No new product endpoint; use existing JSON-RPC and SSE routes.
- No official driver packaging/support claim.
- No final latest/current gameplay support claim.
- No raw Fabric/Yarn/Minecraft event names in the public evidence path.

## Acceptance

- A red artifact check fails before implementation because the connected
  official probe does not write subscription/SSE filter artifacts.
- The connected official attach probe writes all five subscription artifacts.
- `client-rpc-subscribe.json` contains a `subscriptionId` and a filter with
  type `client.connected`.
- `client-events-subscription-stream.sse` contains `event: client.connected`
  and excludes `client.created` and `client.attached`.
- `client-rpc-subscriptions.json` contains the active subscription.
- `client-rpc-unsubscribe.json` reports `unsubscribed=true`.
- `client-rpc-subscriptions-after-unsubscribe.json` has an empty result array.
- `probe-result.json` includes the subscription id, filtered event types,
  subscription count before unsubscribe, and count after unsubscribe.
- Focused official tests, latest official lane check, local CI, and
  `git diff --check` pass through mise.
