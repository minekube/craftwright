# Full Codex Evidence Gate Refresh

Date: 2026-06-28

## Intent

Refresh the concrete completion-gate evidence after final gameplay defaulted
to Codex evidence instead of human Minecraft chat confirmation.

## Distribution Evidence

CLI package:

```sh
mise run package-cli
```

Result: `BUILD SUCCESSFUL`; `:cli:distZip` and `:cli:distTar` ran, and
`build/docker/craftless/` was refreshed.

Packaged CLI smoke:

```sh
build/docker/craftless/bin/craftless server start --once --port 0 --workspace /tmp/craftless-cli-smoke-1782603968
```

Output:

```json
{"ok":true,"url":"http://127.0.0.1:56162","openapi":"/openapi.json","events":"/events","workspace":"/tmp/craftless-cli-smoke-1782603968"}
```

Docker build:

```sh
docker build -t craftless:local .
```

Result: image `craftless:local` built successfully from the copied
`build/docker/craftless/` CLI distribution.

Docker smoke:

```sh
docker run --rm craftless:local /opt/craftless/bin/craftless server start --once --port 0 --workspace /tmp/craftless
```

Output:

```json
{"ok":true,"url":"http://127.0.0.1:43775","openapi":"/openapi.json","events":"/events","workspace":"/tmp/craftless"}
```

Install script smoke:

```sh
tmp="$(mktemp -d /tmp/craftless-install-smoke.XXXXXX)" && CRAFTLESS_VERSION=v0.1.0 CRAFTLESS_INSTALL_DIR="$tmp/bin" CRAFTLESS_HOME="$tmp/home" ./install.sh && "$tmp/bin/craftless" server start --once --port 0 --workspace "$tmp/workspace"
```

Output:

```text
craftless 0.1.0 installed to /tmp/craftless-install-smoke.BGerjE/bin/craftless
{"ok":true,"url":"http://127.0.0.1:56246","openapi":"/openapi.json","events":"/events","workspace":"/tmp/craftless-install-smoke.BGerjE/workspace"}
```

## Compatibility Evidence

Live Mojang manifest probe:

```sh
mise exec -- bun -e '
const manifest = await (await fetch("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")).json();
const release = manifest.versions.find((version) => version.id === manifest.latest.release);
const snapshot = manifest.versions.find((version) => version.id === manifest.latest.snapshot);
const older = manifest.versions.find((version) => version.id === "1.20.6");
if (!release || !snapshot || !older) throw new Error("missing expected manifest entry");
const releaseMeta = await (await fetch(release.url)).json();
const olderMeta = await (await fetch(older.url)).json();
console.log(JSON.stringify({
  latest: manifest.latest,
  latestRelease: { id: release.id, type: release.type, releaseTime: release.releaseTime, time: release.time, sha1: release.sha1, javaVersion: releaseMeta.javaVersion },
  latestSnapshot: { id: snapshot.id, type: snapshot.type, releaseTime: snapshot.releaseTime, time: snapshot.time, sha1: snapshot.sha1 },
  representativeOlder: { id: older.id, type: older.type, releaseTime: older.releaseTime, time: older.time, sha1: older.sha1, javaVersion: olderMeta.javaVersion },
}, null, 2));
'
```

Output:

```json
{
  "latest": {
    "release": "26.2",
    "snapshot": "26.3-snapshot-1"
  },
  "latestRelease": {
    "id": "26.2",
    "type": "release",
    "releaseTime": "2026-06-16T12:03:33+00:00",
    "time": "2026-06-23T11:49:11+00:00",
    "sha1": "0089713c6ba08fdfed86b5dfde296f3f3f59c9ee",
    "javaVersion": {
      "component": "java-runtime-epsilon",
      "majorVersion": 25
    }
  },
  "latestSnapshot": {
    "id": "26.3-snapshot-1",
    "type": "snapshot",
    "releaseTime": "2026-06-23T11:57:02+00:00",
    "time": "2026-06-23T12:05:18+00:00",
    "sha1": "130ead6281f4a16f085689d1c9a716ae2eb4d01c"
  },
  "representativeOlder": {
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
}
```

