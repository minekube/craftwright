# Craftless Project Completion Checklist

This is the active completion board. It is not a roadmap essay or command log.
The next agent should be able to start from the first open row below.

Completion means every CL gate is `[x]`, the cited evidence is current, local
verification passed from the current worktree, `main` is pushed, and
`git status --short --branch` prints only `## main...origin/main`.

Status legend:

- `[ ]` open
- `[~]` in progress with partial evidence
- `[x]` closed with evidence
- `[!]` blocked; include the exact failing command and next diagnostic

## Non-Negotiables

- Work on the system: discovery, projection, invocation, streaming, packaging,
  version/runtime resolution, CLI, docs, and verification.
- Do not add static gameplay catalogs, static CLI gameplay trees, scenario
  actions, preloaded inventory, `/give`, creative-mode shortcuts, or direct
  driver calls to pass a gate.
- Use generated per-client OpenAPI as the gameplay authority. `/actions` and
  `/resources` are projections only.
- When a primitive is missing, record `missing-generic-primitive:<id>` and fix
  the generic discovery/projection/invocation/runtime path.
- Keep root and module `AGENTS.md` files short. Put growing rules in
  `docs/agent-operating-contract.md` or `docs/agent-module-contracts.md`.
- Put specs in `docs/superpowers/specs/`, plans in
  `docs/superpowers/plans/`, evidence in `docs/superpowers/evidence/`, and
  phase history in `docs/superpowers/phase-index.md`.
- Closed means a command, artifact, or evidence file proves behavior from a
  product surface. Intent, code shape, or a local partial run is not enough.

## Current Truth

| Field | State |
| --- | --- |
| Active gate | Post-completion Fabric version-matrix support proof |
| Current state | All original CL gates are closed. Phases 198-206 add the support matrix, supported-row probe workflow, runtime-target create rejection, game-scoped Fabric Loader compatibility, and structured `UNSUPPORTED_RUNTIME_TARGET` details. Phases 207-208 improve ambiguous `craftless api` help, unobserved-connect diagnostics, default daemon workspaces, and stale cache CLI docs after session `019f121c` exposed those agent friction points. Release `v0.3.0` is published. |
| Latest product proof | `mise run final-public-gameplay-probe` passed for Minecraft `1.21.6` with server provisioning disabled. Fabric matrix evidence now includes `/versions/support-targets`, packaged supported-row probing, and machine-readable unsupported runtime rejection evidence. |
| Latest CI truth | `mise run docs-site-verify` passed on the current docs refresh; GitHub docs-site run `28555713663` passed verification but skipped deploy because `CLOUDFLARE_API_TOKEN` is absent; GitHub release-please run `28555365427` completed successfully for `v0.3.0`. |
| Current blocker | Automatic docs deployment is not enabled until the GitHub repository gets `CLOUDFLARE_API_TOKEN`; the live Cloudflare site still returns HTTP 200 from the previous deployment. |
| Next commands | Add `CLOUDFLARE_API_TOKEN` to enable automatic docs deploys, then continue the full Fabric matrix proof goal: every discoverable Fabric runtime row must either be supported and probe-verified through public Craftless surfaces or rejected with a structured actionable reason. |

## Next Work

| Step | Status | Done When | Evidence Or Command |
| --- | --- | --- | --- |
| 1. Original CL completion gates | [x] | CL-01 through CL-08 remain closed with evidence. | Evidence index below. |
| 2. Release current matrix work | [x] | The release PR for post-completion Fabric matrix work is merged and the GitHub release exists. | `v0.3.0` published at `https://github.com/minekube/craftless/releases/tag/v0.3.0`; tag/main commit `98d94fbb504e598196b8b09f395f3fd7250fd26f`. |
| 3. Fabric support matrix visibility | [x] | The supervisor exposes discovered Fabric game/loader/runtime support rows and explicit unsupported reasons. | Phases 198, 200, 201, 203, and 205 evidence below. |
| 4. Unsupported runtime rejection clarity | [x] | Unsupported create-client requests fail before launch with a public code and structured actionable details. | Phases 202, 204, and 206 evidence below. |
| 5. Docs and hosted OpenAPI snapshot freshness | [x] | README, roadmap, active checklist, and docs-site OpenAPI snapshot reflect release `v0.3.0`, `/versions/support-targets`, and structured unsupported runtime errors. | `mise run docs-site-verify` passed; `docs-site/openapi/craftless-supervisor.json` regenerated; GitHub docs-site run `28555713663` passed verification and skipped deploy because `CLOUDFLARE_API_TOKEN` is absent. |
| 6. Full Fabric matrix proof | [~] | Every discoverable Fabric runtime row is either supported and probe-verified through public Craftless surfaces or rejected with a structured actionable reason. | Supported-row probe exists from Phase 199; full breadth remains the active goal. |

