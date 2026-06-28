# Documentation Instructions

`docs/` owns design notes, implementation plans, evidence records, and roadmap
material.

## Rules

- Keep root `AGENTS.md` short and stable. Durable detailed guardrails belong in
  `docs/agent-operating-contract.md`; active status belongs in
  `docs/project-completion-checklist.md`; phase history belongs in
  `docs/superpowers/phase-index.md`.
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
- When documenting latest/current or older-version progress, say whether the
  evidence proves preflight, compile, launch, attach, generated OpenAPI,
  generated actions/resources, SSE, packaged distribution, or gameplay. Do not
  collapse those gates into a vague support claim.
- Avoid stale public routes such as `/player/sendChat`; use
  `POST /clients/{id}:run` and generated aliases such as
  `POST /clients/{id}/player:chat` when discussing action invocation.
- Keep the active completion condition centered on Codex-verifiable evidence:
  generated runtime graph/OpenAPI/actions/resources, SSE/JSON-RPC streams,
  adaptive CLI/API use, multi-version launch/attach support, and honest
  gameplay through public surfaces. Human Minecraft co-play or chat
  confirmation may be documented only as optional diagnostic evidence.
- When adding a new phase, update `docs/superpowers/phase-index.md` and
  `docs/project-completion-checklist.md`; update this docs guardrail only if
  the durable completion story changes. Do not append phase history to root
  `AGENTS.md` or grow any module-local `AGENTS.md` with per-phase status. Do
  not leave future agents to infer whether a phase was foundation work,
  diagnostic evidence, or completion evidence.

## Verification

For docs-only edits, run at least:

```sh
git diff --check
```
