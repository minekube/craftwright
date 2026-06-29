# Craftless Docs Site

This Fumadocs site builds a static OpenAPI reference for Cloudflare Workers
Static Assets.

Build locally from the repository root:

```sh
mise run docs-site-build
```

Deploy from `docs-site/` when authenticated with Cloudflare:

```sh
mise exec -- bun run deploy
```

Cloudflare Workers Builds should use the repository root as its build root and
`mise run docs-site-build` as the build command. Non-production branches get
Worker preview deployments when the Cloudflare project is connected to GitHub.
