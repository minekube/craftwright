# Client Lifecycle Surface Clarity Design

## Problem

Codex session `019f121c-6725-7aa3-85b2-c7bbe08ccd40` repeatedly launched new
Craftless-managed Minecraft clients while debugging. The session transcript
showed timestamped client ids such as `codexhub...`, `codexpub...`,
`codexdbg...`, and `codexnew...` used as retries against the same daemon.

The agent sometimes listed clients, but still treated `craftless clients create`
as a harmless setup, retry, or selection step. In Craftless, `POST /clients` and
`craftless clients create` launch a new daemon-managed real Minecraft Java
client process. Fresh timestamped ids avoided duplicate-id errors and left prior
clients running.

## Root Cause

The durable behavior was correct, but the public surfaces were not explicit
enough for agents:

- supervisor OpenAPI route metadata did not describe `POST /clients` as process
  launch;
- generated CLI help did not print route descriptions;
- README and the repo-local gameplay-agent skill showed create flows before
  lifecycle discipline;
- the skill said to use unique client ids for independent attempts without
  sharply separating deliberate multi-client tests from retry loops.

## Design

Make lifecycle intent obvious at the machine-contract layer first:

- add route `summary` and `description` metadata to `ApiRoute`;
- emit those fields in `OpenApiDocument`;
- print summary and description in generated CLI route help;
- describe `GET /clients`, `GET /clients/{id}`, `POST /clients`,
  `POST /clients/{id}:connect`, and `POST /clients/{id}:stop` with reuse/stop
  guidance;
- keep command generation adaptive from OpenAPI metadata instead of adding a
  bespoke static CLI warning.

Then mirror the same warning in human and agent surfaces:

- README quickstart/API/agent workflow;
- `docs/agent-operating-contract.md`;
- `.agents/skills/craftless-public-gameplay-agent/SKILL.md`.

## Acceptance

- `POST /clients` OpenAPI description says it launches a new real client, is not
  selector/retry/reuse, tells agents to list/get first, and names
  `POST /clients/{id}:stop`.
- `craftless clients create --help` prints the same lifecycle warning from the
  generated supervisor OpenAPI.
- README and gameplay-agent skill include the same warning and stop command.
- Tests cover the OpenAPI contract, generated CLI help, and public docs.
