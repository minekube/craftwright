# Fumadocs GitHub Pages Evidence

## Scope

Phase 193 adds a static Fumadocs documentation site for Craftless, hosted by
GitHub Pages and built with Bun through mise.

The site uses the generated Craftless supervisor OpenAPI snapshot as its API
reference source. The snapshot is exported from the protocol route catalog, not
hand-written into the docs app.

## FumaPress Spike

`/tmp/fumadocs-spike` was cloned to compare Fumadocs and FumaPress. FumaPress is
packaged as `fumadocs-preview`, is Waku-based, and is optimized for fast content
preview. It is not a better fit for this PR because the product requirement is a
static GitHub Pages API documentation site generated from OpenAPI.

## Product Surface

- `docs-site/` contains a Fumadocs/Next static export app.
- `.github/workflows/docs-pages.yml` builds and deploys `docs-site/out` to
  GitHub Pages on pushes to `main` and manual dispatch.
- The Pages workflow sets `CRAFTLESS_DOCS_BASE_PATH=/craftless` so static Next
  links and assets are valid under the repository GitHub Pages path.
- `mise run docs-site-openapi` exports
  `docs-site/openapi/craftless-supervisor.json` from `:protocol`.
- `mise run docs-site-build` regenerates OpenAPI, installs with Bun, and builds
  the static site.
- Supervisor OpenAPI now includes top-level tag descriptions for core pillars so
  Fumadocs can group operation pages and agents get better API context.

## Verification

```sh
mise exec -- bun test playwright/src/distribution.test.ts --test-name-pattern "Fumadocs site is a static GitHub Pages product surface"
```

Result: passed with 1 focused test and 25 assertions.

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
CRAFTLESS_DOCS_BASE_PATH=/craftless mise exec -- bun run build
```

Result: passed from `docs-site/`. Exported HTML referenced `/craftless/_next`
assets and `/craftless/docs/...` navigation paths.

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

Result: all passed. The distribution test reported 19 tests and 229 assertions.
