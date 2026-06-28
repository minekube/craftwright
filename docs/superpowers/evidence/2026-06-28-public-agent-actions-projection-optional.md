# Public Agent Actions Projection Optional Evidence

Date: 2026-06-28

## Scope

Phase 173 closes CL-01 by making the final public-agent action-list projection
dependency optional.

The public-agent runner still records `/actions` as projection evidence when
available, but generated per-client OpenAPI `x-craftless-actions` is the
authority for required primitive checks, argument metadata, and gameplay
planning.

This phase adds no gameplay operation, no public daemon route, no CLI command,
no Fabric adapter, no scenario shortcut, no version lane, and no support claim.
CL-02 owns the remaining Fabric bootstrap binding exit.

## Red

Command:

```sh
mise exec -- gradle :testkit:test --tests 'com.minekube.craftless.testkit.PublicAgentGameplayRunnerTest.runner does not require actions projection endpoint when live openapi has actions'
```

Observed before implementation:

- Exit code: `1`
- Failure: the runner recorded the 404/error projection body instead of the
  expected empty projection artifact `[]`.

## Focused Green

Command:

```sh
mise exec -- gradle :testkit:test --tests 'com.minekube.craftless.testkit.PublicAgentGameplayRunnerTest.runner does not require actions projection endpoint when live openapi has actions'
```

Observed after implementation:

- Exit code: `0`
- Gradle result: `BUILD SUCCESSFUL`

Command:

```sh
mise exec -- gradle :testkit:test --tests 'com.minekube.craftless.testkit.PublicAgentGameplayRunnerTest.runner uses generated client openapi actions as authority over actions projection'
```

Observed:

- Exit code: `0`
- Gradle result: `BUILD SUCCESSFUL`

## Authority Guards

Command:

```sh
mise exec -- gradle :cli:test --tests 'com.minekube.craftless.cli.CraftlessCliTest.adaptive cli does not fetch actions projection as gameplay authority'
```

Observed:

- Exit code: `0`
- Gradle result: `BUILD SUCCESSFUL`

Command:

```sh
mise exec -- gradle :daemon:test --tests 'com.minekube.craftless.daemon.HttpDriverSessionTest.http driver session does not fetch actions endpoint as remote authority'
```

Observed:

- Exit code: `0`
- Gradle result: `BUILD SUCCESSFUL`

## Remaining Production Scan

Command:

```sh
rg -n 'override fun actions\(|get\("actions"\)|"/actions"|/clients/\{id\}/actions|/clients/\$clientId/actions|fabricBootstrapOperationDefinitions|DriverActionSource\.BINDING|DriverActionDescriptor' daemon/src/main cli/src/main driver-*/src/main testkit/src/main protocol/src/main -S
```

Observed:

- `driver-api` and `driver-runtime` keep compatibility action projections
  derived from `RuntimeCapabilityGraph`.
- `protocol` and `daemon` keep the public `/clients/{id}/actions` projection
  route.
- `driver-fabric-attach` keeps loopback `/actions` as projection/debug only;
  Phase 172 proves `HttpDriverSession.actions()` no longer depends on it.
- `testkit` keeps optional public-agent projection artifact fetches; this
  phase proves they are not required.
- `driver-fabric` still contains `fabricBootstrapOperationDefinitions()`;
  that is CL-02 transitional Fabric binding-exit work.

## Final Local Verification

```sh
mise exec -- gradle :testkit:test :cli:test :daemon:test
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
- Gradle protocol, daemon, CLI, and driver-fabric checks passed.
- Bun Playwright helper/distribution tests: `19 pass`, `0 fail`.
