# Strict Fabric API Artifact Resolution Design

## Problem

Fabric cache preparation currently resolves Fabric Loader metadata strictly but
wraps Fabric API Maven metadata resolution in `runCatching { ... }.getOrNull()`.
When Maven metadata has no Fabric API artifact for the resolved Minecraft
version, Craftless silently prepares a Fabric launch plan without Fabric API.

That hides a real multi-version compatibility blocker. For latest/current and
older-version support, missing Fabric API metadata must be visible evidence so
users and agents know the requested runtime lane is not runnable yet.

## Goals

- Make Fabric cache preparation require a matching Fabric API artifact for
  Fabric loader requests.
- Keep Fabric API selection data-driven from Maven metadata.
- Fail with a message that names the resolved Minecraft version and Fabric API.
- Preserve successful Fabric API resolution for current supported metadata.

## Non-Goals

- Do not add a new compiled Fabric lane.
- Do not change Fabric Loom, Fabric Loader, Fabric API, or Minecraft dependency
  versions.
- Do not claim latest/current or older-version support is complete.
- Do not add public gameplay APIs, generated route families, CLI gameplay
  catalogs, Fabric descriptor/binding pairs, or scenario shortcuts.

## Acceptance Criteria

- A red daemon test fails before implementation because a Fabric request with
  loader metadata but no matching Fabric API artifact succeeds.
- After implementation, that request fails with a clear message containing
  `Fabric API` and the requested/resolved Minecraft version.
- Existing successful Fabric API resolution continues to include a
  `FABRIC_MOD` artifact and launch mod handle.
- Focused daemon tests, `git diff --check`, and `mise run ci` pass locally.
