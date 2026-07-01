# Fabric Loader Runtime Matrix Evidence

Phase 201 makes `/versions/support-targets` enumerate discovered Fabric Loader
runtime rows for every Fabric game target instead of listing only packaged
driver rows.

## Verification

```sh
mise exec -- gradle :protocol:test --tests com.minekube.craftless.protocol.OpenApiGenerationTest
```

Result: passed after adding `loaderStable` to the OpenAPI schema and
`NO_COMPATIBLE_DRIVER_MOD` to the support reason enum.

```sh
mise exec -- gradle :daemon:test --tests 'com.minekube.craftless.daemon.LocalSessionApiServerTest.server exposes Minecraft Fabric and packaged driver version discovery'
```

Result: passed. The route test now proves a supported game target includes a
supported loader row plus an unsupported loader row with
`NO_COMPATIBLE_DRIVER_MOD`, while an unsupported game target includes all
discovered loader rows with `NO_DRIVER_MOD`.

```sh
mise run docs-site-verify
```

Result: passed. This regenerated the docs-site supervisor OpenAPI snapshot with
the new `loaderStable` schema field and support reason enum value.

```sh
mise run package-cli
```

Result: passed.

```sh
CRAFTLESS_PACKAGED_MATRIX_DISCOVERY_ONLY=1 \
CRAFTLESS_PACKAGED_MATRIX_TIMEOUT_MS=120000 \
bash scripts/packaged-fabric-supported-matrix-probe.sh
```

Result: passed. The packaged daemon reported `512` Fabric game targets and
`128512` runtime rows (`251` Fabric Loader rows per target), with `3`
supported packaged driver rows and `128509` explicit unsupported rows. The
planner still produced `3` supported probe jobs, one for each packaged driver
runtime row.

```sh
mise run ci
```

Result: passed. This covered lint/ktlint, detekt, Gradle tests, packaged
Craftless smoke, and Bun Playwright tests after the loader-row matrix expansion.

## Current Scope

This phase still does not claim every Fabric Loader version launches. It makes
unsupported loader identities explicit and machine-readable so the remaining
matrix work can prove or reject concrete loader/runtime combinations without
ambiguity.
