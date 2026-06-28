# Packaged Driver Mod Distribution Evidence

## Scope

Phase 97 wires the packaged CLI/Docker runtime path so the Fabric driver mod is
available as a runtime artifact and `CRAFTLESS_FABRIC_DRIVER_MOD` reaches the
daemon provider.

This phase does not add public gameplay actions, static descriptors, generated
route families, CLI gameplay catalogs, Fabric bindings, version-specific APIs,
or Minecraft support claims.

## Red

CLI env/cache wiring red command:

```sh
mise exec -- gradle :cli:test --tests '*CraftlessCliTest.server start forwards configured fabric driver mod environment*'
```

Expected failure observed before implementation:

```text
HttpRequestTimeoutException
```

After threading the fake cache metadata but before fixing the runtime fixture,
the test produced a fast 400 with:

```text
Java runtime manifest for mac-os-arm64/java-runtime-gamma was not found
```

The fixture was then aligned with the daemon test shape.

Package policy red command:

```sh
mise exec -- gradle :protocol:test --tests '*NamespacePolicyTest.package cli stages craftless fabric driver mod for docker runtime*'
```

Expected failure observed before implementation:

```text
package-cli must build the remapped Fabric driver mod jar
```

## Green

Focused green commands:

```sh
mise exec -- gradle :cli:test --tests '*CraftlessCliTest.server start forwards configured fabric driver mod environment*'
mise exec -- gradle :protocol:test --tests '*NamespacePolicyTest.package cli stages craftless fabric driver mod for docker runtime*'
```

Results:

```text
:cli:test focused: BUILD SUCCESSFUL
:protocol:test focused: BUILD SUCCESSFUL
```

## Package And Docker Smoke

Commands:

```sh
mise run package-cli
test -f build/docker/craftless/mods/craftless-driver-fabric.jar
jar tf build/docker/craftless/mods/craftless-driver-fabric.jar | rg '^fabric\.mod\.json$|CraftlessFabricClientEntrypoint'
docker build -t craftless:local .
docker run --rm craftless:local /bin/sh -lc 'test "$CRAFTLESS_FABRIC_DRIVER_MOD" = /opt/craftless/mods/craftless-driver-fabric.jar && test -f "$CRAFTLESS_FABRIC_DRIVER_MOD" && /opt/craftless/bin/craftless server start --once --port 0 --workspace /tmp/craftless'
```

Results:

```text
mise run package-cli: BUILD SUCCESSFUL
staged driver jar: 376K build/docker/craftless/mods/craftless-driver-fabric.jar
jar contents include fabric.mod.json and CraftlessFabricClientEntrypoint.class
docker build -t craftless:local .: success
docker run smoke returned {"ok":true,...,"workspace":"/tmp/craftless"}
```

An extra in-container jar-content check was attempted and failed because the
runtime image uses a JRE and does not include the `jar` tool. The host-side
package smoke verifies the staged jar contents.

## Local Gates

Commands:

```sh
git diff --check
mise exec -- gradle :cli:ktlintCheck :cli:detekt :protocol:ktlintCheck :protocol:detekt
```

Results:

```text
git diff --check: exit 0
ktlint/detekt gate: BUILD SUCCESSFUL
```

Remote CI was not used as a blocking gate for this phase.