## Completion Gates

| Gate | Status | Closure Standard | Evidence |
| --- | --- | --- | --- |
| CL-01 Generated authority | [x] | Public gameplay authority is generated runtime graph/OpenAPI, not static lists. | Phases 171-173; evidence index below. |
| CL-02 Static shortcut guards | [x] | Static gameplay catalog regressions are guarded; transitional Fabric bootstrap cannot become public API authority. | Phase 178; evidence index below. |
| CL-03 Latest/current lane | [x] | Minecraft `26.2` packaged lane completes create, attach, connect, generated OpenAPI, projections, SSE, JSON-RPC, and `craftless api` invocation. | `docs/superpowers/evidence/2026-06-28-latest-current-generated-primitive-smoke.md` |
| CL-04 Representative older lane | [x] | Minecraft `1.20.6` packaged lane completes the same public product gate set as CL-03. | `docs/superpowers/evidence/2026-06-28-representative-older-product-lane.md` |
| CL-05 External usability | [x] | External users and agents can install, run, inspect, stream, invoke, and debug Craftless without reading source. | `docs/superpowers/evidence/2026-06-28-user-facing-usability-docs.md` |
| CL-06 Release-quality local gates | [x] | Local release-quality gates passed after CL-05 closed. | `docs/superpowers/evidence/2026-06-28-final-local-release-gates.md` |
| CL-07 Final public gameplay | [x] | Honest survival gameplay succeeds through public generated API/CLI only, with server provisioning disabled. | `docs/superpowers/evidence/2026-06-28-final-public-gameplay.md` |
| CL-08 Publish completed state | [x] | Final current worktree passes local CI, is clean, committed, pushed to `main`, and indexed. | `docs/superpowers/evidence/2026-06-28-final-completion.md`; final correction commit `2eb73033`. |

## Final Completion Gate

Completion requires runnable support evidence for the latest/current lane, a
representative older lane, and the final public gameplay lane. Diagnostic
compatibility probes may identify future work, but they are not support
evidence.

The final state must prove all of the following from the current worktree:

- `mise run ci` passes.
- `git diff --check` passes.
- `main` contains the final checklist/evidence/code corrections.
- `git status --short --branch` prints only `## main...origin/main`.

## CL-07 Acceptance Contract

The final replay must use public generated API/CLI only. It must not use
creative inventory, `/give`, preloaded inventory, human movement, hard-coded
survival scenario actions, or direct in-process test calls.

Required positive proof:

- Create or attach a real Craftless-controlled client.
- Fetch generated per-client OpenAPI and use it as authority.
- Capture action/resource projections.
- Capture SSE or JSON-RPC subscription artifacts.
- Send chat.
- Observe player, world, entity, and inventory state.
- Collect a resource and prove the inventory change.
- Craft and equip an item and prove inventory/selected-slot state changed.
- Mine or place a block and prove world state changed.
- Interact with or attack an entity and prove public state or server log.
- Record server log.
- Write final artifacts under
  `driver-fabric/build/craftless-final-gameplay/artifacts/`.

Required negative proof:

- No `/give`, creative inventory, preloaded inventory, direct driver calls,
  human movement, server provisioning, `task.*`, `task.survival`, `kill.cow`,
  `find.tree`, `craft.sword`, or other scenario shortcut appears in the final
  probe path.
- `/clients/{id}/actions` and `/clients/{id}/resources` remain projections.
  The authority for gameplay selection is `GET /clients/{id}/openapi.json`.

## Closed Evidence Index

