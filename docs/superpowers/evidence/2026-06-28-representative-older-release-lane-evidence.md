# Representative Older Release Lane Evidence

Date: 2026-06-28

## Intent

Record one real older Minecraft release as compatibility evidence without
adding static gameplay breadth or claiming older-version Fabric client support.

## Mojang Metadata

Command:

```sh
mise exec -- bun -e '
const manifest = await (await fetch("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")).json();
const v = manifest.versions.find((version) => version.id === "1.20.6");
if (!v) throw new Error("missing 1.20.6");
const meta = await (await fetch(v.url)).json();
console.log(JSON.stringify({ id: v.id, type: v.type, releaseTime: v.releaseTime, time: v.time, sha1: v.sha1, javaVersion: meta.javaVersion }, null, 2));
'
```

Output:

```json
{
  "id": "1.20.6",
  "type": "release",
  "releaseTime": "2024-04-29T12:40:45+00:00",
  "time": "2026-06-23T06:29:22+00:00",
  "sha1": "421c84796cf2d6560ce887fee77f2ce3d85ba542",
  "javaVersion": {
    "component": "java-runtime-delta",
    "majorVersion": 21
  }
}
```

## Matrix Evidence

The compatibility matrix includes:

- supported compiled lane: current `1.21.6`;
- unsupported latest-release lane: `26.2`;
- unsupported representative older-release lane: `1.20.6`.

The `1.20.6` lane is:

- id: `older-release-1-20-6`;
- status: `UNSUPPORTED`;
- Java major version: `21`;
- provider: `no-compatible-client-lane`;
- reason: `runtime-lane-missing`.

## Verification

Command:

```sh
mise exec -- gradle :driver-fabric:test --tests '*FabricCompatibilityMatrixTest*'
```

Result:

```text
BUILD SUCCESSFUL
```

## Implication

Craftless now distinguishes a known older Minecraft release from unknown
versions in compatibility evidence. This does not add `1.20.6` client support,
a compiled Loom lane, public version-specific APIs, static gameplay actions,
or CLI gameplay catalogs.
