# Reflective Recipe Bridge Evidence

## Scope

Phase 137 removes direct compile-time dependencies on current-only recipe
display/click APIs from the Fabric backend and projection. Recipe discovery and
crafting still use the public Craftless recipe action shape, but the internal
bridge resolves runtime objects reflectively. This phase does not add public
gameplay API breadth, static catalogs, or older runtime support claims.

## Red Evidence

- `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric recipe bridge does not compile against version-specific recipe display types*'`
  - Failed before implementation because backend/projection sources and mixin
    config named current-only recipe display/click API types.

## Green Evidence

- `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric recipe bridge does not compile against version-specific recipe display types*'`
  - Passed after backend/projection recipe code moved behind reflection and the
    typed recipe-book accessor mixin was removed.
- `mise run fabric-lane-check-older`
  - Passed and wrote `build/reports/fabric-lane-check-older.status` with
    `status=compiled`.

## Local CI Evidence

- `mise run package-cli`
  - Passed before final commit.
- `git diff --check`
  - Passed before final commit.
- `mise run ci`
  - Passed before final commit: Gradle lint, detekt unused-check, Gradle tests,
    and Bun Playwright fixture/distribution tests.
