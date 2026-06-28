# Craftless Project Completion Checklist

This is the active completion board. It is not a phase archive, design doc, or
dumping ground for every task ever attempted.

Craftless is complete only when every CL gate below is `[x]`, the named
evidence files are fresh, local verification passed, the worktree is clean, and
`main` is pushed.

Status legend: `[ ]` open, `[~]` in progress, `[x]` closed with evidence, `[!]`
blocked with an exact blocker and next command.

## Operating Rules

- Work top-down from **Current Execution Packet**.
- Put specs in `docs/superpowers/specs/`.
- Put implementation plans in `docs/superpowers/plans/`.
- Put command transcripts and artifact summaries in
  `docs/superpowers/evidence/`.
- Put phase index entries in `docs/superpowers/phase-index.md`.
- Put durable rules in `docs/agent-operating-contract.md` or
  `docs/agent-module-contracts.md`.
- Do not append phase history or raw logs to this file.
- Do not close a gate from compile-only output, old evidence, remote CI
  waiting, hand-maintained gameplay catalogs, or scenario shortcuts.

## At A Glance

| Field | Current State |
| --- | --- |
| Active gate | CL-07 final honest public gameplay |
| Exact next work | Finish the rerunnable CL-07 public gameplay probe and run it without server provisioning |
| Do not do yet | Do not claim CL-08 or final project completion |
| Current blocker | `scripts/final-public-gameplay-probe.sh`, `tasks.final-public-gameplay-probe`, and CL-07 evidence do not exist yet |
| Completion rule | Close one gate only when its evidence file contains fresh commands and results |

## Gate Board

| Gate | Status | Closure Standard | Evidence |
| --- | --- | --- | --- |
| CL-01 | [x] | Public gameplay authority is generated runtime graph/OpenAPI, not `/actions` or static lists. | Phases 171-173. |
| CL-02 | [x] | Static gameplay catalog regressions are guarded; transitional Fabric bootstrap cannot become public API authority. | Phase 178. |
| CL-03 | [x] | Latest/current Minecraft `26.2` packaged lane completes create/attach/connect/OpenAPI/projections/SSE/JSON-RPC/adaptive CLI invocation. | `docs/superpowers/evidence/2026-06-28-latest-current-generated-primitive-smoke.md` |
| CL-04 | [x] | Representative older Minecraft `1.20.6` packaged lane completes the same public product gate set as CL-03. | `docs/superpowers/evidence/2026-06-28-representative-older-product-lane.md` |
| CL-05 | [x] | External users and agents can install, run, inspect, stream, invoke, and debug Craftless without reading source. | `docs/superpowers/evidence/2026-06-28-user-facing-usability-docs.md` |
| CL-06 | [x] | Local release-quality gates pass after CL-05 is closed. | `docs/superpowers/evidence/2026-06-28-final-local-release-gates.md` |
| CL-07 | [~] | Honest survival gameplay succeeds through public generated API/CLI only. | `docs/superpowers/evidence/2026-06-28-final-public-gameplay.md` |
| CL-08 | [ ] | Final state is clean, committed, pushed to `main`, and indexed. | `docs/superpowers/evidence/2026-06-28-final-completion.md` |

## Current Execution Packet: CL-07

Only this packet is active. Treat checked items here as local progress, not as
gate closure. CL-07 closes only when its evidence file exists and the Gate
Board row is changed to `[x]`.

| Step | Status | Required Output | Next Command |
| --- | --- | --- | --- |
| 1 | [x] | CL-07 spec and implementation plan exist. | Already done: `docs/superpowers/specs/2026-06-28-187-final-public-gameplay-design.md`, `docs/superpowers/plans/2026-06-28-187-final-public-gameplay-plan.md`. |
| 2 | [~] | Distribution guard proves the final probe uses generated public surfaces only and rejects scenario shortcuts. | `mise exec -- bun test playwright/src/distribution.test.ts` must fail red until the script and mise task exist, then pass green. |
| 3 | [ ] | Real-client smoke provisioning can be disabled and is disabled for CL-07. | Add/verify `CRAFTLESS_DISABLE_SMOKE_PROVISIONING=1` and guard against default `CRAFTLESS_SMOKE_PROVISION_ITEM_ID`. |
| 4 | [ ] | `scripts/final-public-gameplay-probe.sh` starts packaged Craftless, uses generated OpenAPI as authority, and writes artifacts. | Create script through `apply_patch`, then `chmod +x scripts/final-public-gameplay-probe.sh`. |
| 5 | [ ] | `.mise.toml` has `tasks.final-public-gameplay-probe` that packages the CLI and runs the real Fabric smoke with provisioning disabled. | Add the task and keep it `mise`/Bun/Gradle only. |
| 6 | [ ] | Guard tests pass for the distribution surface and provisioning boundary. | Run focused Bun/Gradle tests, then `git diff --check`. |
| 7 | [ ] | Final gameplay probe runs through public generated API/CLI only. | `mise run final-public-gameplay-probe`. |
| 8 | [ ] | Probe captures connected OpenAPI, actions/resources, SSE or JSON-RPC subscription, action log, state log, and server log. | Inspect `driver-fabric/build/craftless-final-gameplay/artifacts/`. |
| 9 | [ ] | Gameplay proof covers chat, observation, resource collection, inventory change, craft/equip, block change, and entity interaction. | If it fails, record `missing-generic-primitive:<id>` and fix the generic discovery/projection/invocation system, not a scenario shortcut. |
| 10 | [ ] | CL-07 evidence file summarizes commands, artifacts, and verified state transitions. | Write `docs/superpowers/evidence/2026-06-28-final-public-gameplay.md`. |
| 11 | [ ] | Checklist and phase index mark CL-07 closed only after evidence passes. | Update this file and `docs/superpowers/phase-index.md`. |
| 12 | [ ] | CL-07 work is committed and pushed to `main`. | `git status --short --branch`, `git add ...`, `git commit ...`, `git push origin main`. |

