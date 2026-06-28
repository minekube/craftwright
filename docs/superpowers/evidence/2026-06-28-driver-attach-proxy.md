# Driver Attach Proxy Evidence

## Scope

Phase 98 adds supervisor attach/proxy plumbing. A prepared client session can
be replaced by an attached HTTP-backed driver session, and generated OpenAPI
plus `POST /clients/{id}:run` use the attached driver.

This phase does not implement the Fabric in-client HTTP endpoint and does not
add public gameplay actions, static descriptors, CLI gameplay catalogs,
scenario shortcuts, Fabric bindings, version-specific APIs, or Minecraft
support claims.

## Red

Service attach red command:

```sh
mise exec -- gradle :daemon:test --tests '*ClientSessionServiceTest.attached driver replaces prepared session as openapi authority*'
```

Expected failure observed before implementation:

```text
Unresolved reference 'attachDriver'
```

HTTP attach red command:

```sh
mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.server attach proxies generated run calls to remote driver endpoint*'
```

Expected failure observed before implementation:

```text
expected: <200 OK> but was: <404 Not Found>
```

OpenAPI route red command:

```sh
mise exec -- gradle :protocol:test --tests '*OpenApiGenerationTest.stable lifecycle routes describe create and connect request bodies*'
```

Expected failure observed before implementation:

```text
attach request schema was missing from /clients/{id}:attach
```

## Green

Focused green commands:

```sh
mise exec -- gradle :daemon:test --tests '*ClientSessionServiceTest.attached driver replaces prepared session as openapi authority*'
mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.server attach proxies generated run calls to remote driver endpoint*'
mise exec -- gradle :protocol:test --tests '*OpenApiGenerationTest.stable lifecycle routes describe create and connect request bodies*' --tests '*OpenApiGenerationTest.stable lifecycle routes describe client response bodies*' --tests '*OpenApiGenerationTest.stable routes describe machine readable error responses*'
```

Results:

```text
focused daemon service attach: BUILD SUCCESSFUL
focused daemon HTTP attach: BUILD SUCCESSFUL
focused protocol OpenAPI routes: BUILD SUCCESSFUL
```

The HTTP attach test proves:

- `POST /clients/{id}:attach` accepts a loopback driver endpoint;
- missing-client attach returns `MISSING_CLIENT` with 404;
- `/clients/{id}/openapi.json` projects attached runtime metadata/actions;
- `POST /clients/{id}:run` invokes the attached HTTP driver endpoint.

## Local Gates

Commands:

```sh
git diff --check
mise exec -- gradle :daemon:test :protocol:test
mise exec -- gradle :daemon:ktlintCheck :daemon:detekt :protocol:ktlintCheck :protocol:detekt
```

Results:

```text
git diff --check: exit 0
:daemon:test :protocol:test: BUILD SUCCESSFUL
:daemon:ktlintCheck :daemon:detekt :protocol:ktlintCheck :protocol:detekt: BUILD SUCCESSFUL
```

Remote CI was not used as a blocking gate for this phase.
