# Official Fabric JSON-RPC Query Evidence

## Scope

Phase 164 extends the official/latest Fabric attach probe to capture public
JSON-RPC `query` evidence from `POST /clients/{id}:rpc` for live per-client
`openapi`, `actions`, and `resources` projections. This is transport evidence
only: no gameplay action, operation adapter, static catalog, public route, CLI
gameplay command, scenario shortcut, packaging support, or latest/current
support claim is added.

## Red Check

Command:

```sh
rm -f driver-fabric-official/build/craftless-official-attach-probe/client-rpc-openapi.json \
  driver-fabric-official/build/craftless-official-attach-probe/client-rpc-actions.json \
  driver-fabric-official/build/craftless-official-attach-probe/client-rpc-resources.json
test -f driver-fabric-official/build/craftless-official-attach-probe/client-rpc-openapi.json && \
  test -f driver-fabric-official/build/craftless-official-attach-probe/client-rpc-actions.json && \
  test -f driver-fabric-official/build/craftless-official-attach-probe/client-rpc-resources.json
```

Result before implementation: failed with exit code `1` because the official
probe did not write JSON-RPC query artifacts.

## Focused Check

Command:

```sh
mise exec -- gradle :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*'
```

Result: `BUILD SUCCESSFUL`.

## Connected Official Attach Probe

Command:

```sh
rm -rf driver-fabric-official/build/craftless-official-attach-probe
CRAFTLESS_OFFICIAL_FABRIC_ATTACH_PROBE=1 \
CRAFTLESS_OFFICIAL_ATTACH_PROBE_CONNECT=1 \
CRAFTLESS_OFFICIAL_ATTACH_PROBE_TIMEOUT_MS=180000 \
mise exec -- gradle :driver-fabric-official:officialFabricAttachProbe
```

Result: `BUILD SUCCESSFUL`.

Probe output:

```text
official Fabric probe observed connected client state for official-probe
```

JSON-RPC actions artifact inspection:

```sh
jq -r '.result | "actions=" + (length | tostring)' \
  driver-fabric-official/build/craftless-official-attach-probe/client-rpc-actions.json
```

Output:

```text
actions=0
```

JSON-RPC resources artifact inspection:

```sh
jq -r '.result[].id' \
  driver-fabric-official/build/craftless-official-attach-probe/client-rpc-resources.json
```

Output:

```text
runtime
registry
event
client
player
inventory
recipe
world
entity
screen
```

JSON-RPC OpenAPI artifact inspection:

```sh
jq -r '.id, (.result.info.title // empty), (.result["x-craftless-actions"] | length)' \
  driver-fabric-official/build/craftless-official-attach-probe/client-rpc-openapi.json
```

Output:

```text
rpc:official_probe:openapi
Craftless Client Session API
0
```

`probe-result.json` inspection:

```sh
jq -r '{rpcQueryTargets, rpcActionCount, rpcResourceIds, publicActionCount, publicResourceIds, status, connectTarget, streamedEventTypes}' \
  driver-fabric-official/build/craftless-official-attach-probe/probe-result.json
```

Output:

```json
{
  "rpcQueryTargets": [
    "openapi",
    "actions",
    "resources"
  ],
  "rpcActionCount": 0,
  "rpcResourceIds": [
    "runtime",
    "registry",
    "event",
    "client",
    "player",
    "inventory",
    "recipe",
    "world",
    "entity",
    "screen"
  ],
  "publicActionCount": 0,
  "publicResourceIds": [
    "runtime",
    "registry",
    "event",
    "client",
    "player",
    "inventory",
    "recipe",
    "world",
    "entity",
    "screen"
  ],
  "status": "CONNECTED",
  "connectTarget": "127.0.0.1:55789",
  "streamedEventTypes": [
    "client.created",
    "client.attached",
    "client.connected"
  ]
}
```

## Boundary

This phase proves the connected official/latest lane exposes generated graph
projections through the JSON-RPC query transport that generated clients and
agents can use. It intentionally preserves zero official gameplay actions.

## Final Verification

Commands:

```sh
mise exec -- gradle :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest*'
mise run fabric-lane-check-latest-official
mise run ci
git diff --check
```

Results:

- Focused official shared metadata tests: `BUILD SUCCESSFUL`.
- Latest-official lane check: `BUILD SUCCESSFUL`.
- Full local CI: `BUILD SUCCESSFUL`, including Gradle lint, detekt
  unused-check, Gradle tests, and Bun Playwright tests.
- Diff check: exit code `0`.
