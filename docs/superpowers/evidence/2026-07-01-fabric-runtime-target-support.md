# Fabric Runtime Target Support Evidence

Phase 200 makes `/versions/support-targets` expose explicit loader/runtime
support rows through `runtimeTargets` and moves the packaged matrix runner onto
that public contract.

## Verification

```sh
mise exec -- gradle :protocol:test --tests com.minekube.craftless.protocol.OpenApiGenerationTest
```

Result: passed.

```sh
mise exec -- gradle :daemon:test --tests com.minekube.craftless.daemon.LocalSessionApiServerTest
```

Result: passed.

```sh
bash -n scripts/packaged-fabric-supported-matrix-probe.sh
```

Result: passed.

```sh
mise exec -- bun test playwright/src/distribution.test.ts
```

Result: passed.

```sh
mise run package-cli
```

Result: passed.

```sh
mise run docs-site-verify
```

Result: passed. This regenerated and verified the docs-site supervisor OpenAPI
snapshot with the new `runtimeTargets` schema.

```sh
CRAFTLESS_PACKAGED_MATRIX_DISCOVERY_ONLY=1 \
CRAFTLESS_PACKAGED_MATRIX_TIMEOUT_MS=120000 \
bash scripts/packaged-fabric-supported-matrix-probe.sh
```

Result: passed. The generated `probe-jobs.json` contained three supported
runtime target jobs discovered from packaged `/versions/support-targets`:
`26.2` through `latest-release` with Fabric Loader `0.19.3` and Java 25,
`1.21.6` with Fabric Loader `0.19.3` and Java 21, and `1.20.6` with Fabric
Loader `0.19.3` and Java 21.

```sh
git diff --check
```

Result: passed.

```sh
mise run ci
```

Result: passed. This covered lint/ktlint, detekt, Gradle tests, packaged
Craftless smoke, and Bun Playwright tests. During verification, the namespace
policy scanner was hardened to ignore the existing local
`.craftless-connect-debug-latest` debug cache just like
`.craftless-connect-debug`, so local CI can run without deleting user/debug
artifacts.

```sh
gh workflow run fabric-support-matrix.yml --repo minekube/craftless --ref main
gh run watch 28548727608 --repo minekube/craftless --exit-status
```

Result: passed. The `fabric support matrix` workflow completed successfully on
commit `6511765d36bd35594b24d2159971c000dd244a89` at
<https://github.com/minekube/craftless/actions/runs/28548727608>. It uploaded
the `fabric-support-matrix-reports` artifact as artifact ID `8023189487`
(`348233358` bytes), preserving the packaged support-target reports from the
runner.

## Current Scope

This phase does not claim every Fabric Loader version works for every Fabric
game version. It makes the supported runtime identities explicit and keeps
unsupported game targets machine-readable with `NO_DRIVER_MOD`. The larger
goal remains open until every discoverable Fabric loader/runtime combination is
either supported through a proved driver lane or rejected with a precise
machine-readable reason.
