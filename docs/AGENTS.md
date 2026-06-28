# Documentation Instructions

`docs/` owns design notes, implementation plans, evidence records, and roadmap
material.

## Rules

- Keep README and docs aligned with current architecture.
- Make clear what is implemented now versus roadmap.
- Do not document removed TypeScript SDK or other inactive legacy surfaces as
  active implementation.
- Use `minekube.com` and `com.minekube.craftless` for public domain/package
  references.
- Describe the bridge as evidence infrastructure only.
- Do not describe the bridge as a gameplay action adapter. Gameplay examples
  must use the generated Fabric runtime graph/OpenAPI path, not HMC bridge
  helpers.
- Describe the durable driver direction as Fabric with generated per-client
  OpenAPI/action descriptors, adaptive CLI dispatch/help, and consolidated
  version-aware bindings where practical.
- Describe multi-version support as version-agnostic system work first:
  manifests, Java/runtime selection, Fabric Loader/API resolution, driver mod
  manifests, compatibility lanes, and runtime graph evidence. Per-version code
  is acceptable only for documented divergence behind lane boundaries.
- Avoid stale public routes such as `/player/sendChat`; use
  `POST /clients/{id}:run` and generated aliases such as
  `POST /clients/{id}/player:chat` when discussing action invocation.

## Verification

For docs-only edits, run at least:

```sh
git diff --check
```
