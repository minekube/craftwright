# Shared Fabric Runtime Graph Composition Evidence

## Scope

Phase 153 moved generic Fabric runtime graph fragment composition into
`driver-fabric-discovery` so Fabric lanes share protocol-level graph assembly.
Lane modules still own Minecraft game-class probes, source evidence, accessors,
and execution adapters when those truly diverge.

Non-goals verified for this phase:

- no public gameplay action added;
- no static gameplay catalog added;
- no packaged 26.x driver manifest entry added;
- no version-specific public route family added;
- no latest/current gameplay support claim added.

## Red Evidence

Before implementation, this command failed because
`fabricRuntimeGraph` and `FabricRuntimeGraphFragment` did not exist in shared
discovery infrastructure:

```sh
mise exec -- gradle :driver-fabric-discovery:test :driver-fabric:test --tests '*FabricDriverModuleTest.official lane has opt in launch attach probe task without packaging support claim'
```

Observed failure:

```text
Unresolved reference 'fabricRuntimeGraph'
Unresolved reference 'FabricRuntimeGraphFragment'
```

## Green Evidence

Focused tests:

```sh
mise exec -- gradle :driver-fabric-discovery:test :driver-fabric:test --tests '*FabricDriverModuleTest.official lane has opt in launch attach probe task without packaging support claim' :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*'
```

Result: `BUILD SUCCESSFUL`.

Lint and whitespace:

```sh
mise exec -- gradle lint
git diff --check
```

Result: `BUILD SUCCESSFUL`; `git diff --check` produced no output.

Real official attach probe:

```sh
rm -rf driver-fabric-official/build/craftless-official-attach-probe
CRAFTLESS_OFFICIAL_FABRIC_ATTACH_PROBE=1 \
CRAFTLESS_OFFICIAL_ATTACH_PROBE_TIMEOUT_MS=120000 \
mise exec -- gradle :driver-fabric-official:officialFabricAttachProbe
```

Result: `BUILD SUCCESSFUL`; probe log reported:

```text
official Fabric probe observed client attach for official-probe
```

Machine-readable probe summary:

```text
status=ATTACHED client=official-probe
installedMods=mods:6d85fb9272c1d2f5 runtimeFingerprint=graph:755b3b5233a65773 actions=0 resources=1
```

OpenAPI metadata summary:

```json
{
  "client": "official-probe",
  "minecraft": "26.2",
  "loader": "FABRIC",
  "loaderVersion": "0.19.3",
  "driver": "craftless-driver-fabric-official"
}
```

The official lane remains metadata-only after this phase: `actions=0` and
`resources=1` are expected here and do not satisfy latest/current gameplay
support.
