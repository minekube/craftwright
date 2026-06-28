# Navigation Operation Id Source Ownership Evidence

## Scope

Phase 89 removes duplicated navigation/task operation-id literals from Fabric
backend dispatch and Fabric smoke public-agent required-action checks. It does
not remove navigation adapters or complete generic runtime discovery.

## Red Evidence

- `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric backend and smoke do not own navigation operation id literals*'`
  failed before implementation because `FabricDriverBackend.kt` still repeated
  `navigation.plan`, `navigation.follow`, `navigation.stop`, `task.run`, and
  `task.status`, and `FabricClientSmokeController.kt` still repeated
  `navigation.plan` and `navigation.follow`.

## Green Evidence

- `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric backend and smoke do not own navigation operation id literals*'`

The focused guard passed after adding `FabricNavigationOperationIds` in
`FabricNavigationDiscovery.kt` and using those constants from discovery,
backend dispatch, and smoke readiness checks.

## Local Final Gates

- source scan:
  `rg -n '"(navigation\.plan|navigation\.follow|navigation\.stop|task\.run|task\.status)"' driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverBackend.kt driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricClientSmokeController.kt`
- `git diff --check`
- `mise exec -- gradle lint test --rerun-tasks`
- `mise exec -- bun test playwright`

All final local gates passed before commit. The backend/smoke navigation
operation-id literal scan returned no matches, and the forced Gradle gate
executed lint and tests instead of relying on cached task state.

## Remote CI

Not waited on during active development. Local forced CI is the working gate;
remote CI may continue in the background after push.

## Push Evidence

- Implementation commit pushed to `main`:
  `e1be82a driver-fabric: centralize navigation operation ids`

## Notes

- No new public gameplay action, generated route family, CLI gameplay catalog,
  Fabric execution binding, scenario shortcut, compiled lane, public
  version-specific API, or Minecraft support claim should be added in this
  phase.
- The broader binding-exit blocker remains active because future gameplay
  breadth still needs generic runtime discovery instead of hand-maintained
  bootstrap/navigation operation definitions.
