# Fabric Execution Adapter Naming Design

## Problem

The remaining private Fabric execution layer still used names such as
`FabricActionBinding`, `defaultFabricActionBindings`, and
`actionBindingsById`.

That naming was stale. These classes no longer own public action descriptors,
schemas, OpenAPI metadata, CLI behavior, or gameplay catalog authority. They
are private execution adapters selected by runtime graph operation adapter
keys.

Leaving the old binding names in production code makes future work more
likely to add descriptor/binding pairs instead of working on generic
discovery/projection/invocation.

## Design

Rename the private Fabric execution layer terminology:

- `FabricActionBinding` -> `FabricExecutionAdapter`
- `defaultFabricActionBindings()` -> `defaultFabricExecutionAdapters()`
- `actionBindingsById` -> `executionAdaptersByOperationId`
- `Fabric*ActionBinding` implementation objects -> `Fabric*ExecutionAdapter`
- `FabricActionBindings.kt` -> `FabricExecutionAdapters.kt`

This is a naming and guardrail phase. Runtime behavior, operation ids, adapter
keys, generated OpenAPI, and invocation dispatch remain unchanged.

## Non-Goals

- Do not add gameplay operations.
- Do not remove `fabricBootstrapOperationDefinitions()`.
- Do not claim CL-02 is complete.
- Do not change public OpenAPI, CLI, or daemon route shapes.
- Do not make a new Minecraft version support claim.

## Acceptance

- Production `driver-fabric` code contains no `FabricActionBinding`,
  `defaultFabricActionBindings`, `actionBindings`, or `actionBindingsById`
  names.
- The renamed private execution adapters still expose operation ids only
  through bootstrap operation definitions.
- The backend still registers operation adapters from runtime graph adapter
  keys.
- Existing Fabric operation adapter and capability-probe tests pass.
