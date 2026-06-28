# Version Support Completion Gate Evidence

## Scope

Phase 91 tightens the final completion contract so unsupported latest/older
compatibility lanes cannot satisfy the active multi-version support goal. It
does not add a new compiled Fabric lane or claim new Minecraft client support.

## Red Evidence

- `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.completion gate does not accept unsupported version lanes as support*'`
  failed before implementation because the final completion gate still accepted
  an explicitly accepted support boundary.

## Green Evidence

- `mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.completion gate does not accept unsupported version lanes as support*'`
  passed after the final completion gate was changed to require runnable
  latest/current and representative older support evidence.

## Local Final Gates

- source scan:
  `rg -n "accepted support boundary|unsupported lane.*completion|runnable support evidence" docs/project-completion-checklist.md AGENTS.md`
  returned only the required `runnable support evidence` matches in `AGENTS.md`
  and `docs/project-completion-checklist.md`.
- `git diff --check`
  passed.
- `mise exec -- gradle :driver-fabric:test`
  passed.

## Remote CI

Not waited on during active development. Local forced CI is the working gate;
remote CI may continue in the background after push.

## Notes

- Latest `26.2` and representative older `1.20.6` compatibility records remain
  diagnostic unsupported lane evidence until actual runnable client lane support
  lands.
- No new public gameplay action, generated route family, CLI gameplay catalog,
  Fabric execution binding, scenario shortcut, compiled lane, public
  version-specific API, or Minecraft support claim should be added in this
  phase.
