# Codex Evidence Completion Gate Design

## Goal

Replace the old human Minecraft chat completion gate with a concrete
Codex-verifiable evidence gate while preserving the core Craftless invariant:
final gameplay must be composed through generated public OpenAPI/actions,
resources, SSE/JSON-RPC evidence, adaptive CLI behavior, and agent guidance.

## Requirements

- Human co-play and Minecraft chat confirmation are optional diagnostic
  evidence, not required completion gates.
- Final completion requires current evidence for:
  - `mise run ci`;
  - CLI smoke;
  - Docker runtime smoke;
  - release/install checks;
  - latest/26.x compatibility probe;
  - representative older-version compatibility probe;
  - final honest survival gameplay through the public API/CLI only.
- Final gameplay evidence must not use server-provisioned inventory, pre-seeded
  inventory, manual movement for Craftless, `task.survival.*`, or scenario
  shortcut actions such as `find.tree`, `craft.sword`, or `kill.cow`.
- Existing ready, confirmation, and timeout artifacts may remain as diagnostic
  harness outputs, but `final-gameplay-confirmation.json` must not be required
  for completion.
- Active docs, checklist, runbook, README, AGENTS.md, and repo-local skills
  must agree on this rule.

## Non-Goals

- Do not add public gameplay actions, generated route families, CLI gameplay
  catalogs, Fabric descriptor/binding pairs, or scenario shortcuts.
- Do not claim support for new Minecraft versions without compatibility
  evidence.
- Do not remove historical specs or evidence that describe earlier Robin-based
  runs; they are traceability, not the current completion source of truth.

## Verification

- `git diff --check`
- focused search proving active docs no longer require Robin chat confirmation;
- `mise run architecture-check`;
- `mise run ci` before completion;
- CLI, Docker, release/install, compatibility, and final gameplay evidence
  recorded in `docs/project-completion-checklist.md`.
