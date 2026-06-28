# Create Client Loader Version Design

## Problem

`CachePrepareRequest` already supports a pinned Fabric Loader version, and the
packaged driver-mod manifest is keyed by Minecraft version and Loader version.
`CreateClientRequest` does not expose `loaderVersion`, so API users creating a
client cannot intentionally select the loader lane that matches an installed
driver mod. The daemon therefore chooses whichever compatible loader the cache
resolver selects and only then asks the driver-mod provider for that resolved
lane.

This blocks clean multi-version/runtime-lane work because lane selection is not
fully expressible at the supervisor API boundary.

## Goals

- Add optional `loaderVersion` to `CreateClientRequest`.
- Validate `loaderVersion` with the same file-safe segment rules as cache
  preparation.
- Pass `CreateClientRequest.loaderVersion` into `CachePrepareRequest` during
  runtime preparation.
- Include `loaderVersion` in the supervisor OpenAPI create-client request
  schema.
- Keep alias resolution behavior: `version=latest-release` still resolves to a
  concrete Minecraft version before the driver-mod provider request.

## Non-Goals

- Do not add new compiled Fabric lanes.
- Do not mark latest/current or older versions as newly supported.
- Do not add gameplay actions, route families, CLI gameplay catalogs, Fabric
  gameplay bindings, scenario shortcuts, or public version-specific APIs.
- Do not relax the strict driver-mod manifest match.

## Acceptance Criteria

- A focused daemon test fails before implementation when a client creation
  request with `loaderVersion` still resolves the default loader.
- After implementation, client creation passes the requested loader version to
  cache preparation and to the driver-mod provider through the prepared lane.
- Protocol tests verify `CreateClientRequest.loaderVersion` validation.
- OpenAPI tests verify the create-client request schema exposes nullable
  `loaderVersion`.
- `mise exec -- gradle :protocol:test --tests '*ClientModelsTest.*' --tests '*OpenApiGenerationTest.*'`
  passes.
- `mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.*loader version*'`
  passes.
- `git diff --check` and `mise run ci` pass locally.
