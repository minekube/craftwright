# Post-Cache-Integrity Evidence Refresh

Date: 2026-06-28

## Intent

Refresh the concrete completion-gate evidence after Phase 73 added asset-object
checksum reuse validation and Phase 74 added checksum validation for
metadata-backed binary downloads.

This evidence file records current distribution, compatibility, and final
public gameplay gates. It adds no product behavior and makes no new Minecraft
client support claim.

## Distribution Evidence

CLI package:

```sh
mise run package-cli
```

Result: `BUILD SUCCESSFUL`; `:cli:distZip` and `:cli:distTar` ran, and
`build/docker/craftless/` was refreshed from the packaged distribution.

Packaged CLI smoke:

```sh
tmp="$(mktemp -d /tmp/craftless-cli-smoke.XXXXXX)" && build/docker/craftless/bin/craftless server start --once --port 0 --workspace "$tmp/workspace"
```

Output:

```text
/tmp/craftless-cli-smoke.s5Bpsr
{"ok":true,"url":"http://127.0.0.1:53114","openapi":"/openapi.json","events":"/events","workspace":"/tmp/craftless-cli-smoke.s5Bpsr/workspace"}
```

Docker build:

```sh
docker build -t craftless:local .
```

Result: image `craftless:local` built successfully from
`eclipse-temurin:21-jre-jammy` with `build/docker/craftless/` copied to
`/opt/craftless/`.

Docker smoke:

```sh
docker run --rm craftless:local /opt/craftless/bin/craftless server start --once --port 0 --workspace /tmp/craftless
```

Output:

```json
{"ok":true,"url":"http://127.0.0.1:42427","openapi":"/openapi.json","events":"/events","workspace":"/tmp/craftless"}
```

Install script smoke:

```sh
tmp="$(mktemp -d /tmp/craftless-install-smoke.XXXXXX)" && CRAFTLESS_VERSION=v0.1.0 CRAFTLESS_INSTALL_DIR="$tmp/bin" CRAFTLESS_HOME="$tmp/home" ./install.sh && "$tmp/bin/craftless" server start --once --port 0 --workspace "$tmp/workspace"
```

Output:

```text
/tmp/craftless-install-smoke.023xYR
craftless 0.1.0 installed to /tmp/craftless-install-smoke.023xYR/bin/craftless
add /tmp/craftless-install-smoke.023xYR/bin to PATH if craftless is not found
{"ok":true,"url":"http://127.0.0.1:53134","openapi":"/openapi.json","events":"/events","workspace":"/tmp/craftless-install-smoke.023xYR/workspace"}
```

## Compatibility Evidence

Live Mojang manifest probe:

```sh
mise exec -- bun -e '<manifest probe from Phase 75 plan>'
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

This is explicit unsupported evidence for the latest release lane. It is not a
Fabric client support claim for `26.2` or `26.3-snapshot-1`.

## Final Gameplay Evidence

Command:

```sh
CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS=90000 CRAFTLESS_FABRIC_SMOKE_ACTION_TIMEOUT_MS=120000 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 mise exec -- gradle :driver-fabric:fabricFinalGameplay
```

Result:

```text
local Minecraft server smoke collected 3 evidence event(s)
serverLog=/Users/robin/Developer/minekube/craftless/driver-fabric/build/craftless-final-gameplay/logs/server.log
evidenceLog=/Users/robin/Developer/minekube/craftless/driver-fabric/build/craftless-final-gameplay/artifacts/server-evidence.jsonl
exitCode=0
BUILD SUCCESSFUL in 1m 56s
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

Public-agent discovery and stream evidence:

```json
{"event":"public-agent-discovery","clientId":"fabric-smoke","request":"GET /openapi.json"}
{"event":"public-agent-discovery","clientId":"fabric-smoke","request":"GET /clients/fabric-smoke/openapi.json"}
{"event":"public-agent-discovery","clientId":"fabric-smoke","request":"GET /clients/fabric-smoke/actions","availableActions":["entity.attack","entity.query","inventory.equip","inventory.query","navigation.follow","navigation.plan","navigation.stop","player.chat","player.look","player.move","player.query","player.raycast","recipe.craft","recipe.query","screen.close","screen.query","task.run","task.status","world.block.break","world.block.interact","world.block.query","world.time.query"]}
{"event":"public-agent-stream","clientId":"fabric-smoke","request":"GET /clients/fabric-smoke/events:stream","bytes":6970}
```

Gameplay outcome summary:

- No `public-agent-blocked` event was written.
- No `task.survival`, `find.tree`, `mine.log`, `craft.sword`, or `kill.cow`
  scenario shortcut appeared in public-agent artifacts.
- Public-agent evidence contains 98 generated action invocations across
  `entity.attack`, `entity.query`, `inventory.equip`, `inventory.query`,
  `navigation.follow`, `navigation.plan`, `player.look`, `player.query`,
  `player.raycast`, `recipe.craft`, `recipe.query`, `screen.query`,
  `world.block.break`, `world.block.interact`, and `world.block.query`.
- The public agent crafted a `Wooden Sword`, equipped it through generated
  `inventory.equip`, navigated to Cows through generated `navigation.plan` and
  `navigation.follow`, attacked a Cow through generated `entity.attack`, and
  proved loot pickup through final `inventory.query`.

Final inventory proof:

```json
{"slot":1,"empty":false,"count":1,"item-name":"Leather"}
{"slot":2,"empty":false,"count":2,"item-name":"Raw Beef"}
{"slot":8,"empty":false,"count":1,"item-name":"Wooden Sword"}
```

Server evidence:

```json
{"type":"PLAYER_JOINED","player":"Player88"}
{"type":"CHAT","player":"Player88","message":"hello from Craftless final gameplay"}
{"type":"PLAYER_DISCONNECTED","player":"Player88"}
```

The server evidence contains only the Craftless client join, chat, and
disconnect. There is no operator item provisioning and no human confirmation
phrase.

## Local Verification

Commands:

```sh
git diff --check
mise run architecture-check
mise run ci
```

Result: all commands exited `0`. `architecture-check` ran protocol, daemon,
CLI, driver-fabric, and Bun Playwright checks. `ci` ran Gradle lint, Gradle
test, and Bun Playwright tests.

## Remote Verification

Commit:

```text
53c4864 docs: refresh evidence after cache integrity
```

GitHub Actions:

```text
run=28308176606
headSha=53c4864aadf209500abc34c749f8f92487b26d0c
workflow=ci
conclusion=success
```
