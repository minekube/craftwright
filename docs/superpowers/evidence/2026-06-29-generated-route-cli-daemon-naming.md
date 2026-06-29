# Generated Route CLI And Daemon Naming Evidence

Phase 189 replaces hand-dispatched supervisor HTTP CLI commands with an
OpenAPI metadata interpreter while keeping the small static startup kernel.

Implemented surface:

- `OpenApiOperation` carries `x-craftless-cli` metadata generated from
  `ApiRouteCatalog`.
- `GeneratedRouteCli` loads supervisor `/openapi.json`, matches command tokens,
  expands path placeholders, builds JSON request bodies from metadata/schema
  bindings, renders route-derived help, and dispatches with Ktor.
- `craftless daemon start` is the primary advertised startup command.
- `craftless server start` remains accepted as a compatibility alias but is
  hidden from primary help and registered command paths.
- Per-client gameplay aliases still use live per-client OpenAPI as the
  authority before invoking.
- Current README, setup action, Docker entrypoint, and smoke/probe scripts use
  `daemon start`.

Focused verification:

```sh
mise exec -- gradle :protocol:test --tests com.minekube.craftless.protocol.OpenApiGenerationTest
```

Result: passed after adding `x-craftless-cli` route metadata and the JSON-RPC
route entry.

```sh
mise exec -- gradle :cli:test --tests com.minekube.craftless.cli.CraftlessCliTest
```

Result: passed after routing supervisor commands through the generated route
interpreter and preserving live per-client OpenAPI behavior.

Full verification:

```sh
git diff --check
mise run ci
```

Result: both passed. `mise run ci` included lint, detekt, full Gradle tests,
packaged CLI smoke through `craftless daemon start`, and Bun/Playwright
distribution tests.
