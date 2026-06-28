# Graph-Owned Fabric Actions Evidence

Date: 2026-06-28

## Intent

Record Phase 77 evidence that Fabric public action descriptors now come from
the runtime capability graph rather than directly from `FabricActionBinding`
descriptors.

This phase does not remove transitional execution bindings and does not add
new gameplay actions, route families, CLI catalogs, compiled lanes, public
version-specific APIs, or Minecraft support claims.

## Red Test

Command:

```sh
mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric public actions are projected from runtime graph instead of binding descriptors*'
```

Initial result: `BUILD FAILED` as expected. The new source invariant failed
because connected Fabric public actions still exposed
`DriverActionSource.BINDING`.

## Green Test

Same command after implementation:

```sh
mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric public actions are projected from runtime graph instead of binding descriptors*'
```

Result: `BUILD SUCCESSFUL`.

## Focused Regression

Command:

```sh
mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric runtime discovery probes client state before advertising unavailable raycast*' --tests '*FabricDriverModuleTest.fabric runtime discovery exposes player query only from client state*' --tests '*FabricDriverModuleTest.fabric runtime discovery exposes inventory equip only from client state*'
```

Result: `BUILD SUCCESSFUL`.

## Full Fabric Regression

Command:

```sh
mise exec -- gradle :driver-fabric:test
```

Result: `BUILD SUCCESSFUL`.

## Local Verification

Commands:

```sh
git diff --check
mise run architecture-check
mise run ci
```

Result: all commands passed locally. `mise run architecture-check` included
protocol, daemon, CLI, full `:driver-fabric:test`, and Bun Playwright checks.
`mise run ci` completed Gradle lint, Gradle tests, and Bun Playwright tests.

## Current Boundary

Verified:

- `FabricDriverBackend.actions(clientId)` now projects from
  `runtimeGraph(clientId).operations`.
- Public Fabric action descriptors exposed by `actions()` use
  `DriverActionSource.RUNTIME_PROBE`.
- Private transitional bindings still execute current operations through the
  existing adapter/invocation paths.

Still open:

- Descriptor schemas still use hand-maintained bootstrap schema functions.
- Future gameplay breadth must come from broader generic Fabric/runtime
  discovery, not new descriptor/binding pairs.
