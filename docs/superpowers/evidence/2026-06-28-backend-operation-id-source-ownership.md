# Backend Operation Id Source Ownership Evidence

## Scope

Phase 87 removes duplicated bootstrap operation-id literals from
`FabricDriverBackend` adapter guard checks. It does not remove private adapters
or complete generic runtime discovery.

## Red Evidence

- `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric backend does not own bootstrap operation id guard literals*'`
  failed before implementation because `FabricDriverBackend.kt` still repeated
  bootstrap operation-id literals for entity, block-query, and recipe adapter
  guards.

## Green Evidence

- `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric backend does not own bootstrap operation id guard literals*' --tests '*FabricDriverModuleTest.fabric backend exposes bootstrap bindings as graph operation adapters*'`

The focused guard and existing graph-owned adapter dispatch regression passed
after replacing backend guard literals with `FabricBootstrapOperationIds`
constants.

## Local Final Gates

- source scan:
  `rg -n '"(entity\.query|entity\.attack|world\.block\.query|recipe\.query|recipe\.craft)"' driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt`
- `git diff --check`
- `mise exec -- gradle lint test --rerun-tasks`
- `mise exec -- bun test playwright`

All final local gates passed before commit. The backend operation-id literal
scan returned no matches, and the forced Gradle gate executed lint and tests
instead of relying on cached task state.

## Remote CI

Not waited on during active development. Local forced CI is the working gate;
remote CI may continue in the background after push.

## Push Evidence

- Implementation commit pushed to `main`:
  `04aec1e driver-fabric: centralize backend operation ids`

## Notes

- No new public gameplay action, generated route family, CLI gameplay catalog,
  Fabric execution binding, scenario shortcut, compiled lane, public
  version-specific API, or Minecraft support claim should be added in this
  phase.
- The broader binding-exit blocker remains active because future gameplay
  breadth still needs generic runtime discovery instead of hand-maintained
  bootstrap operation definitions.
