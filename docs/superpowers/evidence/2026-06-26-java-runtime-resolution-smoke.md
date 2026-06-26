# Java Runtime Resolution Smoke Evidence

Date: 2026-06-26

## Purpose

Verify that the local Minecraft server smoke can run Minecraft `26.2` with Java
selected from Craftless Java runtime resolver output, without setting
`CRAFTLESS_SMOKE_JAVA_EXECUTABLE`.

## Commands

Built the current CLI distribution:

```sh
mise exec -- gradle :cli:installDist
```

Started the local supervisor API with a temporary workspace:

```sh
cli/build/install/craftless/bin/craftless server start \
  --port 49277 \
  --workspace /tmp/craftless-java-runtime-api/workspace
```

Resolved Java for Minecraft `26.2` through the supervisor API:

```sh
curl -fsS -X POST http://127.0.0.1:49277/runtimes/java:resolve \
  -H 'Content-Type: application/json' \
  --data '{"minecraftVersion":"26.2"}' \
  | tee /tmp/craftless-java-runtime-api/java-selection-26.2.json
```

Ran local server smoke using the resolver output:

```sh
CRAFTLESS_LOCAL_SERVER_SMOKE=1 \
CRAFTLESS_SMOKE_MINECRAFT_VERSION=26.2 \
CRAFTLESS_LOCAL_SERVER_SMOKE_ROOT=/tmp/craftless-compat-26.2-server-selection \
CRAFTLESS_SMOKE_JAVA_SELECTION_JSON="$(cat /tmp/craftless-java-runtime-api/java-selection-26.2.json)" \
CRAFTLESS_SMOKE_READINESS_TIMEOUT_MS=90000 \
CRAFTLESS_SMOKE_SHUTDOWN_TIMEOUT_MS=10000 \
mise exec -- gradle :testkit:localMinecraftServerSmoke
```

## Result

The smoke passed:

```text
local Minecraft server smoke collected 0 evidence event(s)
serverLog=/tmp/craftless-compat-26.2-server-selection/logs/server.log
evidenceLog=/tmp/craftless-compat-26.2-server-selection/artifacts/server-evidence.jsonl
exitCode=0
```

The smoke wrote Java selection evidence to:

```text
/tmp/craftless-compat-26.2-server-selection/artifacts/java-runtime-selection.json
```

That evidence selected Java 25:

```json
{
  "requirement": {
    "majorVersion": 25,
    "component": "java-runtime-epsilon",
    "reason": "minecraft-version-metadata"
  },
  "status": "SELECTED",
  "selected": {
    "provider": "MISE",
    "majorVersion": 25,
    "version": "25.0.2"
  }
}
```

The resolver also rejected installed Java 21 candidates with
`java-major-too-low`.

The Minecraft server log shows the server started as `26.2` and reached ready:

```text
Starting minecraft server version 26.2
Done (1.301s)! For help, type "help"
```

## Remaining Boundary

This proves Java runtime selection for the server smoke. It does not prove
Fabric client `26.2` support; that remains gated by Phase 26 driver-lane
selection.
