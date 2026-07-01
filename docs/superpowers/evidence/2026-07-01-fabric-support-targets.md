# Fabric Support Targets Evidence

Phase 198 adds a stable supervisor compatibility-reporting route:

```http
GET /versions/support-targets
```

The route composes Fabric's discovered Minecraft target metadata with the
configured Craftless driver mod manifest. Each Fabric target is reported as:

- `supported = true` with matching `driverMods`; or
- `supported = false` with `reason = NO_DRIVER_MOD`.

This makes support coverage explicit for agents. It does not claim that every
Fabric target launches; it makes unsupported targets machine-readable and
keeps launch truth tied to packaged driver lanes.

## Verification

```sh
mise exec -- gradle :protocol:test --tests 'com.minekube.craftless.protocol.ApiRouteCatalogTest.catalog exposes required stable session routes' --tests 'com.minekube.craftless.protocol.OpenApiGenerationTest.stable supervisor openapi describes version discovery and latest defaults' --tests 'com.minekube.craftless.protocol.OpenApiGenerationTest.stable supervisor openapi includes useful descriptions for core pillars'
```

Result: passed.

```sh
mise exec -- gradle :daemon:test --tests 'com.minekube.craftless.daemon.LocalSessionApiServerTest.server exposes Minecraft Fabric and packaged driver version discovery'
```

Result: passed.

```sh
mise exec -- gradle :protocol:test :daemon:test
```

Result: passed.

```sh
mise run docs-site-verify
```

Result: passed.

```sh
mise run package-cli
```

Result: passed. The packaged tar/zip and Docker context include driver lanes for
Fabric `1.20.6`, `1.21.6`, and `26.2`.

Packaged live smoke:

```sh
build/docker/craftless/bin/craftless daemon start --port 18102 --workspace build/fabric-support-targets-smoke/workspace
build/docker/craftless/bin/craftless api /versions/support-targets --api http://127.0.0.1:18102
```

Result: passed. The live packaged daemon returned 512 Fabric targets: 3
supported and 509 unsupported. Supported examples were `26.2`, `1.21.6`, and
`1.20.6`; unsupported examples included `26.3-snapshot-2` and
`26.3-snapshot-1` with `reason = NO_DRIVER_MOD`.

```sh
mise run ci
```

Result: passed. This included lint, unused-check/detekt, Gradle tests, packaged
Craftless CLI smoke, and Playwright distribution tests.

```sh
git diff --check
```

Result: passed.
