# Craftless Final Gameplay Runbook

This runbook is the final completion gate. It is not a substitute for the gate:
Craftless is only complete after Robin confirms in Minecraft chat that the goal
may be completed.

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
keeps the client alive for a bounded human play window.

The old provisioned-item smoke is diagnostic only. It must not be used as final
completion evidence. The final run must start with empty or ordinary survival
inventory and obtain equipment through normal gameplay.

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
- `runtime-metadata.json`

## Invite Robin

When the client has joined and the automated sequence is holding the session
open, run:

```sh
say "Robin, join the Craftless test server now and confirm in Minecraft chat when the goal may be completed."
```

Robin should join or observe the server. Continue using Craftless and the
visible client to verify chat, inventory/tool equip, movement, block
interaction, mining, and a small build/place action. Any failures found here
must be fixed and reverified before completion.

## Completion Rule

Do not call `update_goal(status = "complete")` until all of these are true:

- `mise run lint` passes.
- `mise run architecture-check` passes.
- `mise run ci` passes.
- Final gameplay artifacts exist in the evidence path.
- The gameplay did not bypass the runtime graph with a new static public action
  surface.
- No item was granted through a server command, pre-seeded inventory, or manual
  operator intervention for Craftless.
- Craftless obtained materials, crafted or otherwise legitimately obtained a
  weapon, found a cow, navigated to it, and killed it through discovered
  capabilities.
- Robin writes in Minecraft chat that the goal may be completed.
