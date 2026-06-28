# Phase 68: Full Codex Evidence Gate Refresh Design

## Goal

Refresh the full Codex-verifiable completion evidence after final gameplay was
changed to no longer require human Minecraft chat confirmation by default.

## Context

Phase 67 changed the default final gameplay code path so completion evidence is
produced by Craftless and the public-agent helper without a required Robin
confirmation phrase. The final gate now needs fresh evidence from the actual
current code and release surfaces, not historical Phase 60/65 artifacts.

This phase records evidence only. It does not add or change public gameplay
actions, CLI gameplay catalogs, route families, Fabric descriptor/binding
pairs, or Minecraft version support.

## Requirements

- Refresh CLI packaging and packaged CLI smoke evidence.
- Refresh Docker runtime image smoke evidence.
- Refresh install script/release smoke evidence.
- Refresh latest-release and representative older-release compatibility
  evidence from the live Mojang manifest and compatibility matrix.
- Refresh final honest survival gameplay evidence through generated public
  OpenAPI/actions/resources and SSE, with no server-provisioned inventory and
  no hard-coded survival shortcut.
- Fix active documentation that still implies the macOS ready prompt is
  injected by default.
- Keep completion open unless every gate is verified and the checklist is
  current.

## Non-Goals

- Do not add public gameplay actions, route families, CLI gameplay catalogs,
  Fabric descriptor/binding pairs, scenario shortcuts, new compiled lanes,
  public version-specific APIs, or new Minecraft support claims.
- Do not turn unsupported `26.2` or `1.20.6` lanes into supported lanes.
- Do not mark the active thread goal complete unless all objective
  requirements are proven current.

## Verification

- `mise run package-cli`
- packaged CLI `server start --once --port 0`
- `docker build -t craftless:local .`
- Docker `server start --once --port 0`
- installer smoke with `CRAFTLESS_VERSION=v0.1.0`
- live Mojang manifest probe for latest `26.2` and older `1.20.6`
- compatibility matrix/probe tests
- `fabricClientSmoke` unsupported latest-lane probe for `26.2`
- final `fabricFinalGameplay` run without confirmation phrase
- `git diff --check`
- `mise run architecture-check`
- `mise run ci`
