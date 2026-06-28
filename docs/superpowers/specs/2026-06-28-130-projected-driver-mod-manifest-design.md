# Projected Driver Mod Manifest Design

## Problem

Phase 129 made `cli` stage driver artifacts from the private generated Fabric
driver lane catalog, but `writeDriverModManifest` still writes
`driver-mods.json` as a raw copy of that catalog. That leaks build-only fields
such as `artifactKey` and `distributionPath` into the manifest consumed by the
daemon, packaged CLI, Docker image, setup action, and users.

The private catalog should remain the source of build/distribution metadata.
The packaged manifest should be a public projection containing only the runtime
selection fields the daemon needs.

## Goals

- Keep the private Fabric driver lane catalog as the single source for packaged
  driver-mod manifest generation.
- Generate `driver-mods.json` by parsing catalog entries and projecting only:
  `loader`, `minecraftVersion`, `loaderVersion`, and `path`.
- Use catalog `distributionPath` as the manifest `path`, because the manifest
  path is relative to the packaged distribution.
- Prevent private catalog fields from leaking into generated/package manifests.
- Preserve the existing current lane manifest behavior for `1.21.6`.

## Non-Goals

- Do not add another compiled Fabric lane.
- Do not change Fabric Loom, Fabric Loader, Fabric API, or Minecraft versions.
- Do not claim latest/current or older-version support.
- Do not add public gameplay APIs, generated route families, CLI gameplay
  catalogs, Fabric descriptor/binding pairs, or scenario shortcuts.
- Do not remove daemon manifest compatibility with already-published manifests.

## Acceptance Criteria

- A red distribution guard fails before implementation because the build still
  copies `fabric-driver-lanes.json` directly into `driver-mods.json`.
- `writeDriverModManifest` parses the generated catalog and writes a projected
  JSON manifest.
- Generated `driver-mods.json` contains the current lane with
  `loader=FABRIC`, `minecraftVersion=1.21.6`, `loaderVersion=0.19.3`, and
  `path=mods/craftless-driver-fabric.jar`.
- Generated `driver-mods.json` does not contain `artifactKey` or
  `distributionPath`.
- Packaged CLI distributions still include `driver-mods.json` and
  `mods/craftless-driver-fabric.jar`.
- Focused tests/tasks, `mise run package-cli`, `git diff --check`, and
  `mise run ci` pass locally.
