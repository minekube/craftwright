# Driver Runtime Module Instructions

`driver-runtime/` adapts the stable `driver-api` contract to concrete backends.

## Scope

- `BackendDriverSession`.
- `DriverBackend` abstractions.
- Temporary HMC bridge backend adapter.

## Rules

- Keep bridge details internal. Public results, events, actions, and errors must
  stay Craftless-owned.
- Do not let HMC-Specifics commands, console text, or command syntax become the
  public API.
- The bridge backend is evidence infrastructure only; do not present it as the
  final automation driver.
- Preserve generic action invocation and typed JSON args across the backend
  boundary.
- Prefer shared runtime behavior here over duplicating lifecycle/event logic in
  each driver backend.
- Keep runtime adapters version-neutral. Minecraft/Fabric version divergence
  should arrive as runtime metadata, capability graph nodes, compatibility lane
  decisions, or backend-specific adapters, not duplicated session mechanics.
- Do not add version-specific `DriverSession` implementations or lifecycle
  forks just because one Fabric lane currently works. Keep the session contract
  shared and let version-specific driver code stay behind backend/lane
  boundaries only where the backend truly diverges.
- Prefer one shared `BackendDriverSession` and one shared generic backend
  contract across current, older, and latest/current Fabric lanes. A new
  runtime type for a Minecraft version is a design smell unless the stable
  session contract itself cannot represent the divergence.
- Attached in-client drivers must replace prepared-runtime placeholders before
  their generated OpenAPI/actions/resources are used as evidence.

## Verification

```sh
mise exec -- gradle :driver-runtime:test
```
