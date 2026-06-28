# Testkit Module Instructions

`testkit/` owns fake clients, fake servers, fixtures, and integration helpers.

## Scope

- Fake Minecraft/session objects for unit and route tests.
- Reusable test fixtures for daemon, protocol, driver, and CLI tests.
- Helpers for artifact/event assertions.

## Rules

- Fakes should exercise the same action descriptor and generic invocation paths
  as real drivers.
- Fake driver sessions belong here, not in product driver/runtime modules.
- Keep tests deterministic and offline by default.
- Do not hide product behavior in test-only shortcuts that bypass public
  protocol or driver contracts.
- Prefer focused fixtures over broad global test state.
- Version compatibility fixtures should validate resolver, lane, metadata, and
  attach behavior through public contracts. Do not fake success by hard-coding
  gameplay catalogs or bypassing generated OpenAPI/action/resource discovery.

## Verification

```sh
mise exec -- gradle :testkit:test
```
