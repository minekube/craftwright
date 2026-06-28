# Reflective Fabric World-Change Callback Evidence

## Scope

Phase 135 removes the compile-time dependency on Fabric API
`ClientWorldEvents` by registering that optional world-change callback
reflectively when the runtime Fabric API exposes it. This phase does not add
public gameplay APIs, route families, CLI gameplay catalogs, Fabric descriptor
or binding pairs, scenario shortcuts, or support claims.

## Red Evidence

- `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric event callbacks do not compile against optional world change event*'`
  - Failed before implementation because `FabricEventCallbacks.kt` contained a
    direct `ClientWorldEvents` import/reference.

## Green Evidence

- `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric event callbacks do not compile against optional world change event*'`
  - Passed after the direct type dependency was replaced with reflective
    optional registration.
- `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric api callback event sources use generic craftless evidence*'`
  - Passed, preserving current generic callback source evidence.
- `mise run fabric-lane-check-older`
  - Passed as a compatibility probe and wrote
    `build/reports/fabric-lane-check-older.status` with
    `status=source-compatibility-blocked`.
  - The blocker list is now `PlayerInput,RecipeDisplayEntry`; the previous
    `ClientWorldEvents` blocker is gone.

## Local CI Evidence

- `mise run package-cli`
  - Passed before final commit.
- `git diff --check`
  - Passed before final commit.
- `mise run ci`
  - Passed before final commit: Gradle lint, detekt unused-check, Gradle tests,
    and Bun Playwright fixture/distribution tests.
