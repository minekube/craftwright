# Phase 73: Asset Object Integrity Resume Design

## Goal

Make Minecraft asset-object cache preparation checksum-aware so interrupted
latest-version asset downloads can be retried without preserving corrupt local
files.

## Context

The 26.2 compatibility probe showed that `/cache:prepare` can fail while
downloading large asset sets. `CachePreparationService` already skips existing
binary artifacts, which is good for resumability, but it currently treats any
existing regular file as valid. For Minecraft asset objects the asset index
contains the SHA-1 hash that is also used as the object handle. A partial or
corrupt object file should be re-fetched rather than silently reused.

This is a multi-version foundation improvement. It does not add a Fabric
runtime lane, public gameplay actions, static CLI gameplay catalogs, or a
version-specific public API.

## Requirements

- Asset objects discovered from the Minecraft asset index must carry their
  expected SHA-1 digest into the prepared artifact metadata.
- Cache preparation must reuse an existing asset object only when the local
  file SHA-1 matches the expected digest.
- Cache preparation must re-fetch and replace an existing corrupt asset object.
- Cache preparation must validate newly fetched asset-object bytes before
  writing the final target.
- The behavior must remain idempotent for a valid existing asset object.
- Existing non-asset binary artifact reuse remains unchanged in this phase.
- The public cache manifest may include the expected SHA-1 as metadata, but
  this must not expose raw Minecraft internals as gameplay API.

## Non-Goals

- Do not add a background cache job system in this phase.
- Do not add generic checksum metadata for every artifact type unless required
  for the asset-object path.
- Do not add or claim Minecraft 26.2 Fabric client support.
- Do not add public gameplay actions, generated route families, or scenario
  shortcuts.
- Do not mark the project complete.

## Design

Extend `CachePreparedArtifact` with an optional `sha1` field. Populate it for
`MINECRAFT_ASSET_OBJECT` artifacts from the asset index hash. Update
`CachePreparationService.writeFetchedBytesArtifact(...)` so existing files with
`artifact.sha1` are verified before reuse. If verification fails, download
again into memory, validate the downloaded bytes against `artifact.sha1`, then
write the final file.

This keeps the cache API simple and compatible with existing consumers while
making the highest-volume asset path safe to resume after timeout or partial
download failures.

## Verification

- A focused daemon test fails before implementation by showing corrupt cached
  asset bytes are reused.
- The focused daemon test passes after implementation and proves valid asset
  objects are reused while corrupt ones are re-fetched.
- `mise exec -- gradle :daemon:test --tests '*CachePreparationServiceTest*'`
  passes.
- `git diff --check`, `mise run architecture-check`, and `mise run ci` pass.
