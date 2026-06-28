# Packaged Older Fabric Lane Design

## Problem

The representative older Fabric lane now compiles, but the normal CLI
distribution still packages only the default current driver mod. That leaves
`1.20.6` as source-compatibility evidence, not a selectable runtime artifact.

## Decision

Package the representative older Fabric lane as an additional driver-mod
artifact in the CLI tar/zip distribution. Build the older lane with the same
parameterized `driver-fabric` module, stage its remapped jar under a unique
distribution path, copy its generated private lane catalog, then make the CLI
packaging task merge the current lane catalog with staged extra lane catalogs.

The public `driver-mods.json` remains a clean projection and must not expose
private `artifactKey` or `distributionPath` fields.

## Non-Goals

- Do not claim older runtime support is complete.
- Do not add version-specific public APIs, route families, or gameplay
  catalogs.
- Do not add another source module or hard-code gameplay actions.
- Do not require Docker to build Craftless.

## Verification

- A Bun distribution test must fail before implementation because the build
  does not know how to stage/merge an older driver lane.
- `mise run package-cli` must build both the current and older lane artifacts.
- The packaged tar and zip must contain:
  - root `driver-mods.json`;
  - current `mods/craftless-driver-fabric.jar`;
  - older `mods/fabric-1.20.6/craftless-driver-fabric.jar`;
  - a public manifest entry for Minecraft `1.20.6`.
- `driver-mods.json` must not expose `artifactKey` or `distributionPath`.
