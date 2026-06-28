# Fabric API Cache Resolution Evidence

## Scope

Phase 94 adds Fabric API Maven metadata resolution to cache preparation. Fabric
API is cached as a Fabric mod artifact and exposed through launch mod handles.
This does not add a new compiled Fabric lane or claim new Minecraft client
support.

## Red Evidence

- `mise exec -- gradle :daemon:test --tests '*CachePreparationServiceTest.fabric cache preparation resolves fabric api mod artifact from maven metadata*'`
  failed before implementation because `CachePreparedArtifactKind.FABRIC_MOD`
  and `CacheLaunchPlan.mods` did not exist.

## Green Evidence

- `mise exec -- gradle :daemon:test --tests '*CachePreparationServiceTest.fabric cache preparation resolves fabric api mod artifact from maven metadata*'`
  passed after Fabric API Maven metadata resolution cached a matching
  `+1.21.6` Fabric API jar as a Fabric mod artifact.
- `mise exec -- gradle :protocol:test`
  passed after adding `FABRIC_MOD` and `CacheLaunchPlan.mods`.

## Local Final Gates

- `git diff --check`
  passed.
- `mise exec -- gradle :protocol:test :daemon:test`
  passed.
- `mise exec -- gradle :protocol:ktlintCheck :daemon:ktlintCheck`
  passed.
- `mise exec -- gradle :protocol:detekt :daemon:detekt`
  passed.

## Remote CI

Not waited on during active development. Local forced CI is the working gate;
remote CI may continue in the background after push.

## Notes

- Fabric API resolution uses Fabric Maven metadata at
  `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml`.
- Latest/current and representative older Fabric client runtime support remains
  open until runnable provider-backed support lands and generated API/CLI
  gameplay verification passes.
