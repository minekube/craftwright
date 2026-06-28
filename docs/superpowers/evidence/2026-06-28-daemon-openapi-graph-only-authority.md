# Daemon OpenAPI Graph-Only Authority Evidence

Date: 2026-06-28

## Scope

Phase 171 removes the daemon fallback that converted `DriverSession.actions()`
into public per-client OpenAPI when `runtimeGraph()` was empty.

Generated per-client OpenAPI, `/actions`, `/resources`, alias routes, CLI
metadata, and agent workflow metadata must derive from the runtime capability
graph. `DriverSession.actions()` is not an independent public authority.

This phase also preserves graph result payload requiredness in generated
OpenAPI so daemon result validation stays metadata-driven.

This phase adds no gameplay operation, no public route family, no CLI command,
no Fabric adapter, no scenario shortcut, no version lane, and no support claim.

## Red

Command:

```sh
mise exec -- gradle :daemon:test --tests '*ClientSessionServiceTest.descriptor only actions are not public openapi authority when runtime graph is empty*'
```

Observed before implementation:

- Exit code: `1`
- Failure: descriptor-only `actions()` still published `player.chat` through
  generated OpenAPI/alias route fallback.

## Focused Green

Command:

```sh
mise exec -- gradle :daemon:test --tests '*ClientSessionServiceTest.descriptor only actions are not public openapi authority when runtime graph is empty*'
```

Observed after removing the fallback:

- Exit code: `0`
- Gradle result: `BUILD SUCCESSFUL`

## Local Session Fixture Regression

Command:

```sh
mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.server rejects unavailable discovered actions before driver invocation*' --tests '*LocalSessionApiServerTest.server validates generic invocation through generated openapi authority before driver invocation*' --tests '*LocalSessionApiServerTest.server returns generic action result data payload*' --tests '*LocalSessionApiServerTest.server rejects driver results that violate advertised result schema*' --tests '*LocalSessionApiServerTest.server dispatches nested generated action aliases*' --tests '*LocalSessionApiServerTest.server records action events from operation ids*'
```

Observed after converting stale descriptor-backed fixtures to runtime graph
fixtures:

- Exit code: `0`
- Gradle result: `BUILD SUCCESSFUL`

## Full Daemon Regression

Command:

```sh
mise exec -- gradle :daemon:test
```

Observed:

- Exit code: `0`
- Gradle result: `BUILD SUCCESSFUL`

## Final Local Gates

Command:

```sh
mise exec -- gradle :protocol:test :daemon:test
```

Observed:

- Exit code: `0`
- Gradle result: `BUILD SUCCESSFUL`

Command:

```sh
git diff --check
```

Observed:

- Exit code: `0`

Command:

```sh
mise run architecture-check
```

Observed:

- Exit code: `0`
- Gradle protocol, daemon, CLI, driver-fabric checks passed.
- Bun Playwright helper/distribution tests: `19 pass`, `0 fail`.

Command:

```sh
mise run ci
```

Observed:

- Exit code: `0`
- `lint` passed.
- `unused-check` passed through Detekt.
- `mise exec -- gradle test` passed.
- Bun Playwright helper/distribution tests: `19 pass`, `0 fail`.
