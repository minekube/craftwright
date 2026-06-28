# Phase 166 Runtime Graph Default Action Projection Evidence

Phase 166 makes the JVM driver contract derive action descriptors from the
runtime capability graph by default. This removes duplicate action-list
projection paths without adding gameplay operations, routes, adapters, static
catalogs, scenario shortcuts, version lanes, or support claims.

## Red Check

Command:

```sh
mise exec -- gradle :driver-api:test --tests '*DriverSessionContractTest*'
```

Observed before implementation:

```text
Class 'GraphOnlyDriverSession' is not abstract and does not implement abstract member:
fun actions(): List<DriverActionDescriptor>
```

This proved a graph-only `DriverSession` still needed a hand-written
`actions()` override before the default projection existed.

## Implementation Evidence

- `DriverSession.actions()` now defaults to sorted
  `runtimeGraph().operations.map { it.toDriverActionDescriptor() }`.
- `driver-api` owns shared projection helpers for:
  - `RuntimeOperationNode.toDriverActionDescriptor()`;
  - `RuntimeSchema.toDriverActionArgument()`;
  - `RuntimeSchema.toDriverActionResultDescriptor()`;
  - `RuntimeSchema.toDriverActionResultProperty()`;
  - `RuntimeAvailability.toDriverActionAvailability()`.
- `FakeDriverSession` no longer owns a local graph-to-action mapper.
- `FabricDriverBackend.actions(clientId)` keeps the backend override but uses
  the shared `toDriverActionDescriptor()` helper.
- Prepared graph-empty sessions in daemon and official probe code no longer
  override `actions()` just to return an empty list.
- The Fabric module guardrail test reads the durable version-breadth contract
  from `docs/agent-operating-contract.md`, preserving the short root
  `AGENTS.md` policy.

## Verification

Command:

```sh
mise exec -- gradle :driver-api:cleanTest :driver-api:test --tests '*DriverSessionContractTest*'
```

Result:

```text
BUILD SUCCESSFUL
7 actionable tasks: 2 executed, 5 up-to-date
```

Command:

```sh
mise exec -- gradle :driver-fabric:clean :driver-fabric:compileJava
```

Result:

```text
BUILD SUCCESSFUL
11 actionable tasks: 4 executed, 7 up-to-date
```

Command:

```sh
mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.official lane has opt in launch attach probe task without packaging support claim'
```

Result:

```text
BUILD SUCCESSFUL
21 actionable tasks: 2 executed, 19 up-to-date
```

Command:

```sh
mise exec -- gradle :driver-api:test :testkit:test :driver-fabric:test :daemon:test :driver-fabric-official:test
```

Result:

```text
BUILD SUCCESSFUL
35 actionable tasks: 1 executed, 34 up-to-date
```

Command:

```sh
mise run fabric-lane-check-latest-official
```

Result:

```text
BUILD SUCCESSFUL
15 actionable tasks: 15 up-to-date
```

Command:

```sh
mise run ci
```

Result:

```text
BUILD SUCCESSFUL
lint, unused-check, Gradle test, and Bun Playwright tests passed.
19 Bun tests passed, 0 failed.
```

## Boundary

Phase 166 is a projection cleanup only. It does not add official 26.x gameplay
support, a packaged latest driver manifest entry, public gameplay breadth, a
new route family, a static action catalog, or final completion evidence.
