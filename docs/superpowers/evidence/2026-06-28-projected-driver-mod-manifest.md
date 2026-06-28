# Projected Driver Mod Manifest Evidence

## Scope

Phase 130 makes packaged `driver-mods.json` a public projection of the private
Fabric driver lane catalog. It removes private build/distribution fields from
the manifest consumed by the daemon and packaged distributions. This does not
add a compiled lane, change Fabric versions, claim latest/older support, or add
gameplay APIs/actions/routes/scenario shortcuts.

## Red Evidence

- `mise exec -- bun test playwright/src/distribution.test.ts`
  - Failed before implementation because `cli/build.gradle.kts` did not contain
    `renderDriverModManifest` and still wrote
    `catalog.readText().trimEnd()` directly into `driver-mods.json`.

## Green Evidence

- `mise exec -- bun test playwright/src/distribution.test.ts`
  - Passed after the distribution guard required projected manifest generation
    and private-field package checks.
- `mise exec -- gradle :cli:writeDriverModManifest`
  - Passed. The task generated a projected manifest from the private Fabric
    lane catalog.

## Artifact Evidence

Generated `cli/build/generated/driver-mods/driver-mods.json` contained:

- `loader`: `FABRIC`
- `minecraftVersion`: `1.21.6`
- `loaderVersion`: `0.19.3`
- `path`: `mods/craftless-driver-fabric.jar`

The generated manifest did not contain:

- `artifactKey`
- `distributionPath`

## Local CI Evidence

- `mise run package-cli`
  - Passed. Built CLI `distZip` and `distTar`, verified packaged driver mod and
    manifest presence, rejected `artifactKey` and `distributionPath` in tar/zip
    packaged manifests, refreshed `build/docker/craftless`, and verified the
    Docker staging driver jar contents.
- `git diff --check`
  - Passed.
- `mise run ci`
  - Passed. Ran lint, unused/dead-code checks through detekt, Gradle tests, and
    Bun Playwright helper/distribution tests.
