# Official Fabric Live Client State Probe Evidence

Date: 2026-06-28

## Summary

Phase 157 adds a narrow latest/current official-lane client-state provider.
`OfficialFabricDriverBackend` now composes `fabricClientStateGraphFragment`
from a lane-provided `FabricClientStateGraphSnapshot` instead of directly
using `FabricClientStateGraphSnapshot.disconnected()`.

The production provider reads the running `net.minecraft.client.Minecraft`
singleton through official/Mojang names and schedules state reads on the
Minecraft client thread when needed.

This is live client-state evidence plumbing only. It adds no gameplay actions,
no operation adapters, no packaged `26.2` driver manifest entry, and no
latest/current support claim.

## Verification

Focused red test before implementation:

```sh
mise exec -- gradle :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*' :driver-fabric:test --tests '*FabricDriverModuleTest.official lane has opt in launch attach probe task without packaging support claim'
```

Result: failed because `clientStateProvider` and
`OfficialFabricClientStateProvider` did not exist.

Focused green tests after implementation:

```sh
mise exec -- gradle :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*' :driver-fabric:test --tests '*FabricDriverModuleTest.official lane has opt in launch attach probe task without packaging support claim'
```

Result: `BUILD SUCCESSFUL`.

Enabled official attach probe:

```sh
CRAFTLESS_OFFICIAL_FABRIC_ATTACH_PROBE=1 \
CRAFTLESS_OFFICIAL_ATTACH_PROBE_TIMEOUT_MS=120000 \
mise exec -- gradle :driver-fabric-official:officialFabricAttachProbe
```

Result: `BUILD SUCCESSFUL`, with `official Fabric probe observed client attach
for official-probe`.

Probe artifacts:

```text
status=ATTACHED client=official-probe
installedMods=mods:6d85fb9272c1d2f5 runtimeFingerprint=graph:62818b62751e2d22 actions=0 resources=10 handles=10 events=3
```

Client-state availability from the title-screen attach probe:

```text
client=unavailable:client-not-connected
entity=unavailable:client-not-connected
inventory=unavailable:client-not-connected
player=unavailable:client-not-connected
recipe=unavailable:client-not-connected
screen=available:
world=unavailable:client-not-connected
```

The client-state resources remain unavailable in this probe because the
official client attaches at the title screen before connecting to a server.
The evidence advancement is that availability is now produced by the official
live state provider, not a hard-coded disconnected graph fragment.
