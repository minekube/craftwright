# Create Client Loader Version Evidence

## Scope

Phase 123 adds optional `loaderVersion` to supervisor client creation and
threads it into cache/runtime preparation. This is runtime-lane selection
plumbing for version-aware launches. It does not add gameplay APIs, compiled
lanes, scenario shortcuts, or new Minecraft support claims.

## Red

Protocol command:

```sh
mise exec -- gradle :protocol:test --tests '*ClientModelsTest.*' --tests '*OpenApiGenerationTest.*'
```

Result: failed before implementation because `CreateClientRequest` had no
`loaderVersion` parameter/property.

Daemon command:

```sh
mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.*loader version*'
```

Result: failed before daemon pass-through because a create-client request with
`loaderVersion = 0.16.14` still prepared the default `0.17.2` loader lane.

## Green

Protocol command:

```sh
mise exec -- gradle :protocol:test --tests '*ClientModelsTest.*' --tests '*OpenApiGenerationTest.*'
```

Result: passed after adding `CreateClientRequest.loaderVersion`, validation,
and the OpenAPI create-client schema property.

Daemon command:

```sh
mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.*loader version*'
```

Result: passed after passing `CreateClientRequest.loaderVersion` to
`CachePrepareRequest`.

## Local Gates

Commands:

```sh
git diff --check
mise run ci
```

Result: passed. `mise run ci` completed Gradle lint, unused-check, Gradle
tests, and Bun Playwright tests successfully.
