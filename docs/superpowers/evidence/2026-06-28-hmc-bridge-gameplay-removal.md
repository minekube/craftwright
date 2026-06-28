# HMC Bridge Gameplay Removal Evidence

## Scope

Phase 81 removes stale gameplay action exposure from the temporary HMC bridge.
The bridge remains launch/lifecycle evidence only. Gameplay must come from the
Fabric runtime capability graph and generated per-client OpenAPI path.

## Red Evidence

- `mise exec -- gradle :bridge-hmc:test --tests '*HmcBridgeBackendTest.bridge backend source has no gameplay helpers*'`
  failed before implementation because `HmcBridgeBackend.kt` still contained
  gameplay helper code.
- `mise exec -- gradle :driver-runtime:test --tests '*BackendDriverSessionTest.hmc bridge backend is lifecycle only and exposes no gameplay actions*' --tests '*BackendDriverSessionTest.hmc bridge backend has no static gameplay action catalog*'`
  failed before implementation because `HmcBridgeDriverBackend` still exposed
  and invoked static bridge-owned `player.chat` and `player.move` actions.

## Green Evidence

- `mise exec -- gradle :bridge-hmc:test`
- `mise exec -- gradle :driver-runtime:test`

Both focused regressions passed after removing bridge gameplay helpers,
driver action descriptors, invocation branches, and gameplay smoke-plan steps.

## Local Final Gates

- `git diff --check`
- `mise run architecture-check`
- `mise run ci`

All local gates passed before commit.

## Remote CI

- Commit: `74ac1fe480f94cfd504dfbcc5c92c28d25e94b78`
- GitHub Actions run: `28310939885`
- Workflow: `ci`
- Result: passed.

## Notes

- No Fabric generated actions, generated route families, CLI gameplay catalogs,
  scenario shortcuts, compiled lanes, or Minecraft support claims were added.
- HMC bridge `ClientAction` is lifecycle-only.
- HMC bridge unsupported gameplay invocation errors remain Craftless-owned.
