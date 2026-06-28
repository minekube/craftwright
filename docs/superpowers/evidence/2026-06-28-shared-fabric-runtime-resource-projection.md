# Shared Fabric Runtime Resource Projection Evidence

Date: 2026-06-28

## Scope

Phase 152 moves Fabric runtime metadata resource projection into
`driver-fabric-discovery`. Both the Yarn/remap lane and the latest/current
official lane now use shared projection for the `runtime` graph resource while
supplying lane-specific evidence from their own modules.

This is runtime graph metadata plumbing only. It does not add gameplay actions,
package the official `26.2` lane, add static CLI gameplay commands, create
version-specific public APIs, or claim latest/current gameplay support.

## Red Evidence

Shared projection test and official architecture guard before implementation:

```sh
mise exec -- gradle :driver-fabric-discovery:test :driver-fabric:test --tests '*FabricDriverModuleTest.official lane has opt in launch attach probe task without packaging support claim'
```

Observed:

```text
Unresolved reference 'fabricRuntimeResourceNode'
```

## Green Evidence

Focused shared/lane tests:

```sh
mise exec -- gradle :driver-fabric-discovery:test :driver-fabric:test --tests '*FabricDriverModuleTest.official lane has opt in launch attach probe task without packaging support claim' :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*'
```

Observed:

```text
BUILD SUCCESSFUL
```

Lint and whitespace:

```sh
mise exec -- gradle :driver-fabric-discovery:test :driver-fabric:test --tests '*FabricDriverModuleTest.official lane has opt in launch attach probe task without packaging support claim' :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*' lint
git diff --check
```

Observed:

```text
BUILD SUCCESSFUL
```

Real enabled official attach probe:

```sh
rm -rf driver-fabric-official/build/craftless-official-attach-probe
CRAFTLESS_OFFICIAL_FABRIC_ATTACH_PROBE=1 \
CRAFTLESS_OFFICIAL_ATTACH_PROBE_TIMEOUT_MS=120000 \
mise exec -- gradle :driver-fabric-official:officialFabricAttachProbe
```

Observed:

```text
BUILD SUCCESSFUL
status=ATTACHED client=official-probe
installedMods=mods:6d85fb9272c1d2f5 runtimeFingerprint=graph:755b3b5233a65773 actions=0 resources=1
```

## Guardrails

- The shared helper projects only metadata evidence.
- `driver-fabric-discovery` remains independent from lane modules, daemon, and
  CLI.
- Minecraft game-class registry, server-feature, and gameplay operation
  discovery stay in lane modules until they can be generalized safely.
