# Fabric Driver Module Instructions

`driver-fabric/` owns the Fabric/Loom in-client driver module. Keep the durable
design version-agnostic: shared discovery, projection, invocation, event, and
transport code is the default. Add per-version code only where Minecraft,
Fabric API, mappings, or bytecode signatures actually diverge and a shared
reflection/compatibility shim is not practical.

## Scope

- Fabric client entrypoint, bootstrap selection, and compiled lane metadata.
- Mixins/accessors and bytecode-sensitive Minecraft glue.
- Runtime capability graph discovery and Craftless-owned projection.
- Client-thread gateway for connect, chat, stop, and generated action
  invocation.
- Real Fabric backend behavior and version-aware compatibility lanes.

## Rules

- Java is appropriate for Mixins, accessors, and exact bytecode signatures.
  Kotlin is appropriate for driver/runtime logic where Fabric classloading is
  proven safe.
- Keep Minecraft calls on the client thread.
- Do not expose Fabric, Yarn, intermediary, or Minecraft implementation names as
  public action IDs, routes, CLI commands, or docs.
- Work on the generic discovery/projection/invocation system first. Do not add a
  new public gameplay action by hand-writing one descriptor plus one binding
  just because a real binding can be written.
- Do not register static placeholder descriptors for future gameplay actions.
- If an unavailable action/resource appears in per-client OpenAPI, it must come
  from a runtime discovery probe that inspected the running client and records
  why the operation is unavailable.
- Prefer shared reflection/mapping probes, compatibility lanes, and generated
  metadata over copied per-version driver trees.
- When version-specific code is unavoidable, isolate only the diverging adapter,
  accessor, mixin, or provider behind a lane boundary. Keep action/resource
  naming, schemas, invocation dispatch, Ktor loopback, self-attach, and OpenAPI
  projection shared.
- Every new lane must prove the same public contracts: self-attach replaces the
  prepared runtime session, generated actions/resources are non-empty when the
  runtime supports them, and public API output stays Craftless-owned.
- Do not depend on the HMC bridge for final Fabric behavior.

## Verification

```sh
mise exec -- gradle :driver-fabric:test
```
