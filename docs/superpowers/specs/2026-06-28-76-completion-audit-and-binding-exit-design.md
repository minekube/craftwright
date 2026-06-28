# Phase 76: Completion Audit And Binding Exit Design

## Goal

Record the current completion state against the active Craftless goal and make
the remaining generated-discovery exit work explicit.

## Context

Phase 75 refreshed the current distribution, compatibility, CI, and final
public gameplay evidence. That evidence is strong enough to retire stale
completion-gate wording around Robin chat confirmation and old Phase 68
evidence, but it is not enough to mark the whole product complete.

The remaining architectural blocker is that the Fabric driver still carries a
small allowlisted set of hand-written public gameplay descriptors/bindings as
transitional bootstrap evidence. Those descriptors are represented in the
runtime graph and currently power important final gameplay evidence, but they
are still not the durable product shape. Completion requires the gameplay
surface to come from generic runtime discovery, projection, and executable
adapters, not from growing or depending on a fixed Kotlin binding list.

This phase is a governance and evidence audit. It changes no product behavior.

## Requirements

- Align the active checklist with the current goal wording: no Robin Minecraft
  chat confirmation is required for completion.
- Replace stale "Phase 68 evidence" final-gate wording with current Phase 75
  evidence plus this completion audit.
- Record which current gates are verified:
  - CLI packaging and packaged CLI smoke.
  - Docker runtime image and smoke.
  - Install script smoke.
  - Ktor-only JVM HTTP/client/SSE direction.
  - mise/Bun-only repository workflows.
  - JSON-RPC-style invocation and SSE event streaming.
  - Latest and representative older-version compatibility evidence.
  - Final honest public API/CLI gameplay evidence without provisioning
    shortcuts.
- Record which gates remain open:
  - The public gameplay action surface still depends on the transitional
    hand-written Fabric binding allowlist.
  - Latest `26.2` and older `1.20.6` lanes have current explicit unsupported
    evidence, not runtime support.
  - Broad Fabric/API affordance discovery is not complete while adding new
    gameplay breadth still means adding or extending descriptor/binding code.
- Keep the active goal open until the binding allowlist can be removed or
  reduced to private execution adapters behind generic runtime discovery.

## Non-Goals

- Do not add public gameplay actions, generated route families, CLI gameplay
  catalogs, Fabric descriptor/binding pairs, scenario shortcuts, new compiled
  lanes, public version-specific APIs, or new Minecraft support claims.
- Do not remove the transitional bindings in this phase; this phase only
  records why they still block completion.
- Do not reintroduce Robin chat confirmation as a required completion gate.

## Verification

- `git diff --check`
- `mise run architecture-check`
- `mise run ci`
- GitHub Actions CI for the pushed `main` commit
