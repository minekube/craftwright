# Latest Current Generated Primitive Smoke Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> superpowers:subagent-driven-development or superpowers:executing-plans to
> implement this plan task-by-task.

**Goal:** Close CL-03f by proving a public packaged API/CLI user can invoke a
generated operation selected from live per-client OpenAPI metadata.

**Architecture:** Extend the packaged latest-current product probe from Phase
182. Keep all helper scripting under `mise exec -- bun`; do not add static
gameplay commands, static operation catalogs, or scenario shortcuts.

---

## Task 1: Red Distribution Guard

**Files:**

- Modify: `playwright/src/distribution.test.ts`

- [x] Add expectations that the packaged latest-current probe script contains:
  - `client-generated-action-selected.json`;
  - `client-rpc-invoke-generated.json`;
  - `client-cli-invoke-generated.log`;
  - `x-craftless-actions`;
  - JSON-RPC `method: "invoke"`;
  - `clients "$CLIENT_ID" run "$GENERATED_ACTION_ID"`.
- [x] Run:

  ```sh
  mise exec -- bun test playwright/src/distribution.test.ts
  ```

- [x] Verify the test fails before implementation because the probe does not
  capture generated invocation artifacts.

## Task 2: Capture Generated Invocation

**Files:**

- Modify: `scripts/packaged-latest-current-probe.sh`

- [x] Add `GENERATED_ACTION_ID`.
- [x] Read live `GET /clients/{id}/openapi.json`.
- [x] Select an available no-required-argument action from
  `x-craftless-actions`.
- [x] Write the selected descriptor to
  `client-generated-action-selected.json`.
- [x] Invoke the selected operation through `POST /clients/{id}:rpc` with
  JSON-RPC `method: "invoke"` and write
  `client-rpc-invoke-generated.json`.
- [x] Run the packaged adaptive CLI path:

  ```sh
  craftless clients "$CLIENT_ID" run "$GENERATED_ACTION_ID" --api "$API"
  ```

- [x] Write `client-cli-invoke-generated.log`.
- [x] Add the selected action to `packaged-probe-summary.json`.

## Task 3: Verify

- [x] Run:

  ```sh
  mise exec -- bun test playwright/src/distribution.test.ts
  ```

- [x] Run:

  ```sh
  mise run packaged-latest-current-probe
  ```

- [x] Inspect the generated artifacts and confirm the selected operation was
  accepted through both JSON-RPC and CLI.

## Task 4: Evidence And Checklist

**Files:**

- Create:
  `docs/superpowers/evidence/2026-06-28-latest-current-generated-primitive-smoke.md`
- Modify:
  `docs/project-completion-checklist.md`
- Modify:
  `docs/superpowers/phase-index.md`

- [x] Record the red/green test evidence.
- [x] Record the packaged live probe evidence.
- [x] Mark CL-03f and CL-03 complete.
- [x] Move the Current Execution Packet to CL-04 representative older lane.
- [x] Keep CL-05, CL-06, CL-07, and CL-08 open.

## Task 5: Final Checks And Push

- [ ] Run:

  ```sh
  mise exec -- bun test playwright/src/distribution.test.ts
  git diff --check
  ```

- [ ] Commit and push directly to `main`.
