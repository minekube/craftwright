# Protocol Module Instructions

`protocol/` owns Craftless's machine-readable API metadata and serializable
protocol DTOs.

## Scope

- Route catalog entries for the stable daemon/kernel API.
- OpenAPI document models and emitters.
- Action descriptors, argument schemas, and vendor extensions.
- Public resource/action naming conventions.

## Rules

- Keep OpenAPI and action descriptors authoritative for agents, SDKs, adaptive
  CLI dispatch/help, and tests.
- Keep HTTP verbs as protocol data strings such as `"GET"` and `"POST"`.
  Do not introduce a Craftless-owned HTTP method enum.
- Prefer Craftless-owned action names such as `player.move` and `player.chat`.
  Do not expose Fabric, Yarn, Minecraft implementation, HMC-Specifics, or
  launcher names in public metadata.
- Stable kernel routes belong here; per-client generated behavior should be
  described as metadata and action descriptors, not as static route expansion
  for every possible Minecraft operation.
- Use `kotlinx.serialization` DTOs for public protocol shapes.
- Keep protocol DTOs version-agnostic. Version-specific facts should be data in
  runtime metadata, source evidence, fingerprints, availability reasons, graph
  nodes, schemas, and descriptors.
- Do not model Minecraft/Fabric version differences with new static protocol
  enums or duplicated DTOs unless the public machine contract itself truly
  changes.

## Verification

Use focused tests first:

```sh
mise exec -- gradle :protocol:test
```
