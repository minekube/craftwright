# Invoke Fallback Naming Removal Evidence

## Scope

Phase 120 removes stale old-invoke wording from active source, tests,
governance, and active specs/plans. It preserves the stable
`DriverSession.invoke(...)` generic invocation contract and does not change
runtime dispatch behavior.

## Red

Command:

```sh
mise exec -- gradle :protocol:test --tests '*NamespacePolicyTest.active code and governance avoid stale invoke wording*'
```

Result before implementation: failed as expected because active tests/docs
still used stale old-invoke wording.

## Green

Commands:

```sh
mise exec -- gradle :protocol:test --tests '*NamespacePolicyTest.active code and governance avoid stale invoke wording*'
mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.server dispatches graph operations through registered operation adapters*' --tests '*LocalSessionApiServerTest.server rejects graph operation availability and schema before operation adapters*'
mise exec -- gradle :driver-fabric:test --tests '*FabricDriverModuleTest.fabric compatibility invoke dispatches unavailable operations from runtime graph*' --tests '*FabricDriverModuleTest.fabric compatibility invoke adapters come from private binding map*'
```

Result after implementation: all focused commands passed locally.

Active stale-wording scan:

```sh
rg -in "legacy invoke|legacyinvoke|fabric legacy invoke" AGENTS.md docs/project-completion-checklist.md docs/superpowers/specs docs/superpowers/plans protocol/src daemon/src driver-fabric/src driver-runtime/src testkit/src cli/src -S
```

Result: exited `1`, meaning no matches in active scanned surfaces.

## Local Gates

Commands:

```sh
git diff --check
mise run ci
```

Result: both commands passed locally. `mise run ci` completed Gradle lint,
unused-check/detekt, Gradle tests, and Bun Playwright tests successfully.
