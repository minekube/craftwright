# Official Fabric Lane Module Instructions

`driver-fabric-official/` is an internal latest/current Fabric lane build
boundary for Minecraft 26.x official/unobfuscated mappings. Its job is to make
the current/latest lane real through shared Craftless runtime infrastructure,
not through a second hand-written driver.

## Scope

- Non-remap Fabric Loom build boundary.
- Java 25 latest/current compile probes.
- Minimal Fabric client entrypoint and metadata needed to prove the build lane.
- Shared Fabric attach/runtime handoff once extracted from the Yarn/remap lane.

## Rules

- Do not add public gameplay actions, static route families, CLI gameplay
  catalogs, or scenario shortcuts here.
- Do not clone the Yarn/remap `driver-fabric` gameplay bindings into this
  module. Extract shared attach, transport, discovery/projection/invocation
  contracts, and generic runtime graph plumbing into common modules; add an
  official-lane adapter only where Minecraft, Fabric API, mappings, loader, or
  bytecode signatures actually diverge.
- Do not make this module depend on the Yarn/remap `driver-fabric` module.
  Shared code belongs in a neutral module consumed by both lanes.
- Keep Ktor loopback, attach environment parsing, session replacement,
  JSON-RPC-style invocation transport, SSE/lifecycle event plumbing, action
  projection, and OpenAPI generation shared by default.
- If latest/current support requires per-version code, isolate only the
  diverging adapter/accessor/provider behind the lane boundary and document the
  exact incompatibility that forced it.
- Do not package this module as a supported driver lane or add it to
  `driver-mods.json` until launch, self-attach, generated OpenAPI/actions,
  resources, SSE, and public API/CLI gameplay evidence pass.
- Keep public names Craftless-owned. Fabric/Minecraft names are implementation
  inputs only.

## Verification

```sh
mise run fabric-lane-check-latest-official
```
