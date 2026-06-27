# Craftless Final Gameplay Runbook

This runbook is the final completion gate. It is not a substitute for the gate:
Craftless is only complete after Codex verifies the generated public API/CLI
gameplay evidence, CI, distribution smoke checks, and compatibility probes.

## Preflight

Run:

```sh
mise run lint
mise run architecture-check
mise run ci
```

Do not continue to the live gameplay gate until these pass.

## Start The Final Gameplay Session

Run:

```sh
CRAFTLESS_FINAL_GAMEPLAY=1 mise exec -- gradle :driver-fabric:fabricFinalGameplay
```

The task starts the local Minecraft server fixture, launches the
Craftless-controlled Fabric client, invokes discovered graph-backed operations
through `POST /clients/{id}:run`, captures `/clients/{id}/events:stream`, and
records public-agent gameplay artifacts.

The outer local-server action timeout is separate from the Fabric action
timeout. `CRAFTLESS_FABRIC_SMOKE_ACTION_TIMEOUT_MS` controls Fabric API requests
and the public-agent request budget. `CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS`
controls the outer server fixture process and must cover client launch,
public-agent execution, any configured post-run hold window, and shutdown. The
`fabricFinalGameplay` task computes a large enough default; only override it
with `CRAFTLESS_LOCAL_SERVER_SMOKE_ACTION_TIMEOUT_MS` when a longer diagnostic
session is needed.

The harness may still write ready, confirmation, or timeout artifacts when a
diagnostic human hold is configured. Those artifacts are diagnostic handoff
evidence, not the required completion signal. Current completion is based on
public-agent gameplay results and the verification gates below.

The old provisioned-item smoke is diagnostic only. It must not be used as final
completion evidence. The final run must start with empty or ordinary survival
inventory and obtain equipment through normal gameplay.

The internal survival task harness is also diagnostic. It may expose missing
driver primitives, but it is not the durable product API and must not be the
only completion proof. Final completion requires an external agent-style run
that fetches the live per-client OpenAPI/actions/resources, subscribes to
events, and composes gameplay through generated Craftless actions/handles and
documented agent skills.

`fabricFinalGameplay` runs the process-external public-agent helper by default
while its in-memory daemon is alive. For targeted debugging against an existing
daemon URL, run the helper manually:

```sh
CRAFTLESS_PUBLIC_AGENT_BASE_URL=http://127.0.0.1:<daemon-port> \
CRAFTLESS_PUBLIC_AGENT_CLIENT_ID=fabric-smoke \
CRAFTLESS_PUBLIC_AGENT_ARTIFACTS_DIR=driver-fabric/build/craftless-final-gameplay/artifacts \
mise exec -- gradle :testkit:publicAgentGameplay
```

This helper must report missing generic primitives as blockers instead of
calling `task.survival.*` or adding scenario-specific actions.

Default evidence path:

```text
driver-fabric/build/craftless-final-gameplay/artifacts/
```

Required artifacts:

- `server.log`
- `server-evidence.jsonl`
- `client-openapi-connected.json`
- `client-actions-connected.json`
- `client-resources-connected.json`
- `client-events.jsonl`
- `client-events-stream.sse`
- `gameplay-results.jsonl`
- `public-agent-gameplay-results.jsonl`
- `public-agent-state.jsonl`
- `runtime-metadata.json`

Optional diagnostic artifacts:

- `final-gameplay-ready.json`
- `final-gameplay-confirmation.json`
- `final-gameplay-confirmation-timeout.json`

## Optional Human Observation

When the client has joined, the public-agent sequence has finished, and the
session enters the bounded hold window, the harness writes
`final-gameplay-ready.json`. On macOS, the opt-in `fabricFinalGameplay` task
also configures a default `say` prompt and repeats it during the hold window
when `CRAFTLESS_FABRIC_SMOKE_READY_REMINDER_MS` is positive:

```text
Craftless final gameplay is ready. Join localhost port <server-port> if you want to observe.
```

Override the prompt command with `CRAFTLESS_FABRIC_SMOKE_READY_COMMAND_JSON`.
The command receives:

- `CRAFTLESS_FABRIC_SMOKE_READY_BASE_URL`
- `CRAFTLESS_FABRIC_SMOKE_READY_CLIENT_ID`
- `CRAFTLESS_FABRIC_SMOKE_READY_SERVER_HOST`
- `CRAFTLESS_FABRIC_SMOKE_READY_SERVER_PORT`
- `CRAFTLESS_FABRIC_SMOKE_READY_ARTIFACTS_DIR`
- `CRAFTLESS_FABRIC_SMOKE_READY_HOLD_MS`

Human observation can still be useful for debugging visible-client behavior,
chat, inventory/tool equip, movement, block interaction, mining, and small
build/place actions. Any failures found during observation must be fixed and
reverified before completion, but a human chat phrase is no longer required.

## Completion Rule

Do not call `update_goal(status = "complete")` until all of these are true:

- `mise run lint` passes.
- `mise run architecture-check` passes.
- `mise run ci` passes.
- Final gameplay artifacts exist in the evidence path.
- The gameplay did not bypass the runtime graph with a new static public action
  surface.
- The survival scenario is reproducible by an external agent or adaptive CLI
  flow using generated OpenAPI/SSE/JSON-RPC metadata, not only by an internal
  hard-coded `task.survival.*` scenario.
- No item was granted through a server command, pre-seeded inventory, or manual
  operator intervention for Craftless.
- Craftless obtained materials, crafted or otherwise legitimately obtained a
  weapon, found a public combat target through generated entity perception
  with Cow preferred when visible, navigated to it, and killed it through
  discovered capabilities.
- Public-agent artifacts prove the survival scenario through generated
  OpenAPI/actions/resources, SSE events, and inventory/world/entity state.
- Latest and representative older-version compatibility probes have current
  evidence.
