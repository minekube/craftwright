# Daemon Module Instructions

`daemon/` owns the local supervisor/session API and wires clients to driver
sessions.

## Scope

- Ktor local API server.
- Client session lifecycle and in-memory session state.
- Stable `/openapi.json` kernel API.
- Per-client `/clients/{id}/openapi.json`, `/clients/{id}/actions`, and
  `POST /clients/{id}:run`.
- Runtime driver factory integration.
- Version, loader, Java runtime, artifact cache, driver mod selection, and
  attach-environment orchestration.

## Rules

- Use Ktor Server for HTTP routes and Ktor Client in tests.
- Do not add OkHttp, Java `HttpClient`, `com.sun.net.httpserver`, or
  hand-rolled HTTP clients.
- Do not add public static routes such as `/clients/{id}/player/sendChat` for
  every action. Use action descriptors plus `POST /clients/{id}:run`, with
  generated aliases only when they are described by OpenAPI.
- Keep daemon routes Craftless-owned and independent from bridge command
  strings or Minecraft implementation names.
- Preserve typed JSON action args. Do not coerce every action arg to strings.
- Emit structured session events for lifecycle, chat, movement, stop, and
  errors where the driver result provides enough information.
- Keep version-specific knowledge in data and resolver services: Mojang
  manifests, Fabric loader/API resolution, Java runtime selection, driver mod
  manifests, and compatibility probes. Do not branch daemon routes or public API
  shapes per Minecraft version.
- A launched client is not enough completion evidence. The daemon must surface
  whether the prepared runtime session was replaced by an attached in-client
  driver before generated gameplay OpenAPI/actions/resources are claimed as
  working.

## Verification

Use focused tests first:

```sh
mise exec -- gradle :daemon:test
```
