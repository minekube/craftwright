# Official Fabric Lane Module Instructions

`driver-fabric-official/` is an internal latest/current Fabric lane build
boundary for Minecraft 26.x official/unobfuscated mappings. Its job is to make
the current/latest lane real through shared Craftless runtime infrastructure,
not through a second hand-written driver.
Treat this module as a compatibility lane and probe boundary. The durable
implementation belongs in shared Fabric attach/runtime/discovery/projection
modules by default; official-lane code should exist only for proven
official-mapping, Fabric API, loader, Minecraft, or bytecode-signature
divergence.
When a latest/current detail is needed, first ask whether it is generic Fabric
runtime discovery, metadata, artifact resolution, attach transport, invocation,
or OpenAPI projection that belongs in a shared module. Keep only the smallest
official-mapping adapter/accessor/provider here, and record the exact
divergence that forced it.

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
- Do not introduce official-lane `DriverSession` forks, public protocol DTOs,
  public routes, CLI commands, action IDs, or duplicated gameplay
  implementations. If a 26.x/latest behavior differs, expose the difference as
  runtime metadata, graph evidence, availability, or a narrow internal adapter.
- Keep Ktor loopback, attach environment parsing, session replacement,
  JSON-RPC-style invocation transport, SSE/lifecycle event plumbing, action
  projection, and OpenAPI generation shared by default.
- Use `driver-fabric-discovery` for shared Fabric Loader identity,
  installed-mod fingerprints, runtime metadata snapshots, deterministic
  fingerprint helpers, runtime metadata projection, and protocol-level graph
  composition. Use it for shared non-gameplay registry resource/handle
  projection from official-lane registry fingerprints and event resource/event
  projection from official-lane event-source evidence as well. Use it for
  shared non-gameplay client-state resource/handle projection from
  official-lane state snapshots, including disconnected snapshots while this
  lane is metadata-only. Do not reintroduce official-only copies of that
  metadata, registry graph, event graph, client-state graph, or graph plumbing.
- If latest/current support requires per-version code, isolate only the
  diverging adapter/accessor/provider behind the lane boundary and document the
  exact incompatibility that forced it.
- Runtime metadata must come from the running Fabric Loader/client where
  possible. Do not replace unknown data with official-lane placeholder
  fingerprints, static installed-mod lists, or hard-coded version claims.
- Client-state evidence should come from the running official/Mojang-mapped
  Minecraft client when available. Keep it to lane-provided booleans projected
  through shared discovery; do not turn this module into a copied Yarn/remap
  gateway or gameplay binding catalog.
- Do not package this module as a supported driver lane or add it to
  `driver-mods.json` until launch, self-attach, generated OpenAPI/actions,
  resources, SSE, and public API/CLI gameplay evidence pass.
- Keep public names Craftless-owned. Fabric/Minecraft names are implementation
  inputs only.

## Verification

```sh
mise run fabric-lane-check-latest-official
```
