# Public Agent OpenAPI Action Authority Design

## Problem

The public-agent gameplay runner fetches generated per-client OpenAPI, but it
still chooses its available action set and argument support from
`GET /clients/{id}/actions`.

`/clients/{id}/actions` is a convenience projection. The durable authority for
agent workflows is the generated per-client OpenAPI document and its
`x-craftless-actions` metadata.

## Design

The runner must:

1. Fetch supervisor OpenAPI.
2. Fetch generated per-client OpenAPI.
3. Fetch `/actions` only as a diagnostic projection artifact.
4. Use `clientSpec["x-craftless-actions"]` for:
   - available action ids;
   - required primitive checks;
   - action argument support checks.

If a generated client OpenAPI document lacks `x-craftless-actions`, the runner
should block with an explicit public evidence reason instead of silently
treating `/actions` as the source of truth.

## Non-Goals

- Do not add gameplay actions.
- Do not change the survival scenario sequence in this slice.
- Do not remove the `/actions` endpoint.
- Do not change invocation transport.

## Acceptance

- A public-agent test passes when the generated client OpenAPI contains all
  required actions but `/clients/{id}/actions` returns an empty projection.
- The runner still records `/actions` as a fetched projection artifact.
- Argument support checks use generated client OpenAPI metadata.
- Existing public-agent tests continue to pass.
