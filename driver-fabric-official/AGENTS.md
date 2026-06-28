# Official Fabric Lane Module Instructions

`driver-fabric-official/` is an internal latest/current Fabric lane build
boundary for Minecraft 26.x official/unobfuscated mappings.

## Scope

- Non-remap Fabric Loom build boundary.
- Java 25 latest/current compile probes.
- Minimal Fabric client entrypoint and metadata needed to prove the build lane.

## Rules

- Do not add public gameplay actions, static route families, CLI gameplay
  catalogs, or scenario shortcuts here.
- Do not clone the Yarn/remap `driver-fabric` gameplay bindings into this
  module. Shared discovery/projection/invocation code must be extracted only
  after the official lane boundary compiles.
- Do not package this module as a supported driver lane or add it to
  `driver-mods.json` until launch, self-attach, generated OpenAPI/actions,
  resources, SSE, and public API/CLI gameplay evidence pass.
- Keep public names Craftless-owned. Fabric/Minecraft names are implementation
  inputs only.

## Verification

```sh
mise run fabric-lane-check-latest-official
```
