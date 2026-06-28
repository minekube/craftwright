# Static Unsupported Version Lane Removal Evidence

## Scope

Phase 93 removes hand-maintained latest/older unsupported Fabric lanes from
active product runtime code. Historical probe evidence remains as history, but
the runtime matrix now contains provider-backed lanes and falls back to generic
unsupported-version handling for non-provider-backed versions.

## Red Evidence

- `mise exec -- gradle :driver-fabric:test --tests '*FabricCompatibilityMatrixTest.matrix does not catalog static unsupported latest or older lanes*'`
  failed before implementation because `26.2` still resolved to
  `latest-release-26-2`.

## Green Evidence

- `mise exec -- gradle :driver-fabric:test --tests '*FabricCompatibilityMatrixTest*' --tests '*FabricDriverModuleTest*'`
  passed after the static latest/older matrix entries and the `26.2` smoke
  JSON branch were removed.

## Local Final Gates

- `git diff --check`
  passed.
- `rg -n "latest-release-26-2|older-release-1-20-6|\\\"26\\.2\\\" ->" driver-fabric/src/main driver-fabric/build.gradle.kts README.md docs/roadmap.md`
  returned no active product/current-doc matches.
- `mise exec -- gradle :driver-fabric:test`
  passed.

## Remote CI

Not waited on during active development. Local forced CI is the working gate;
remote CI may continue in the background after push.

## Notes

- This phase does not delete historical evidence files that recorded old probe
  outputs.
- Latest/current and representative older Fabric client runtime support remains
  open until runnable provider-backed support lands and generated API/CLI
  gameplay verification passes.