### Exact Next Commands

Verify the currently red CL-07 guard:

```sh
mise exec -- bun test playwright/src/distribution.test.ts
```

Implement and verify the probe surface:

```sh
mise run package-cli
mise exec -- bun test playwright/src/distribution.test.ts
git diff --check
```

CL-07 final verification:

```sh
mise run final-public-gameplay-probe
git diff --check
```

### CL-07 Failure Rules

- If a required primitive is not discovered from live per-client OpenAPI, write
  `missing-generic-primitive:<id>` and fix generic discovery/projection or
  invocation.
- If inventory does not change after a world action, improve public state,
  pickup/drop perception, or invocation evidence. Do not preload inventory.
- If movement or targeting is unreliable, improve generated navigation,
  raycast, query handles, or adapter behavior. Do not script a fixed survival
  route.
- If a Minecraft version or lane diverges, isolate the diverging adapter or
  provider behind the version lane. Do not create version-specific public API,
  CLI command trees, or copied gameplay catalogs.
- If the probe only proves accepted invocation status without observed public
  state changes, CL-07 remains open.

## Gate Acceptance Contracts

### CL-05: External User And Agent Usability

- README covers install script, packaged CLI, Docker runtime image, reusable
  GitHub Action, supervisor OpenAPI, generated per-client OpenAPI, adaptive
  CLI, SSE, JSON-RPC query/subscription, cache behavior, and evidence
  expectations.
- README/docs have no active TypeScript SDK positioning, previous brand name,
  `.dev` domain, HMC-as-final-driver wording, static gameplay SDK wording, or
  server-cheat completion wording.
- CLI gameplay examples stay adaptive and OpenAPI-derived.
- Docker smoke proves the image runs the copied packaged Craftless artifact.
- Install smoke proves a fresh user can run the packaged CLI.
- Agent skill docs teach generated OpenAPI/SSE/JSON-RPC composition,
  missing-primitive reporting, and no scenario shortcuts.

### CL-06: Final Local Release Gates

Run and record all of:

```sh
mise run lint
mise run architecture-check
mise run ci
mise run package-cli
git diff --check
```

Also record fresh Docker smoke, install smoke, latest/current packaged lane
probe, and representative older packaged lane probe.

### CL-07: Final Honest Public Gameplay

The final gameplay replay must use public generated API/CLI only. It must not
use creative inventory, `/give`, preloaded inventory, human movement,
hard-coded survival scenario actions, or direct in-process test calls.

Required proof:

- Create or attach a real Craftless-controlled client.
- Fetch generated per-client OpenAPI.
- Capture actions/resources projections.
- Subscribe to SSE or JSON-RPC subscription stream.
- Write chat.
- Observe player/world/entity and inventory state.
- Collect a resource and prove the result through public state.
- Craft and equip an item and prove inventory/selected-slot state changed.
- Mine or place a block and prove world state changed.
- Interact with or attack an entity and prove public entity/action state.
- Pick up or drop an item and prove inventory or item-entity state changed.
- Record server log.
- Write final artifacts under
  `driver-fabric/build/craftless-final-gameplay/artifacts/`.

Required negative proof:

- No `/give`, creative inventory, preloaded inventory, direct driver calls,
  human movement, server provisioning, `task.*`, `task.survival`, `kill.cow`,
  `find.tree`, `craft.sword`, or other scenario shortcut can appear in the
  final probe path.
- `/clients/{id}/actions` and `/clients/{id}/resources` are projection
  artifacts only. The authority for gameplay selection is
  `GET /clients/{id}/openapi.json`.

### CL-08: Publish Completed State

- Final evidence names every closed CL gate and command.
- Checklist, phase index, evidence, README/docs, and code are committed.
- `git status --short --branch` is clean after commit.
- `git push origin main` succeeds.

## Final Completion Gate

Completion remains blocked until CL-01 through CL-08 are checked with fresh
evidence. The final record must include runnable support evidence for both the
latest/current lane and the representative older lane under the same public
API/CLI gates.

Historical phase sections do not close the product goal. They are indexed in
`docs/superpowers/phase-index.md` and backed by specs, plans, and evidence
files.

## Current Baseline

Craftless currently has a Kotlin/JVM Ktor supervisor, adaptive JVM CLI,
generated per-client OpenAPI, graph-projected actions/resources, generic
invocation, SSE plus JSON-RPC-style query/control, packaged distribution paths,
and staged Fabric gameplay evidence.

CL-03 is closed for latest/current Minecraft `26.2`. CL-04 is closed for
representative older Minecraft `1.20.6`. CL-05 is closed for external-user
and agent usability. CL-06 is closed for local release-quality gates. CL-07 is
the active blocker: implement and run a rerunnable final honest survival
gameplay probe through public generated API/CLI only, with server provisioning
disabled.

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
