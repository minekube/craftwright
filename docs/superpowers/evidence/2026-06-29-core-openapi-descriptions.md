# Core OpenAPI Descriptions Evidence

## Scope

Phase 192 extends supervisor OpenAPI operation descriptions beyond client
lifecycle. Every stable supervisor route now carries a non-empty summary and a
useful description for adaptive agents and generated CLI help.

Covered core pillars:

- supervisor OpenAPI discovery and version/runtime identity;
- supervisor event listing and Server-Sent Event streaming;
- cache prepare, export, and cleanup;
- Java runtime list and resolve;
- client lifecycle list, create, get, attach, connect, and stop;
- per-client generated OpenAPI, action projection, resource projection,
  generic action invocation, JSON-RPC control/query, and client events.

The route metadata remains Craftless-owned and descriptor-oriented. It does not
add static gameplay catalogs, scenario shortcuts, or per-version public APIs.

## Red Evidence

```sh
mise exec -- gradle :protocol:test --tests com.minekube.craftless.protocol.OpenApiGenerationTest.stable\ supervisor\ openapi\ describes\ every\ core\ pillar\ route
```

Result before implementation: failed because the first undescribed stable route
had no OpenAPI summary.

## Green Evidence

```sh
mise exec -- gradle :protocol:test --tests com.minekube.craftless.protocol.OpenApiGenerationTest.stable\ supervisor\ openapi\ describes\ every\ core\ pillar\ route
```

Result after implementation: passed. Gradle reported `BUILD SUCCESSFUL in 1s`.

```sh
mise exec -- gradle :protocol:test :cli:test
```

Result: passed. Gradle reported `BUILD SUCCESSFUL in 10s`.

## Final Check

```sh
git diff --check
```

Result: passed with no output.
