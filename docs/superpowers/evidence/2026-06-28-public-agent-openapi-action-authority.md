# Public-Agent OpenAPI Action Authority Evidence

Date: 2026-06-28

## Scope

Phase 169 makes `PublicAgentGameplayRunner` use generated per-client OpenAPI
`x-craftless-actions` as the authority for action ids and action argument
metadata.

`GET /clients/{id}/actions` is still fetched and recorded as a projection
artifact, but it no longer decides whether the public-agent workflow can run.

This is a consumer authority cleanup only. It does not add gameplay
operations, public route shapes, CLI commands, action adapters, static action
catalogs, scenario shortcuts, version lanes, or support claims.

## Red

Command:

```sh
mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest.runner uses generated client openapi actions as authority over actions projection*'
```

Observed before implementation:

- Exit code: `1`
- After fixing a test-only compile mistake, the focused test failed because
  the runner blocked on the empty `/actions` projection instead of using
  generated OpenAPI `x-craftless-actions`.

## Green

Command:

```sh
mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest.runner uses generated client openapi actions as authority over actions projection*'
```

Observed after implementation:

- Exit code: `0`
- Gradle result: `BUILD SUCCESSFUL`

## Focused Regression

Command:

```sh
mise exec -- gradle :testkit:test --tests '*PublicAgentGameplayRunnerTest*'
```

Observed:

- Exit code: `0`
- Gradle result: `BUILD SUCCESSFUL`

## Testkit Regression

Command:

```sh
mise exec -- gradle :testkit:test
```

Observed:

- Exit code: `0`
- Gradle result: `BUILD SUCCESSFUL`

## Local CI

Command:

```sh
mise run ci
```

Observed:

- Exit code: `0`
- Gradle result: `BUILD SUCCESSFUL`
- Bun result: `19 pass`, `0 fail`

## Diff Check

Command:

```sh
git diff --check
```

Observed:

- Exit code: `0`
