# Strict Fabric API Artifact Resolution Evidence

## Scope

Phase 132 makes Fabric cache preparation require a matching Fabric API Maven
artifact for the resolved Minecraft version. Missing Fabric API metadata is now
a visible compatibility blocker instead of a silent degraded Fabric launch plan.
This phase does not add a compiled lane, change Fabric versions, claim
latest/older support, or add gameplay APIs/actions/routes/scenario shortcuts.

## Red Evidence

- `mise exec -- gradle :daemon:test --tests '*CachePreparationServiceTest.fabric cache preparation requires matching fabric api artifact*'`
  - Failed before implementation because the test expected an exception but
    `prepare` succeeded when Fabric API Maven metadata had no `+1.21.7`
    artifact.

## Green Evidence

- `mise exec -- gradle :daemon:test --tests '*CachePreparationServiceTest.fabric cache preparation requires matching fabric api artifact*' --tests '*CachePreparationServiceTest.fabric cache preparation resolves fabric api mod artifact from maven metadata*'`
  - Passed after Fabric API resolution became strict and the successful metadata
    path stayed intact.
- `mise exec -- gradle :daemon:test`
  - Passed after daemon and local server test fixtures modeled the required
    Fabric API metadata/binary.
- `mise exec -- gradle :cli:test`
  - Passed after CLI cache/server test fixtures modeled the required Fabric API
    metadata/binary.

## Local CI Evidence

- `git diff --check`
  - Passed.
- `mise run ci`
  - Passed. Ran lint, unused/dead-code checks through detekt, Gradle tests, and
    Bun Playwright helper/distribution tests.
