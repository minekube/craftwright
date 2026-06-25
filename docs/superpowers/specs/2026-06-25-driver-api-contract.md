# Driver API Contract

Date: 2026-06-25

## Purpose

`driver-api/` is the JVM contract between the supervisor/daemon and any
in-client driver implementation. The fake daemon session and the future Fabric
driver should use the same session shape so CLI and Playwright routes do not
change when fake state is replaced by real Minecraft control.

## Current Contract

The module currently exposes:

- `DriverSession`
- `DriverClientSnapshot`
- `ConnectionTarget`
- `ChatCommand`
- `PlayerSnapshot`
- `DriverEvent`
- `DriverEventType`
- `FakeDriverSession`

`driver-runtime/` now adds:

- `BackendDriverSession`
- `DriverBackend`
- `DriverBackendResult`
- `DriverBackendAction`
- `HmcBridgeDriverBackend`

`driver-fabric-1_21_6/` now adds the first versioned Fabric/Loom module:

- `CraftwrightFabricClientEntrypoint`
- `FabricDriverBackend`
- `FabricClientGateway`
- `MinecraftFabricClientGateway`
- `fabric.mod.json`
- `craftwright-driver-fabric-1_21_6.mixins.json`

Minimum supported actions:

- snapshot current client state;
- connect to a host/port;
- send chat;
- return player identity/state;
- stop the session;
- return structured driver events.

`FakeDriverSession` is a test and daemon-development implementation. It is not
the final Minecraft driver. It exists so daemon, CLI, and fixture code can use
the same public contract before the Fabric module lands.

`BackendDriverSession` is the first runtime adapter. It keeps `DriverSession`
state and events in Craftwright-owned types while delegating automation actions
to a `DriverBackend`. The current HMC bridge adapter is temporary. The Fabric
module now routes connect, chat, command, and stop actions through a
client-thread gateway. It must still add real player state, movement,
perception, and structured event observation.

## Fabric Handoff

The first Fabric driver module should continue implementing the runtime
backend/session boundary for real client state:

- map `connect(ConnectionTarget)` to in-client server connection behavior;
- map `sendChat(ChatCommand)` to real client chat and slash-command send
  behavior;
- keep Minecraft calls scheduled on the client thread;
- make `player()` return real player state;
- emit `DriverEvent` values for ready, connect, chat, movement, stop, and
  error lifecycle events;
- keep low-level Mixins/accessors in Java when bytecode shape matters.

The public daemon routes remain:

- `POST /clients/{id}/connection/connect`
- `POST /clients/{id}/player/sendChat`
- `GET /clients/{id}/player`
- `POST /clients/{id}/stop`
- `GET /clients/{id}/events`

The Fabric module should change the driver implementation behind those routes,
not the route contract.
