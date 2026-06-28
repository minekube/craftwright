# Fabric Execution Adapter Naming Evidence

Date: 2026-06-28

## Scope

Phase 174 removes stale action-binding names from private Fabric execution
adapter production code.

The private layer still contains transitional execution adapters, and CL-02
remains open until generic Fabric discovery/projection/invocation replaces the
hand-maintained bootstrap operation definition list. This phase only removes
misleading naming that made the private execution layer look like public
descriptor/binding pairs.

This phase adds no gameplay operation, no public daemon route, no CLI command,
no scenario shortcut, no version lane, and no support claim.

## Red

Command:

```sh
mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric execution adapters do not use stale action binding names*'
```

Observed before implementation:

- Exit code: `1`
- Failure: production source still contained stale names such as
  `FabricActionBinding`, `defaultFabricActionBindings`, and
  `actionBindingsById`.

## Focused Green

Command:

```sh
mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric execution adapters do not use stale action binding names*' --tests '*FabricDriverModuleTest.fabric backend exposes bootstrap bindings as graph operation adapters*' --tests '*FabricCapabilityProbeTest.fabric capability probe context does not receive execution adapters for graph schemas*'
```

Observed after implementation:

- Exit code: `0`
- Gradle result: `BUILD SUCCESSFUL`

## Source Scan

Command:

```sh
rg -n 'FabricActionBinding|defaultFabricActionBindings|actionBindings|actionBindingsById|ActionBinding|FabricActionBindings|action bindings|action binding' driver-fabric/src/main driver-fabric/src/test -S
```

Observed after implementation:

- No production stale-name hits.
- Remaining hits are the guard test's forbidden-token list and test name.

## Final Local Verification

```sh
mise exec -- gradle :driver-fabric:test
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
