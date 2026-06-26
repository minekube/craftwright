# Minecraft 26.2 Compatibility Probe

Date: 2026-06-26

## Intent

Probe the latest Minecraft release lane through existing Craftless surfaces
without adding static compatibility shortcuts.

The official Mojang version manifest reported:

- latest release: `26.2`
- latest snapshot: `26.3-snapshot-1`

## Commands

Plain server smoke with repository-pinned Java 21:

```sh
CRAFTLESS_LOCAL_SERVER_SMOKE=1 \
CRAFTLESS_SMOKE_MINECRAFT_VERSION=26.2 \
CRAFTLESS_LOCAL_SERVER_SMOKE_ROOT=/tmp/craftless-compat-26.2-server \
CRAFTLESS_SMOKE_READINESS_TIMEOUT_MS=90000 \
CRAFTLESS_SMOKE_SHUTDOWN_TIMEOUT_MS=10000 \
mise exec -- gradle :testkit:localMinecraftServerSmoke
```

Plain server smoke with Java 25 through mise:

```sh
CRAFTLESS_LOCAL_SERVER_SMOKE=1 \
CRAFTLESS_SMOKE_MINECRAFT_VERSION=26.2 \
CRAFTLESS_LOCAL_SERVER_SMOKE_ROOT=/tmp/craftless-compat-26.2-server-java25 \
CRAFTLESS_SMOKE_JAVA_EXECUTABLE="$HOME/.local/share/mise/installs/java/temurin-25.0.3+9.0.LTS/bin/java" \
CRAFTLESS_SMOKE_READINESS_TIMEOUT_MS=90000 \
CRAFTLESS_SMOKE_SHUTDOWN_TIMEOUT_MS=10000 \
mise exec java@temurin-25.0.3+9.0.LTS gradle@9.6.0 -- gradle :testkit:localMinecraftServerSmoke
```

Fabric client smoke against a `26.2` server:

```sh
CRAFTLESS_FABRIC_CLIENT_SMOKE=1 \
CRAFTLESS_SMOKE_MINECRAFT_VERSION=26.2 \
CRAFTLESS_LOCAL_SERVER_SMOKE_ROOT=/tmp/craftless-compat-26.2-fabric \
CRAFTLESS_SMOKE_JAVA_EXECUTABLE="$HOME/.local/share/mise/installs/java/temurin-25.0.3+9.0.LTS/bin/java" \
CRAFTLESS_SMOKE_READINESS_TIMEOUT_MS=90000 \
CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS=120000 \
CRAFTLESS_SMOKE_SHUTDOWN_TIMEOUT_MS=10000 \
CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS=30000 \
mise exec java@temurin-25.0.3+9.0.LTS gradle@9.6.0 -- gradle :driver-fabric:fabricClientSmoke
```

Supervisor API cache and client-create probe:

```sh
cli/build/install/craftless/bin/craftless server start --port 49249 --workspace /tmp/craftless-api-26.2-live.m8PY8X/workspace
curl -X POST http://127.0.0.1:49249/cache:prepare -H 'content-type: application/json' \
  --data '{"minecraftVersion":"26.2","loader":"FABRIC"}'
curl -X POST http://127.0.0.1:49249/clients -H 'content-type: application/json' \
  --data '{"id":"probe26","version":"26.2","loader":"FABRIC","profile":{"kind":"OFFLINE","name":"Probe26"}}'
```

## Findings

- `26.2` server does not start on Java 21. The server jar reports class file
  version `69.0`, which requires Java 25.
- `26.2` server starts successfully when Java 25 is supplied through mise.
- Current Fabric real-client smoke still launches Minecraft `1.21.6` with
  Fabric API `0.128.2+1.21.6` and Fabric Loader `0.19.3`.
- The `1.21.6` client attempted to connect to the `26.2` server, but no player
  joined and no server-side join evidence was produced.
- The in-client API emitted `client.connected` after accepting the connect
  request even though the server never observed a joined player.
- Standalone supervisor `/clients` creation for `version=26.2` fails because
  the local API server is wired to `DriverSessionFactory.unavailable()`.
- `/cache:prepare` for `26.2` began downloading Minecraft metadata and assets,
  but eventually failed with a 60 second socket timeout on one asset URL.

## Blockers

1. Java runtime selection must be version-aware. The project-level Java 21 pin
   is not enough for Minecraft `26.2` server/client lanes.
2. The Fabric driver is still a compiled `1.21.6` lane. Version selection in
   smoke/server setup does not select a matching Fabric client/mod lane.
3. `client.connected` currently means the connect action was accepted by the
   client, not that a server join was confirmed. Cross-version probes need
   server-observed join or client state evidence.
4. The standalone supervisor API can create only records when a real driver
   runtime is unavailable; it cannot currently install and launch a versioned
   Fabric client with the Craftless driver.
5. Cache preparation is synchronous and fragile for large Minecraft asset sets.
   It needs resumable/idempotent artifact jobs, bounded retries with jitter,
   partial progress reporting, and clear retryable failure semantics.

## Implication

The next Phase 26 implementation should start with compatibility matrix and
runtime/provider facades, not with another hard-coded Fabric action. The matrix
must include Java runtime requirements, compiled Fabric lane metadata, and
separate server/client compatibility evidence.
