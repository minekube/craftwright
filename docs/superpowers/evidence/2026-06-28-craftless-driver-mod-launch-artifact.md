# Craftless Driver Mod Launch Artifact Evidence

## Scope

Phase 96 wires the prepared-runtime launch path so a configured Craftless
Fabric in-client driver mod can be cached as a `FABRIC_MOD` artifact and added
to `CacheLaunchPlan.mods`.

This is launch artifact wiring only. It does not add public gameplay actions,
static descriptors, CLI gameplay catalogs, Fabric bindings, version-specific
APIs, or Minecraft version support claims.

## Red

Command:

```sh
mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.prepared runtime launch plan includes configured craftless fabric driver mod*'
```

Expected failure observed before implementation:

```text
No parameter with name 'clientRuntimeDriverModProvider' found.
Unresolved reference 'StaticClientRuntimeDriverModProvider'.
```

## Green

Command:

```sh
mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.prepared runtime launch plan includes configured craftless fabric driver mod*'
```

Result:

```text
BUILD SUCCESSFUL
```

The focused test proves:

- the daemon accepts a configured Fabric driver mod provider;
- the configured mod is copied into `cache/mods/craftless/<sha>.jar`;
- the copied mod is recorded as a `FABRIC_MOD` artifact;
- the launch plan includes the copied driver mod handle in `launch.mods`.

## Local Gates

Commands:

```sh
git diff --check
mise exec -- gradle :daemon:test
mise exec -- gradle :daemon:ktlintCheck :daemon:detekt
```

Results:

```text
git diff --check: exit 0
:daemon:test: BUILD SUCCESSFUL
:daemon:ktlintCheck :daemon:detekt: BUILD SUCCESSFUL
```

Remote CI was not used as a blocking gate for this phase.
