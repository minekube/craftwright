# Active Docs Agent Onboarding Alignment Evidence

Date: 2026-06-28

## Scope

Phase 170 aligns active README and repo-local agent onboarding docs with the
generated runtime graph/OpenAPI authority model.

This is a docs-governance slice only. It does not add gameplay operations,
route families, CLI commands, Fabric bindings, scenario shortcuts, version
lanes, or support claims.

## README Agent Usage

`README.md` now includes an `Agent Usage` section that tells agents to fetch
supervisor and per-client OpenAPI, treat per-client action/resource metadata
as authority, use `/actions` and `/resources` as projection evidence, subscribe
to SSE, invoke only advertised actions, and reject server-provisioned
inventory, internals, and scenario actions as product proof.

## Active Docs Scan

Command:

```sh
rg -n "<old-product-name>|<old-domain>|TypeScript SDK|typescript sdk|static (action|gameplay)|static.*catalog|scenario shortcut|hand-written gameplay|sendChat|player/sendChat|task\\.survival" README.md AGENTS.md docs/agent-operating-contract.md docs/agent-module-contracts.md docs/roadmap.md docs/final-gameplay-runbook.md docs/agent-skills.md .agents/skills/craftless-public-gameplay-agent/SKILL.md -S
```

Observed before final verification:

- No old product-name matches in active docs.
- No old domain matches in active docs.
- `TypeScript SDK` matches are negative guardrails such as removed/deferred SDK
  wording.
- `task.survival.*`, `scenario shortcut`, `sendChat`, and static-catalog
  matches are negative guardrails or historical evidence references, not
  active product instructions.

## Playwright Helper And Distribution Tests

Command:

```sh
mise exec -- bun test playwright
```

Observed:

- Exit code: `0`
- Bun result: `19 pass`, `0 fail`

## Architecture Check

Command:

```sh
mise run architecture-check
```

Observed:

- Exit code: `0`
- Gradle protocol, daemon, CLI, and driver-fabric checks completed
  successfully.
- Bun Playwright helper/distribution tests completed with `19 pass`, `0 fail`.

## Local CI

Command:

```sh
mise run ci
```

Observed:

- Exit code: `0`
- Gradle result: `BUILD SUCCESSFUL`
- Bun result: `19 pass`, `0 fail`

## Diff Check

Command:

```sh
git diff --check
```

Observed:

- Exit code: `0`
