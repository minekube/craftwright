# Driver Attach Proxy Design

## Problem

The packaged runtime can now launch a Minecraft/Fabric client with the
Craftless Fabric driver mod present, but the supervisor still exposes the
client through `craftless-prepared-client-runtime` with no live driver
attachment. The existing Fabric smoke harness proves a real backend can expose
generated runtime actions in-process, but the normal product path needs a
generic attach handoff from the launched client driver back to the supervisor.

Without an attach proxy, `/clients/{id}/openapi.json`, `/clients/{id}/actions`,
JSON-RPC, SSE, and `POST /clients/{id}:run` cannot become the authority for the
launched packaged client.

## Goals

- Add a supervisor attach method for a running client driver endpoint.
- Replace the prepared placeholder driver session with an attached
  HTTP-backed `DriverSession`.
- Keep the attached driver contract generic: snapshot, connect, actions,
  runtime metadata, runtime graph, invoke, stop, and events.
- Keep gameplay breadth inside the attached driver runtime graph and generated
  OpenAPI projection.
- Use Ktor Client for outbound JVM HTTP.

## Non-Goals

- Do not add public gameplay actions, static descriptors, generated route
  families, CLI gameplay catalogs, Fabric bindings, or survival shortcuts.
- Do not implement the Fabric in-client HTTP endpoint in this phase.
- Do not expose raw Fabric/Yarn/Minecraft names as public contracts.
- Do not claim final gameplay completion or multi-version support from the
  attach proxy alone.

## Acceptance Criteria

- A daemon/service test proves a client initially backed by a prepared session
  can be attached to a different driver session and then exposes the attached
  runtime metadata/actions through generated OpenAPI.
- A daemon HTTP test proves `POST /clients/{id}:attach` can attach a fake
  loopback driver endpoint and `POST /clients/{id}:run` invokes through that
  endpoint.
- `HttpDriverSession` uses Ktor Client and delegates snapshot/connect/actions/
  runtime metadata/runtime graph/invoke/stop/events over JSON endpoints.
- The supervisor OpenAPI/checklist documents this as attach plumbing, not a
  gameplay API.
- Focused daemon tests, ktlint, detekt, and diff checks pass.
