# Official World Time Invocation Evidence

Date: 2026-06-28

## Scope

Phase 180 advances CL-03e only. The official 26.x/latest-current lane can now
invoke the generated `world.time.query` operation through an internal official
world-time provider.

CL-03 remains open. This phase does not package the official lane as supported
and does not provide connected OpenAPI/SSE/JSON-RPC artifacts or latest-lane
gameplay smoke.

## Red

Command:

```sh
mise exec -- gradle :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest.official backend invokes generated world time query through lane provider*'
```

Observed before implementation:

- Exit code: `1`
- Failure: compile errors for missing `worldTimeProvider`,
  `OfficialFabricWorldTimeProvider`, and `OfficialFabricWorldTime`.

## Green

Command:

```sh
mise exec -- gradle :driver-fabric-official:test --tests '*OfficialFabricSharedRuntimeMetadataTest.official backend invokes generated world time query through lane provider*'
```

Observed:

- Exit code: `0`
- Gradle result: `BUILD SUCCESSFUL`

## Broad Local Verification

Command:

```sh
git diff --check
```

Observed:

- Exit code: `0`

Command:

```sh
mise exec -- gradle :driver-fabric-official:test
```

Observed:

- Exit code: `0`
- Gradle result: `BUILD SUCCESSFUL`

Command:

```sh
mise run fabric-lane-check-latest-official
```

Observed:

- Exit code: `0`
- Status artifact: `status=compiled`

Command:

```sh
mise run architecture-check
```

Observed:

- Exit code: `0`
- Gradle protocol, daemon, CLI, and driver-fabric checks passed.
- Bun Playwright helper/distribution tests: `19 pass`, `0 fail`.

## Remaining CL-03 Work

- A connected official client still needs fresh generated OpenAPI,
  actions/resources, SSE, JSON-RPC query, and JSON-RPC subscription artifacts.
- The packaged CLI still must create or attach the latest/current lane through
  the supervisor API.
- Latest/current public API/CLI gameplay smoke remains open.
