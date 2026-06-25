# Fabric Driver Module Instructions

`driver-fabric-1_21_6/` is the current transitional Fabric/Loom driver module.
The target architecture is one consolidated `driver-fabric` module with
internal version-aware bindings where practical. Delete this dir when made obsolete.

## Scope

- Fabric client entrypoint and metadata.
- Mixins/accessors and bytecode-sensitive Minecraft glue.
- Client-thread gateway for connect, chat, command, stop, and generated action
  invocation.
- First real Fabric backend behavior.

## Rules

- Java is appropriate for Mixins, accessors, and exact bytecode signatures.
  Kotlin is appropriate for driver/runtime logic where Fabric classloading is
  proven safe.
- Keep Minecraft calls on the client thread.
- Do not expose Fabric, Yarn, intermediary, or Minecraft implementation names as
  public action IDs, routes, CLI commands, or docs.
- Register only actions that this runtime can actually support. Per-client
  OpenAPI/action descriptors should reflect support checks.
- Prefer internal version-aware bindings and reflection/mapping probes over new
  public Gradle subprojects per Minecraft version.
- Do not depend on the HMC bridge for final Fabric behavior.

## Verification

```sh
mise exec -- gradle :driver-fabric-1_21_6:test
```
