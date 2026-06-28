# Phase 75: Post-Cache-Integrity Evidence Refresh Design

## Goal

Refresh the full Codex-verifiable completion evidence after the Phase 73 and
Phase 74 cache-integrity changes.

## Context

Phase 68 proved the current distribution, compatibility, and final public
gameplay gates before the latest cache changes. Phase 73 then changed asset
object cache reuse, and Phase 74 changed checksum validation for metadata-backed
binary downloads. Those changes are aligned with multi-version launch
reliability, but they make the Phase 68 evidence stale for the active
completion gate.

This phase records evidence only. It does not add public gameplay actions,
generated route families, CLI gameplay catalogs, Fabric descriptor/binding
pairs, scenario shortcuts, new compiled lanes, public version-specific APIs, or
new Minecraft support claims.

## Requirements

- Refresh CLI packaging and packaged CLI smoke evidence from the current code.
- Refresh Docker runtime image build and smoke evidence from the current code.
- Refresh install-script smoke evidence for the published `v0.1.0` surface.
- Refresh live Mojang manifest evidence for latest release `26.2`, latest
  snapshot `26.3-snapshot-1`, and representative older release `1.20.6`.
- Refresh compatibility matrix/probe evidence and the explicit unsupported
  latest Fabric client lane smoke.
- Refresh final honest survival gameplay evidence through generated public
  OpenAPI/actions/resources and SSE, without server-provisioned inventory,
  manual Craftless movement, or scenario shortcuts.
- Refresh local and remote CI evidence after the evidence docs are committed.
- Keep the overall project goal active unless a completion audit proves every
  objective requirement.

## Non-Goals

- Do not make `26.2`, `26.3-snapshot-1`, or `1.20.6` supported Fabric client
  lanes.
- Do not add or rename public gameplay actions, route families, CLI gameplay
  catalogs, Fabric descriptor/binding pairs, or scenario shortcuts.
- Do not change product runtime behavior unless evidence reveals a real blocker
  that must be fixed in a separate spec/plan phase.

## Verification

- `mise run package-cli`
- packaged CLI `server start --once --port 0`
- `docker build -t craftless:local .`
- Docker `server start --once --port 0`
- installer smoke with `CRAFTLESS_VERSION=v0.1.0`
- live Mojang manifest probe through `mise exec -- bun`
- compatibility matrix/probe tests
- latest unsupported `fabricClientSmoke` for `26.2`
- no-confirmation `fabricFinalGameplay`
- `git diff --check`
- `mise run architecture-check`
- `mise run ci`
- GitHub Actions CI for the pushed `main` commit
