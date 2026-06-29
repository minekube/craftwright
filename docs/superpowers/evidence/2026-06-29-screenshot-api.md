# Screenshot API Evidence

## Scope

PR-ready screenshot API slice:

- generated runtime graph resource `media.screenshot`
- generated operation `media.screenshot.capture`
- generated alias route `/clients/{id}/media/screenshot:capture`
- generic invocation through `/clients/{id}:run`
- generic artifact serving through `/clients/{id}/artifacts/{artifact-id}`
- deterministic fake-driver path for offline tests

Fabric screenshot capture remains explicit follow-up adapter work.

## Verification

- Baseline:
  `mise exec -- gradle :protocol:test :driver-api:test :driver-runtime:test :daemon:test :testkit:test`
  passed before implementation.
- Protocol projection:
  `mise exec -- gradle :protocol:test --tests 'com.minekube.craftless.protocol.OpenApiGenerationTest' --rerun-tasks`
  passed. The screenshot projection test proved resource
  `media.screenshot`, operation `media.screenshot.capture`, generated alias
  `/clients/{id}/media/screenshot:capture`, and the artifact result schema.
- Fake driver red:
  `mise exec -- gradle :testkit:test --tests 'com.minekube.craftless.testkit.FakeDriverSessionTest' --rerun-tasks`
  failed before implementation because the fake runtime graph did not expose
  `media.screenshot`.
- Fake driver green:
  the same focused `:testkit:test` command passed after adding the
  runtime-graph resource, operation, and deterministic result metadata.
- Daemon artifact red:
  `mise exec -- gradle :daemon:test --tests 'com.minekube.craftless.daemon.LocalSessionApiServerTest.server serves client artifacts with traversal guards' --rerun-tasks`
  failed before implementation because `/clients/{id}/artifacts/{artifact-id}`
  was not served.
- Daemon artifact green:
  the same focused `:daemon:test` command passed after adding
  `ClientArtifactStore` and the guarded Ktor route.
- Focused suite:
  `mise exec -- gradle :protocol:test :testkit:test :daemon:test` passed.
- Lint:
  `mise run lint` passed after ktlint import/expression-body corrections.
- Full CI:
  `mise run ci` passed, including lint, unused-check/detekt, Gradle tests,
  packaged Craftless CLI smoke, and Bun Playwright tests.
- Whitespace:
  `git diff --check` passed after the CI run.

## Remaining Work

Fabric screenshot capture is not implemented in this PR. The next adapter
slice should discover `media.screenshot.capture` from the live Fabric client
capability graph, capture PNG bytes on the Minecraft client thread, write them
under the daemon-owned client artifact directory, and return the same
Craftless-owned metadata shape used by the fake driver.
