# Bootstrap Adapter Key Separation Design

## Problem

Fabric bootstrap operation definitions still paired public operation metadata
with private execution adapter keys through an `adapter: String` field and
per-definition `adapter = FabricBootstrapOperationAdapters.*` assignments.

That kept the transitional bootstrap list shaped like descriptor/binding
pairs. It also made private execution wiring look like part of the public
operation descriptor shape.

## Design

Remove adapter keys from `FabricBootstrapOperationDefinition`. Keep private
adapter-key ownership in a separate mapping from operation id to adapter key.

Runtime graph projection still writes `RuntimeOperationNode.adapter`, because
the invocation layer needs a private dispatch key. The key is looked up from
the separate mapping instead of stored inside the public-shape bootstrap
definition.

The backend uses the same mapping when registering private execution adapters,
so adapter registration and graph projection share one private adapter-key
source.

## Non-Goals

- Do not add gameplay operations.
- Do not remove all bootstrap operation definitions.
- Do not change public action ids or schemas.
- Do not expose adapter keys as public API.
- Do not claim CL-02 is complete.
- Do not make a new Minecraft version support claim.

## Acceptance

- `FabricBootstrapOperationDefinition` has no `adapter` field.
- `fabricBootstrapOperationDefinitions()` has no
  `adapter = FabricBootstrapOperationAdapters.*` assignments.
- Runtime operation nodes still carry adapter keys for private invocation.
- Backend adapter registration uses the same private operation-id to
  adapter-key mapping.
- Focused CL-02d guard tests and the Fabric module test suite pass.
