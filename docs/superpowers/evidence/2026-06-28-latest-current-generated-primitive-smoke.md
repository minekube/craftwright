# Latest Current Generated Primitive Smoke Evidence

Phase: 183

Scope: closes CL-03f and therefore closes CL-03. This does not close CL-04,
CL-05, CL-06, CL-07, or CL-08.

## What This Proves

The packaged latest-current product lane can now:

- resolve `latest-release` to Minecraft `26.2`;
- create and attach the packaged official Fabric client;
- connect to a real local Minecraft server;
- fetch generated per-client OpenAPI;
- select a generated operation from `x-craftless-actions`;
- invoke that generated operation through JSON-RPC `method=invoke`;
- invoke the same operation through the adaptive packaged CLI.

The probe does not use a static CLI gameplay command, a static Kotlin gameplay
catalog, driver internals, server-provisioned inventory, or `task.*` scenario
shortcuts.

## Red/Green Guard

Red command:

```sh
mise exec -- bun test playwright/src/distribution.test.ts
```

Red result:

```text
Expected to contain: "client-generated-action-selected.json"
13 pass
1 fail
```

Green command:

```sh
mise exec -- bun test playwright/src/distribution.test.ts
```

Green result:

```text
14 pass
0 fail
114 expect() calls
```

## Live Product Probe

Command:

```sh
mise run packaged-latest-current-probe
```

Result:

```text
local Minecraft server smoke collected 1 evidence event(s)
serverLog=build/craftless-packaged-latest-current-probe/logs/server.log
evidenceLog=build/craftless-packaged-latest-current-probe/artifacts/server-evidence.jsonl
exitCode=0

BUILD SUCCESSFUL in 31s
```

Artifact root:

```text
driver-fabric/build/craftless-packaged-latest-current-probe/artifacts
```

New CL-03f artifacts:

- `client-generated-action-selected.json`
- `client-rpc-invoke-generated.json`
- `client-cli-invoke-generated.log`

## Selected Generated Action

`client-generated-action-selected.json` selected:

```json
{
  "id": "world.time.query",
  "source": "runtime-probe",
  "availability": "available",
  "availabilityReason": null
}
```

The selector read this from live per-client OpenAPI `x-craftless-actions` and
required an available action with no required arguments.

## JSON-RPC Invocation Transcript

`client-rpc-invoke-generated.json`:

```json
{
  "id": "invoke-generated:world.time.query",
  "result": {
    "action": "world.time.query",
    "status": "ACCEPTED",
    "message": "official lane action world.time.query queried",
    "data": {
      "time": 2147,
      "time-of-day": 2147
    }
  },
  "error": null,
  "jsonrpc": "2.0"
}
```

## Adaptive CLI Transcript

`client-cli-invoke-generated.log`:

```json
{
  "action": "world.time.query",
  "status": "ACCEPTED",
  "message": "official lane action world.time.query queried",
  "data": {
    "time": 2157,
    "time-of-day": 2157
  }
}
```

The CLI command was:

```sh
craftless clients "$CLIENT_ID" run "$GENERATED_ACTION_ID" --api "$API"
```

where `GENERATED_ACTION_ID` was selected from generated OpenAPI metadata.

## Probe Summary

`packaged-probe-summary.json` records:

```json
{
  "status": "connected",
  "clientId": "latest-current",
  "minecraftVersion": "latest-release",
  "concreteLatestVersion": "26.2",
  "generatedInvocationAction": "world.time.query",
  "actionCount": 1,
  "openapiActionCount": 1
}
```

## Server Evidence

`server-evidence.jsonl`:

```json
{"type":"PLAYER_JOINED","player":"LatestCurrent"}
```

SSE lifecycle evidence still includes:

```text
client.created
client.attached
client.connected
```

## Remaining Open Work

The next active gate is CL-04: the representative older packaged lane must pass
the same product gate set. CL-05 through CL-08 remain open after that.
