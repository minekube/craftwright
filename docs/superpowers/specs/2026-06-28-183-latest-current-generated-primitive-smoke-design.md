# Latest Current Generated Primitive Smoke Design

## Problem

Phase 182 proved that the packaged `latest-release` lane can create, attach,
connect, and capture generated OpenAPI, projection, SSE, and JSON-RPC
artifacts. CL-03 still remained open because no public packaged API or adaptive
CLI invocation had executed one generated operation from that live OpenAPI.

Attach evidence alone is not gameplay/API evidence. The product must prove that
an external user can discover an operation from the generated per-client
OpenAPI metadata and invoke it through public surfaces without a static CLI
gameplay catalog or scenario shortcut.

## Goal

Extend the packaged latest-current probe so it:

- waits for an available no-argument generated operation in
  `x-craftless-actions`;
- records the selected generated action;
- invokes it through public JSON-RPC `method=invoke`;
- invokes the same action through `craftless clients <id> run <action>`;
- records both transcripts as CL-03f evidence.

## Non-Goals

- Do not add a static gameplay action.
- Do not hard-code a survival scenario or `task.*` shortcut.
- Do not bypass generated OpenAPI metadata by directly calling driver internals.
- Do not mark CL-04, CL-05, CL-06, or CL-07 complete.

## Design

Use the existing `scripts/packaged-latest-current-probe.sh` product probe. After
the connected OpenAPI/projection artifacts are captured, run a Bun helper
through `mise exec -- bun` that repeatedly fetches
`GET /clients/{id}/openapi.json`, reads `x-craftless-actions`, and selects the
first action with:

- `availability == "available"`;
- no required arguments.

The selector writes `client-generated-action-selected.json` and prints the
operation id. The script then:

1. calls `POST /clients/{id}:rpc` with JSON-RPC `method: "invoke"`,
   `params.action` set to the selected generated action, and empty `args`;
2. writes the raw response to `client-rpc-invoke-generated.json`;
3. fails if the response does not return the selected action with
   `status == "ACCEPTED"`;
4. runs `craftless clients "$CLIENT_ID" run "$GENERATED_ACTION_ID" --api "$API"`;
5. writes the CLI transcript to `client-cli-invoke-generated.log`.

The distribution guard requires those artifact names and the generated metadata
source so the probe cannot regress into static command or scenario behavior.

## Acceptance

- The distribution guard fails before the script captures generated invocation
  artifacts.
- `mise exec -- bun test playwright/src/distribution.test.ts` passes after the
  script update.
- `mise run packaged-latest-current-probe` writes:
  - `client-generated-action-selected.json`;
  - `client-rpc-invoke-generated.json`;
  - `client-cli-invoke-generated.log`.
- The selected action comes from `x-craftless-actions`.
- The JSON-RPC and CLI transcripts show the selected action returning
  `ACCEPTED`.
- CL-03 and CL-03f can be marked closed, while CL-04 becomes the next active
  gate.
