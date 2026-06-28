# Smoke Bootstrap Action Id Source Ownership Evidence

## Scope

Phase 90 removes duplicated bootstrap action-id literals from the Fabric smoke
controller. It does not remove the transitional bootstrap definitions or
complete generic runtime discovery.

## Red Evidence

- `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric smoke controller does not own bootstrap action id literals*'`
  failed before implementation because `FabricClientSmokeController.kt` still
  repeated bootstrap action ids such as `player.chat`, `entity.query`, and
  `world.block.break`.

## Green Evidence

- `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric smoke controller does not own bootstrap action id literals*'`

The focused guard passed after replacing smoke action calls and public-agent
required primitives with `FabricBootstrapOperationIds` constants.

## Local Final Gates

- source scan:
  `rg -n '"(player\.chat|player\.move|screen\.query|world\.time\.query|player\.query|entity\.query|inventory\.query|inventory\.equip|player\.look|player\.raycast|world\.block\.break|world\.block\.interact)"' driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricClientSmokeController.kt`
- `git diff --check`
- `mise exec -- gradle lint test --rerun-tasks`
- `mise exec -- bun test playwright`

All final local gates passed before commit. The smoke controller bootstrap
action-id literal scan returned no matches, and the forced Gradle gate executed
lint and tests instead of relying on cached task state.

## Remote CI

Not waited on during active development. Local forced CI is the working gate;
remote CI may continue in the background after push.

## Notes

- No new public gameplay action, generated route family, CLI gameplay catalog,
  Fabric execution binding, scenario shortcut, compiled lane, public
  version-specific API, or Minecraft support claim should be added in this
  phase.
- The broader binding-exit blocker remains active because future gameplay
  breadth still needs generic runtime discovery instead of hand-maintained
  bootstrap/navigation operation definitions.
