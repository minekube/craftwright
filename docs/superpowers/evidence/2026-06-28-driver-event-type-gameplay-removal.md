# Driver Event Type Gameplay Removal Evidence

## Scope

Phase 119 removes gameplay-specific `CHAT` and `MOVEMENT` values from the
stable `DriverEventType` contract. Accepted action observations stay
operation-id-owned through daemon session/live events.

This phase intentionally does not touch `LocalServerEvidenceType.CHAT` or
`LocalServerEvidenceType.MOVEMENT`; those are local server log evidence types,
not stable driver event contract values.

## Red

Command:

```sh
mise exec -- gradle :driver-api:test --tests '*DriverSessionContractTest.driver event types do not expose static gameplay categories*'
```

Result before implementation: failed because `DriverEventType` still exposed
`CHAT` and `MOVEMENT`.

## Green

Commands:

```sh
mise exec -- gradle :driver-api:test --tests '*DriverSessionContractTest.driver event types do not expose static gameplay categories*'
mise exec -- gradle :testkit:test --tests '*FakeDriverSessionTest.*'
mise exec -- gradle :daemon:test --tests '*ClientSessionServiceTest.created clients expose a driver session contract*'
mise exec -- gradle :driver-fabric:test --tests '*FabricDriverSelfAttachTest.loopback endpoint exposes driver session contract*'
```

Result after implementation: all focused commands passed locally.

## Static Reference Scan

Command:

```sh
rg -n "DriverEventType\.(CHAT|MOVEMENT)|\bCHAT,|\bMOVEMENT,|it\.type\.name == \"MOVEMENT\"" driver-api/src driver-runtime/src daemon/src testkit/src driver-fabric/src/main driver-fabric/src/test daemon/src/test testkit/src/test -S
```

Result: no `DriverEventType.CHAT` or `DriverEventType.MOVEMENT` references
remain. Remaining `CHAT` and `MOVEMENT` hits are limited to local server log
evidence fixtures.

## Local Gates

Commands:

```sh
git diff --check
mise run ci
```

Result: both commands passed locally. `mise run ci` completed Gradle lint,
unused-check/detekt, Gradle tests, and Bun Playwright tests successfully.
