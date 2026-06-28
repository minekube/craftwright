# README And Roadmap Evidence Alignment Design

Date: 2026-06-28

## Problem

The public README and roadmap lag behind the current Phase 68 evidence. They
still emphasize older diagnostic smoke details, including provisioned item
setup, and the README still describes final public gameplay evidence as open.

## Goal

Align public-facing docs with the current verified state without changing
product behavior or overstating version support.

## Requirements

- README must explain Craftless as a generated local API for real Minecraft
  Java clients.
- README must keep quickstart, Docker, GitHub Action, API, CLI, cache/runtime,
  status, roadmap, and development sections current.
- README must state that the final no-confirmation public-agent gameplay gate
  has current evidence, including generated OpenAPI/actions/resources, SSE,
  Wooden Sword crafting/equip, Cow attack, and Raw Beef/Leather drops.
- README must still state the project is not complete while generic-discovery,
  multi-version, and completion-audit work remains open.
- Roadmap must stop presenting provisioned iron-sword smoke as the current
  product baseline.
- Roadmap must describe latest `26.2` and representative older `1.20.6`
  compatibility as manifest/runtime-resolution evidence with explicit
  unsupported Fabric client lanes, not supported client breadth.
- AGENTS.md and checklist must include this docs-only phase.
- This phase must not add public gameplay actions, generated route families,
  CLI gameplay catalogs, Fabric descriptor/binding pairs, scenario shortcuts,
  new compiled lanes, public version-specific APIs, or new Minecraft support
  claims.

## Non-Goals

- No code changes.
- No release publishing.
- No gameplay rerun beyond using already-recorded Phase 68 evidence.
- No claim that Minecraft `26.2` or `1.20.6` Fabric clients are supported.

## Verification

- `git diff --check`
- `mise run architecture-check`
- `mise run ci`
