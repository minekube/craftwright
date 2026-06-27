# Phase 35: Final Confirmation Timeout Artifact Plan

## Goal

Make the final gameplay hold outcome explicit when Robin's Minecraft chat
confirmation is not observed before timeout.

## Steps

- [x] Add a focused failing test in `FabricDriverModuleTest` for
  `final-gameplay-confirmation-timeout.json`.
- [x] Implement timeout artifact writing in `FabricClientSmokeController` after
  the confirmation hold expires.
- [x] Ensure the existing successful-confirmation path still exits before the
  timeout artifact is written.
- [x] Update `AGENTS.md` and `docs/project-completion-checklist.md` with Phase
  35 and the latest held-run evidence.
- [x] Verify with focused `driver-fabric` tests and docs whitespace checks.

## Verification

```sh
mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric smoke controller writes confirmation timeout artifact when Robin chat is not observed*'
mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric smoke controller stops final session after configured chat confirmation evidence*' --tests '*FabricDriverModuleTest.fabric smoke controller writes confirmation timeout artifact when Robin chat is not observed*'
git diff --check
```
