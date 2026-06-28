# Version Support Completion Gate Design

## Problem

The active goal requires latest/current-version and representative older-version
support to land and be verified. The current final completion gate still says
latest and representative older runtime lanes may have "the requested support
or an explicitly accepted support boundary." That wording made sense when
recording unsupported lane evidence, but it is too weak for the active goal.

Unsupported lanes such as latest release `26.2` and representative older
release `1.20.6` are useful diagnostics. They must not count as final
completion evidence.

## Goals

- Make the final completion gate require runnable support evidence for latest
  and representative older runtime lanes.
- Keep unsupported latest/older lane records as diagnostic evidence only.
- Add a source guard that fails if the checklist final gate accepts an
  unsupported support boundary as completion.
- Avoid claiming new version support in this phase.

## Non-Goals

- Do not add a new compiled Fabric/Loom runtime lane in this phase.
- Do not mark latest `26.2` or older `1.20.6` as supported.
- Do not add gameplay actions, route families, CLI commands, generated aliases,
  Fabric execution adapters, version lanes, or support claims.
- Do not mark the project complete.

## Acceptance Criteria

- `docs/project-completion-checklist.md` final gate no longer accepts an
  "explicitly accepted support boundary" for latest/older runtime lane support.
- The final gate requires runnable support evidence for latest and
  representative older runtime lanes.
- Historical unsupported-lane phases remain diagnostic and do not claim support.
- AGENTS, checklist, plan, and evidence record Phase 91 and keep the broader
  multi-version implementation blocker active.
