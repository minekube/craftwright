# Fumadocs Cloudflare Workers Evidence

## Scope

Phase 193 adds a static Fumadocs documentation site for Craftless, hosted by
Cloudflare Workers Static Assets and built with Bun through mise.

The site uses the generated Craftless supervisor OpenAPI snapshot as its API
reference source. The snapshot is exported from the protocol route catalog, not
hand-written into the docs app.

## FumaPress Spike

`/tmp/fumadocs-spike` was cloned to compare Fumadocs and FumaPress. FumaPress is
packaged as `fumadocs-preview`, is Waku-based, and is optimized for fast content
preview. It is not a better fit for this PR because the product requirement is a
static hosted API documentation site generated from OpenAPI.

## Product Surface

- `docs-site/` contains a Fumadocs/Next static export app.
- `docs-site/wrangler.jsonc` deploys `docs-site/out` with Cloudflare Workers
  Static Assets.
- The Worker config enables `preview_urls`, so Cloudflare Workers Builds can
  produce branch and pull-request preview deployments when connected to GitHub.
- `docs-site/package.json` exposes `wrangler deploy` and
  `wrangler versions upload --preview-alias preview` scripts for authenticated
  deploy and preview uploads.
- `mise run docs-site-openapi` exports
  `docs-site/openapi/craftless-supervisor.json` from `:protocol`.
- `mise run docs-site-build` regenerates OpenAPI, installs with Bun, and builds
  the static site.
- Supervisor OpenAPI now includes top-level tag descriptions for core pillars so
  Fumadocs can group operation pages and agents get better API context.
- OpenAPI serialization now omits JSON nulls and default empty sections while
  preserving required OpenAPI root fields. The supervisor snapshot is 96,654
  bytes after minimization, down from roughly 214 KB before this change.

## Verification

```sh
mise exec -- bun test playwright/src/distribution.test.ts --test-name-pattern "Fumadocs site is a Cloudflare Workers product surface with previews"
```

Result: passed with 1 focused test and 29 assertions.

```sh
cd docs-site && mise exec -- bun run build
```

Result: passed. Next generated `/docs/api-reference` plus 22 generated
operation pages under `/docs/api-reference/routes/...`.

```sh
mise run docs-site-build
```

Result: passed. The task regenerated OpenAPI, verified the frozen Bun lockfile,
and exported the static site to `docs-site/out`.

```sh
mise exec -- bun run wrangler deploy --dry-run
```

Result: passed from `docs-site/` with Wrangler `4.105.0`. Wrangler read the
static assets from `docs-site/out` and exited before upload because of
`--dry-run`.

```sh
mise exec -- gradle :protocol:test --tests com.minekube.craftless.protocol.OpenApiGenerationTest.openapi\ json\ omits\ nulls\ and\ default\ empty\ sections
```

Result: passed. The generated `docs-site/openapi/craftless-supervisor.json`
contains no serialized `: null` fields and omits root-level empty action,
handle, and event arrays.

```sh
mise exec -- gradle :protocol:test
```

Result: passed after the repository namespace scanner was updated to allow the
new docs app while ignoring generated dependency and build directories.

```sh
mise exec -- gradle :protocol:ktlintCheck
mise exec -- bun test playwright/src/distribution.test.ts
git diff --check
```

Result: all passed. The distribution test reported 19 tests and 233 assertions.
