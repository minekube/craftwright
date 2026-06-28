# Bootstrap Adapter Key Separation Evidence

Date: 2026-06-28

## Scope

Phase 176 closes CL-02d only. It separates private Fabric adapter-key
ownership from bootstrap operation public descriptor shape.

This phase does not add gameplay operations, CLI commands, route families,
scenario shortcuts, or a new Minecraft version support claim. CL-02 remains
open for real discovery-sourced operation nodes and broader architecture
guards.

## Red

Command:

```sh
mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.bootstrap operation definitions do not own private adapter key pairs*'
```

Observed before implementation:

- Exit code: `1`
- Failure: production `FabricBootstrapOperationDefinitions.kt` still
  contained `val adapter: String` and per-definition
  `adapter = FabricBootstrapOperationAdapters.*` pairs.

## Focused Green

Command:

```sh
mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.bootstrap operation definitions do not own private adapter key pairs*' --tests '*FabricDriverModuleTest.transitional fabric binding operation ids are represented as runtime graph operations*' --tests '*FabricDriverModuleTest.fabric backend does not derive binding adapter keys from operation ids*' --tests '*FabricDriverModuleTest.fabric backend exposes runtime capability graph from probes*'
```

Observed after implementation:

- Exit code: `0`
- Gradle result: `BUILD SUCCESSFUL`

## Source Scan

Command:

```sh
rg -n 'val adapter: String|adapter = FabricBootstrapOperationAdapters' driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricBootstrapOperationDefinitions.kt -S
```

Observed:

- Exit code: `1`
- No matches.

## Module Verification

Command:

```sh
mise exec -- gradle :driver-fabric:test
```

Observed:

- Exit code: `0`
- Gradle result: `BUILD SUCCESSFUL`

## Final Local Verification

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
