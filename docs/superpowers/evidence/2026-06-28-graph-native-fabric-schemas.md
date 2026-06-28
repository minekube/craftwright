# Graph-Native Fabric Schemas Evidence

## Scope

Phase 78 removes the runtime graph schema dependency on transitional
`FabricActionBinding` descriptors. It does not add gameplay breadth, public
route families, static CLI catalogs, Fabric descriptor/binding pairs, scenario
shortcuts, compiled lanes, version-specific public APIs, or Minecraft support
claims.

## Red

- Command:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricCapabilityProbeTest.fabric capability probe context does not receive action bindings for graph schemas*'`
- Result: failed as expected because `FabricCapabilityProbeContext` still had a
  `bindings` field.
- Evidence: assertion failure at `FabricCapabilityProbeTest.kt:102`.

## Green

- Command:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricCapabilityProbeTest.fabric capability probe context does not receive action bindings for graph schemas*'`
- Result: `BUILD SUCCESSFUL`.
- Command:
  `mise exec -- gradle :driver-fabric:test --tests '*FabricCapabilityProbeTest.fabric graph schemas stay available without binding descriptor fallback*'`
- Result: `BUILD SUCCESSFUL`.
- Command:
  `mise exec -- gradle :driver-fabric:test`
- Result: `BUILD SUCCESSFUL`.

## Final Local Gates

- Command: `git diff --check`
- Result: passed with no output.
- Command: `mise run architecture-check`
- Result: `BUILD SUCCESSFUL` across protocol, daemon, CLI, driver-fabric, and
  Bun Playwright architecture tests.
- Command: `mise run ci`
- Result: `BUILD SUCCESSFUL`; Gradle lint, Gradle tests, and Bun Playwright
  tests passed.

## Remote Gates

- Commit: `7683209 driver-fabric: own graph operation schemas`
- Push: `main` updated from `66a0e65` to `7683209`.
- GitHub Actions: `ci` run `28309427777`.
- Result: passed; `verify` completed in 6m05s and `Run mise run ci` passed.
