# Installed CLI Driver Mod Distribution Evidence

## Scope

Phase 103 closes the install-script and reusable GitHub Action driver-mod gap:
the normal Gradle CLI tar/zip distribution carries
`mods/craftless-driver-fabric.jar`, and `craftless server start` auto-discovers
that installed driver mod when `CRAFTLESS_FABRIC_DRIVER_MOD` is absent.

This is distribution/runtime wiring only. It adds no gameplay descriptors,
static route families, Fabric action bindings, scenario shortcuts, compile-time
daemon dependency on `driver-fabric`, or Minecraft version support claim.

## Red Checks

- `mise exec -- gradle :protocol:test --tests '*NamespacePolicyTest.package cli stages craftless fabric driver mod for docker runtime*'`
  failed because `cli/build.gradle.kts` did not package the remapped Fabric
  driver mod into the normal CLI distribution.
- `mise exec -- gradle :cli:test --tests '*CraftlessCliTest.server start uses packaged fabric driver mod when env is absent*'`
  failed to compile because `CraftlessCli.run(...)` had no distribution-root
  input for installed-driver discovery.

## Green Focused Checks

- `mise exec -- gradle :protocol:test --tests '*NamespacePolicyTest.package cli stages craftless fabric driver mod for docker runtime*' :cli:test --tests '*CraftlessCliTest.server start uses packaged fabric driver mod when env is absent*'`
  passed.

## Final Local Verification

- `git diff --check`
- `mise exec -- gradle :protocol:test --tests '*NamespacePolicyTest.package cli stages craftless fabric driver mod for docker runtime*' :cli:test --tests '*CraftlessCliTest.server start uses packaged fabric driver mod when env is absent*'`
- `mise exec -- gradle :protocol:ktlintCheck :protocol:detekt :cli:ktlintCheck :cli:detekt`
- `mise run package-cli`
- `mise run ci`

All commands above passed locally on 2026-06-28.

Additional package audit:

- `tar -tf cli/build/distributions/craftless-*.tar | rg '/mods/craftless-driver-fabric.jar$'`
  printed `craftless-0.1.0-SNAPSHOT/mods/craftless-driver-fabric.jar`.
- `jar tf cli/build/distributions/craftless-*.zip | rg '/mods/craftless-driver-fabric.jar$'`
  printed `craftless-0.1.0-SNAPSHOT/mods/craftless-driver-fabric.jar`.
- `build/docker/craftless/bin/craftless server start --once --port 0 --workspace /tmp/craftless-phase103-packaged-smoke`
  returned `{"ok":true,...}` from the extracted packaged CLI.