- CL-01:
  `docs/superpowers/evidence/2026-06-28-daemon-openapi-graph-only-authority.md`,
  `docs/superpowers/evidence/2026-06-28-remote-driver-action-graph-authority.md`,
  `docs/superpowers/evidence/2026-06-28-public-agent-actions-projection-optional.md`.
- CL-02:
  `docs/superpowers/evidence/2026-06-28-fabric-execution-adapter-naming.md`,
  `docs/superpowers/evidence/2026-06-28-bootstrap-resource-derivation.md`,
  `docs/superpowers/evidence/2026-06-28-bootstrap-adapter-key-separation.md`,
  `docs/superpowers/evidence/2026-06-28-client-state-operation-discovery.md`,
  `docs/superpowers/evidence/2026-06-28-static-gameplay-guard-closure.md`.
- Post-completion CLI architecture:
  `docs/superpowers/evidence/2026-06-29-generated-route-cli-daemon-naming.md`.
- CL-03:
  `docs/superpowers/evidence/2026-06-28-packaged-official-latest-lane.md`,
  `docs/superpowers/evidence/2026-06-28-official-client-state-world-time-operation.md`,
  `docs/superpowers/evidence/2026-06-28-official-world-time-invocation.md`,
  `docs/superpowers/evidence/2026-06-28-packaged-latest-current-attach-artifacts.md`,
  `docs/superpowers/evidence/2026-06-28-latest-current-generated-primitive-smoke.md`.
- CL-04:
  `docs/superpowers/evidence/2026-06-28-representative-older-product-lane.md`.
- CL-05:
  `docs/superpowers/evidence/2026-06-28-user-facing-usability-docs.md`.
- CL-06:
  `docs/superpowers/evidence/2026-06-28-final-local-release-gates.md`.
- CL-07:
  `docs/superpowers/evidence/2026-06-28-final-public-gameplay.md`.
- CL-08:
  `docs/superpowers/evidence/2026-06-28-final-completion.md`.
- Phase 188:
  `docs/superpowers/evidence/2026-06-29-tiny-agent-lifecycle-defaults.md`.
- Phase 189:
  `docs/superpowers/evidence/2026-06-29-generated-route-cli-daemon-naming.md`.
- Phase 190:
  Release Please automation is indexed in `docs/superpowers/phase-index.md`.
- Phase 191:
  `docs/superpowers/evidence/2026-06-29-client-lifecycle-surface-clarity.md`.
- Phase 192:
  `docs/superpowers/evidence/2026-06-29-core-openapi-descriptions.md`.
- Phase 193:
  `docs/superpowers/evidence/2026-06-29-api-only-cli.md`.
- Phase 194:
  `docs/superpowers/evidence/2026-06-29-fumadocs-github-pages.md`.
- Phase 195:
  `docs/superpowers/evidence/2026-06-29-screenshot-api.md`.
- Phase 196:
  `docs/superpowers/evidence/2026-06-29-windowless-muted-defaults.md`.
- Phase 197:
  `docs/superpowers/evidence/2026-07-01-headless-presentation-truth.md`.
- Phase 198:
  `docs/superpowers/evidence/2026-07-01-fabric-support-targets.md`.
- Phase 199:
  `docs/superpowers/evidence/2026-07-01-fabric-supported-matrix-proof.md`.
- Phase 200:
  `docs/superpowers/evidence/2026-07-01-fabric-runtime-target-support.md`.
- Phase 201:
  `docs/superpowers/evidence/2026-07-01-fabric-loader-runtime-matrix.md`.
- Phase 202:
  `docs/superpowers/evidence/2026-07-02-runtime-target-create-rejection.md`.
- Phase 203:
  `docs/superpowers/evidence/2026-07-02-wildcard-fabric-loader-matrix.md`.
- Phase 204:
  `docs/superpowers/evidence/2026-07-02-fabric-loader-request-rejection.md`.
- Phase 205:
  `docs/superpowers/evidence/2026-07-02-game-scoped-fabric-loader-matrix.md`.
- Phase 206:
  `docs/superpowers/evidence/2026-07-02-structured-runtime-target-error.md`.
- Phase 207:
  `docs/superpowers/evidence/2026-07-02-session-019f121c-cli-help.md`.