Compatibility tests:

```sh
mise exec -- gradle :driver-fabric:test --tests '*FabricCompatibilityMatrixTest*' --tests '*FabricCapabilityProbeTest.runtime metadata probe emits sanitized compatibility lane evidence*' :testkit:test --tests '*LocalMinecraftServerSmokeTest.local server smoke records unsupported runtime lane without provisioning server*'
```

Result: `BUILD SUCCESSFUL`.

Latest unsupported runtime-lane smoke:

```sh
rm -rf /tmp/craftless-fabric-smoke-26-lane-refresh && CRAFTLESS_FABRIC_CLIENT_SMOKE=1 CRAFTLESS_SMOKE_MINECRAFT_VERSION=26.2 CRAFTLESS_LOCAL_SERVER_SMOKE_ROOT=/tmp/craftless-fabric-smoke-26-lane-refresh CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 mise exec -- gradle :driver-fabric:fabricClientSmoke
```

Result:

```text
local Minecraft server smoke unsupported for runtime lane latest-release-26-2: runtime-lane-missing
BUILD SUCCESSFUL
```

Artifact:

```json
{"id":"latest-release-26-2","status":"UNSUPPORTED","minecraftVersion":"26.2","javaMajorVersion":25,"providerId":"no-compatible-client-lane","unsupportedReason":"runtime-lane-missing"}
```

## Final Gameplay Evidence

Command:

```sh
CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS=90000 CRAFTLESS_FABRIC_SMOKE_ACTION_TIMEOUT_MS=120000 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 mise exec -- gradle :driver-fabric:fabricFinalGameplay
```

Result:

```text
local Minecraft server smoke collected 3 evidence event(s)
exitCode=0
BUILD SUCCESSFUL in 2m 8s
```

Required artifacts were present and non-empty:

```text
server-evidence.jsonl
client-openapi-connected.json
client-actions-connected.json
client-resources-connected.json
client-events.jsonl
client-events-stream.sse
gameplay-results.jsonl
public-agent-gameplay-results.jsonl
public-agent-state.jsonl
runtime-metadata.json
```

Public-agent state:

```json
{"event":"public-agent-discovery","clientId":"fabric-smoke","request":"GET /openapi.json"}
{"event":"public-agent-discovery","clientId":"fabric-smoke","request":"GET /clients/fabric-smoke/openapi.json"}
{"event":"public-agent-discovery","clientId":"fabric-smoke","request":"GET /clients/fabric-smoke/actions","availableActions":["entity.attack","entity.query","inventory.equip","inventory.query","navigation.follow","navigation.plan","navigation.stop","player.chat","player.look","player.move","player.query","player.raycast","recipe.craft","recipe.query","screen.close","screen.query","task.run","task.status","world.block.break","world.block.interact","world.block.query","world.time.query"]}
{"event":"public-agent-stream","clientId":"fabric-smoke","request":"GET /clients/fabric-smoke/events:stream","bytes":7322}
```

Gameplay outcome summary:

- No `public-agent-blocked` event was written.
- Public-agent evidence includes generated `recipe.craft`,
  `inventory.equip`, `entity.query`, `navigation.plan`,
  `navigation.follow`, and `entity.attack`.
- The client crafted and equipped a `Wooden Sword`.
- The agent found Cows through generated `entity.query`.
- The agent attacked a Cow through generated `entity.attack`.
- Follow-up `entity.query` observed the Cow with `alive:false`.
- Follow-up `entity.query` observed dropped `Raw Beef`, `Leather`, and an
  `Experience Orb`.
- `server-evidence.jsonl` contains only the Craftless client's join, chat, and
  disconnect. No operator item provisioning or confirmation phrase is present.

Server evidence:

```json
{"type":"PLAYER_JOINED","player":"Player462"}
{"type":"CHAT","player":"Player462","message":"hello from Craftless final gameplay"}
{"type":"PLAYER_DISCONNECTED","player":"Player462"}
```

## Implication

This refresh proves the current code can pass the distribution, compatibility,
and final public gameplay evidence gates without the old Robin chat completion
requirement. It does not add product behavior or claim new Minecraft version
support.
