# TypeScript SDK And Playwright Integration Plan

Date: 2026-06-25

## Purpose

The TypeScript packages are protocol consumers. They do not own Minecraft
client lifecycle behavior and they do not parse human CLI output. They speak to
the JVM daemon and, later, the generated per-session client API.

## Packages

- `ts-sdk/` publishes the first `@craftwright/client` shape.
- `playwright/` publishes the first `@craftwright/playwright` fixture and
  matcher helpers.

Both packages are tested with Bun:

```sh
mise exec -- bun test ts-sdk
mise exec -- bun test playwright
```

`mise exec -- bun test ts-sdk` includes a live smoke test that starts
`mcw clients api` through `mise exec -- gradle -q :cli:run --args=clients api`,
then uses the SDK against the running JVM daemon.

Full repository CI runs through mise as well:

```sh
mise run ci
```

## Current SDK Surface

The initial SDK is intentionally route-focused:

- `createCraftwright({ baseUrl, fetch })`
- `start({ baseUrl, fetch })`
- `mc.version()`
- `mc.openapi()`
- `mc.launch({ name, id, version, loader, offline })`
- `mc.client(id)`
- `client.connect(host, port)`
- `client.chat(message)`
- `client.command(command)`
- `client.waitForChat(pattern, { timeoutMs, intervalMs })`
- `client.player()`
- `client.stop()`

`launch()` maps to `POST /clients`, matching the daemon's current client
creation contract. Player methods map to session-scoped routes under
`/clients/{id}/...`, which is the route shape the daemon and generated client
API should converge on as the real driver lands.

The daemon fake-session API currently implements the SDK smoke routes:

- `POST /clients/{id}/connection/connect`
- `POST /clients/{id}/player/sendChat`
- `GET /clients/{id}/player`
- `POST /clients/{id}/stop`
- `GET /clients/{id}/events`

These routes update in-memory client state and session events. They are not a
real Minecraft driver yet, but they let the CLI, SDK, fixtures, and OpenAPI
metadata converge on one contract before the Fabric driver replaces fake state.

## Playwright Direction

The Playwright package currently exports low-dependency helpers:

- `createCraftwrightFixture({ sdk })`
- `toHaveChat(player, pattern)`

The fixture accepts an injected SDK for tests and defaults to
`createCraftwright()` for runtime use. This keeps the adapter bound to the
TypeScript SDK instead of shelling out to `mcw` or parsing CLI text.

When `@playwright/test` is added, this package should layer these helpers into
`test.extend({ mc })` and `expect.extend({ toHaveChat })` without changing the
SDK contract.

## Next Steps

- Generate typed SDK methods from `/openapi.json` instead of hand-writing route
  strings.
- Add Playwright Test peer integration once package publishing shape and CI
  install strategy are settled.
- Add Vitest-compatible helpers that reuse the SDK without duplicating route
  logic.
