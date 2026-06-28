# Craftless Agent Instructions

This file is intentionally short. It is only the repository-wide entrypoint for
agents, not the active roadmap, phase log, detailed rule inventory, or module
contract.

Do not append per-phase history, roadmap checkboxes, temporary tasks,
completion evidence, module rules, or long guardrail lists here.

## Read First, Then Update There

Before changing code or docs, read:

- `docs/agent-operating-contract.md` for durable API, driver, transport,
  versioning, and workflow guardrails;
- `docs/agent-module-contracts.md` for durable module-local guardrails;
- the nearest subdirectory `AGENTS.md` as a short pointer to the relevant
  module section;
- `docs/project-completion-checklist.md` for active completion gates, blockers,
  and evidence status;
- `docs/superpowers/phase-index.md` for the maintained phase index.

When an instruction needs to grow, update the owning doc above. Keep every
`AGENTS.md` as a stable routing file.

## Immutable Basics

Craftless uses `com.minekube.craftless` and `minekube.com`. Use `mise` for all
repo tooling. Push directly to `main` when asked. Preserve unrelated user work.
