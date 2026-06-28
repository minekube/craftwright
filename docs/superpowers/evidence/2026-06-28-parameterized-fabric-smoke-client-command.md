# Parameterized Fabric Smoke Client Command Evidence

## Scope

Phase 140 makes the default Fabric smoke action command preserve the active
compiled lane Gradle properties when launching the inner
`:driver-fabric:runClient` command.

This prepares the harness for real older/future lane smoke. It does not itself
prove older Minecraft runtime support, launch, attach, generated OpenAPI, or
gameplay.

## Red

Command:

```sh
mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric client smoke runClient command preserves parameterized lane properties'
```

Result after correcting the test string literals: failed.

Relevant failure:

```text
FabricDriverModuleTest > fabric client smoke runClient command preserves parameterized lane properties() FAILED
```

The guard failed because `driver-fabric/build.gradle.kts` did not contain
`fabricSmokeLaneGradleProperties` or the active `craftless.fabric.*` property
arguments in the default `runClient` action command.

## Green

Command:

```sh
mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric client smoke runClient command preserves parameterized lane properties' --tests '*FabricDriverModuleTest.fabric client smoke passes runtime lane evidence before launching client'
```

Result: passed.

Evidence covered by the test:

- the build script defines `fabricSmokeLaneGradleProperties`;
- the default inner `:driver-fabric:runClient` command receives the active
  `craftless.fabric.*` lane properties;
- runtime-lane evidence remains written before launching the client;
- static historical latest/older unsupported lane ids remain absent.

## Local CI

The first `mise run ci` attempt failed in `:driver-fabric:ktlintKotlinScriptCheck`
because `jsonStringArray` used a multi-line body expression whose first line
fit on the function signature line. After applying the formatting-only fix,
the local CI gate passed.

Command:

```sh
mise run ci
```

Result: passed.

Covered gates:

- `mise exec -- gradle lint`;
- `mise run unused-check`;
- `mise exec -- gradle test`;
- `mise exec -- bun test playwright`.

Additional hygiene:

```sh
git diff --check
```

Result: passed.
