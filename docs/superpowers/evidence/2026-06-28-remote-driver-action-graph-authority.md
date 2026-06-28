# Remote Driver Action Graph Authority Evidence

Date: 2026-06-28

## Scope

Phase 172 removes a remaining remote action-list authority path:
`HttpDriverSession.actions()` no longer fetches `GET /actions` from an
attached driver endpoint.

Remote driver sessions now use the shared `DriverSession.actions()` default,
which projects descriptors from `runtimeGraph()`.

This phase adds no gameplay operation, no public daemon route, no CLI command,
no Fabric adapter, no scenario shortcut, no version lane, and no support claim.
CL-01 remains open until the remaining action-list authority scan and guards
are complete.

## Red

Command:

```sh
mise exec -- gradle :daemon:test --tests 'com.minekube.craftless.daemon.HttpDriverSessionTest.remote actions derive from runtime graph without calling actions endpoint'
```

Observed before implementation:

- Exit code: `1`
- Failure: `JsonDecodingException` because `HttpDriverSession.actions()`
  fetched the failing `/actions` endpoint instead of deriving descriptors from
  `/runtime-graph`.

## Focused Green

Command:

```sh
mise exec -- gradle :daemon:test --tests 'com.minekube.craftless.daemon.HttpDriverSessionTest.remote actions derive from runtime graph without calling actions endpoint'
```

Observed after removing the override:

- Exit code: `0`
- Gradle result: `BUILD SUCCESSFUL`

## Attach Compatibility

Command:

```sh
mise exec -- gradle :driver-fabric-attach:test
```

Observed:

- Exit code: `0`
- Gradle result: `BUILD SUCCESSFUL`

## Authority Scan

Command:

```sh
rg -n 'override fun actions\(|get\("actions"\)|"/actions"|/clients/\{id\}/actions|client-actions|actions projection|actions endpoint' daemon/src/main cli/src/main driver-*/src/main testkit/src/main protocol/src/main -S
```

Observed after this phase:

- No remaining production `get("actions")` in `HttpDriverSession`.
- Remaining hits are public daemon/protocol projection routes, smoke artifact
  names, backend compatibility projection, and Fabric loopback projection.

## Final Local Verification

```sh
mise exec -- gradle :daemon:test :driver-fabric-attach:test
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
mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.completion gate does not accept unsupported version lanes as support*'
```

Observed after restoring the required runnable-support wording in the final
completion gate:

- Exit code: `0`
- Gradle result: `BUILD SUCCESSFUL`

Command:

```sh
mise run architecture-check
```

Observed:

- Exit code: `0`
- Gradle protocol, daemon, CLI, and driver-fabric checks passed.
- Bun Playwright helper/distribution tests: `19 pass`, `0 fail`.
