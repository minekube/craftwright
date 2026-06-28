# Packaged Older Fabric Lane Evidence

## Scope

Phase 138 packages the representative older Fabric lane as a real selectable
driver-mod artifact in the CLI distribution. This proves artifact packaging
and manifest selection plumbing only; older runtime support still requires
launch, attach, generated OpenAPI, and smoke evidence.

## Red Evidence

- `mise exec -- bun test playwright/src/distribution.test.ts --test-name-pattern "CLI distribution packages representative older fabric lane"`
  - Failed before implementation because `driver-fabric/build.gradle.kts` did
    not contain `craftless.fabric.distributionPath`.

## Green Evidence

- `mise exec -- bun test playwright/src/distribution.test.ts --test-name-pattern "CLI distribution packages representative older fabric lane"`
  - Passed after the build and mise packaging path supported staged extra
    Fabric lane catalogs.
- `mise run package-cli`
  - Passed after building the older `1.20.6` lane, staging its jar and private
    lane catalog, building the current distribution with
    `-Pcraftless.extraFabricDriverLaneRoot=build/driver-lanes/older`, and
    checking tar, zip, and Docker context artifacts.

## Packaged Manifest Evidence

Generated `driver-mods.json` contained two public entries:

```json
{
  "loader": "FABRIC",
  "minecraftVersion": "1.21.6",
  "loaderVersion": "0.19.3",
  "fabricApiVersion": "0.128.2+1.21.6",
  "javaMajorVersion": 21,
  "mappingsFingerprint": "craftless-fabric-bindings",
  "path": "mods/craftless-driver-fabric.jar"
}
```

```json
{
  "loader": "FABRIC",
  "minecraftVersion": "1.20.6",
  "loaderVersion": "0.19.3",
  "fabricApiVersion": "0.100.8+1.20.6",
  "javaMajorVersion": 21,
  "mappingsFingerprint": "craftless-fabric-bindings-1-20-6",
  "path": "mods/fabric-1.20.6/craftless-driver-fabric.jar"
}
```

The public manifest does not include private `artifactKey` or
`distributionPath` fields.

## Local CI Evidence

- `git diff --check`
  - Passed before final commit.
- `mise run ci`
  - Passed before final commit: Gradle lint, detekt unused-check, Gradle tests,
    and Bun Playwright fixture/distribution tests.
