# Fabric Discovery Module Instructions

`driver-fabric-discovery/` owns shared Fabric Loader/runtime discovery code
that is reusable across current, older, latest/current, and future Fabric
driver lanes.

## Scope

- Fabric Loader identity and installed-mod discovery.
- Deterministic runtime metadata fingerprints.
- Shared Fabric runtime metadata snapshot/provider helpers.
- Shared Fabric runtime metadata resource projection for the runtime graph.

## Rules

- Do not add gameplay actions, action descriptors, scenario shortcuts, CLI
  behavior, public route families, or version-specific public APIs here.
- Keep this module free of Yarn, intermediary, official-mapping, and Minecraft
  game-class calls. Lane modules may pass lane-specific mappings fingerprints,
  registry probes, server-feature probes, and execution adapters into shared
  metadata helpers.
- Prefer generic Fabric Loader data and Craftless-owned metadata over
  per-version constants. Per-version divergence belongs in the lane adapter
  that calls this module.
- Shared runtime graph projection may expose metadata evidence only. Lane
  modules must provide any lane-specific source evidence and keep Minecraft
  game-class registry/server/action discovery outside this module.
- Do not depend on `driver-fabric`, `driver-fabric-official`, `daemon`, or
  `cli`.

## Verification

```sh
mise exec -- gradle :driver-fabric-discovery:test
```
