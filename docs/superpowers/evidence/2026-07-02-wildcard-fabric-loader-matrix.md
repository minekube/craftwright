# Wildcard Fabric Loader Matrix Evidence

Date: 2026-07-02
Phase: 203

## Changed

- `VersionDiscoveryService` now applies manifest driver rows with omitted
  `loaderVersion` to each discovered Fabric Loader version when no exact lane
  exists.
- Wildcard rows no longer create a separate `loaderVersion = null` runtime
  target when concrete Fabric Loader versions are discoverable.
- `LocalSessionApiServerTest` now covers a wildcard Fabric driver lane for
  Minecraft `1.21.6` and verifies `/versions/support-targets` reports the
  discovered `0.17.2` and `0.16.14` loader rows as supported.

## Verification

- `mise exec -- gradle :daemon:test --tests 'com.minekube.craftless.daemon.LocalSessionApiServerTest.support targets project wildcard Fabric driver lane onto discovered loader versions'`
- `mise exec -- gradle :daemon:test`
- `git diff --check`
- `mise run lint`
- `mise run ci`
