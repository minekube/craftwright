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
| Active gate | All CL gates closed |
| Current state | All CL gates are closed. Phase 190 scheduled Release Please automation is implemented and verified. |
| Latest product proof | `mise run final-public-gameplay-probe` passed for Minecraft `1.21.6` with server provisioning disabled. Artifacts are under `driver-fabric/build/craftless-final-gameplay/artifacts/`. |
| Latest CI truth | `mise run ci` passed after Phase 190 scheduled Release Please automation. |
| Current blocker | None known. |
| Next commands | Commit Phase 190 and push `main`, then confirm a clean branch state. |

## Next Work

| Step | Status | Done When | Evidence Or Command |
| --- | --- | --- | --- |
| 1. Full local CI from corrected worktree | [x] | `mise run ci` exits `0` after the final checklist and ktlint corrections. | `mise run ci` passed. |
| 2. Whitespace guard | [x] | Patch has no whitespace errors. | `git diff --check` passed. |
| 3. Publish final corrections | [x] | Final CI/checklist fixes are committed and pushed to `main`. | `2eb73033 chore: close final checklist corrections`; `git push origin main` |
| 4. Clean pushed tree | [x] | Local branch is not ahead and no files are dirty. | `git status --short --branch` printed `## main...origin/main` after pushing `2eb73033`. |
| 5. Goal closure | [x] | All repo-side gates are ready for the active goal to be marked complete after final audit status. | Final audit status command required before `update_goal(status=complete)`. |

## Completion Gates

| Gate | Status | Closure Standard | Evidence |
| --- | --- | --- | --- |
| CL-01 Generated authority | [x] | Public gameplay authority is generated runtime graph/OpenAPI, not static lists. | Phases 171-173; evidence index below. |
| CL-02 Static shortcut guards | [x] | Static gameplay catalog regressions are guarded; transitional Fabric bootstrap cannot become public API authority. | Phase 178; evidence index below. |
| CL-03 Latest/current lane | [x] | Minecraft `26.2` packaged lane completes create, attach, connect, generated OpenAPI, projections, SSE, JSON-RPC, and adaptive CLI invocation. | `docs/superpowers/evidence/2026-06-28-latest-current-generated-primitive-smoke.md` |
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
  `docs/superpowers/evidence/2026-06-29-fumadocs-github-pages.md`.
