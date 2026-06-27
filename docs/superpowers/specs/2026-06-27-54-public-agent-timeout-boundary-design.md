# Phase 54: Public Agent Timeout Boundary Design

## Goal

Make final gameplay failures surface as public-agent blocker artifacts by
separating the generated-action request timeout from the public-agent helper
process timeout.

## Context

The Phase 52 rerun exposed a real final-gameplay failure path. The public agent
crafted and equipped a wooden sword, then stalled while invoking generated
`navigation.follow` during combat target exploration. The helper did not write
`public-agent-blocked` because its generated-action HTTP timeout came from the
long outer `CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS`, while the Fabric smoke
controller waited only the shorter Fabric action timeout for the whole helper
process. That process timeout was too small for normal generated-API
exploration, and it killed the helper before it could either complete or report
a later `action-request-failed:navigation.follow` blocker.

## Requirements

- Public-agent generated-action HTTP requests must time out before the Fabric
  smoke controller times out the public-agent helper process.
- The public-agent helper process timeout must cover the whole generated-API
  gameplay attempt, not one generated action.
- `CRAFTLESS_PUBLIC_AGENT_ACTION_REQUEST_TIMEOUT_MS` remains the explicit
  highest-precedence override.
- When no explicit public-agent timeout is set, the helper should prefer
  `CRAFTLESS_FABRIC_SMOKE_ACTION_TIMEOUT_MS` over the long outer
  `CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS`.
- The `fabricFinalGameplay` Gradle task should export a
  `CRAFTLESS_PUBLIC_AGENT_ACTION_REQUEST_TIMEOUT_MS` default that is smaller
  than the Fabric action timeout.
- Timeout failures should be recorded by the public-agent helper as
  `action-request-failed:<action>` blocker artifacts.
- Do not add gameplay actions, scenario shortcuts, static catalogs, new route
  families, new Minecraft support claims, or public version-specific APIs.
- Do not mark Craftless complete.

## Non-Goals

- Do not change pathfinder semantics in this phase.
- Do not add aquatic combat special cases.
- Do not increase final gameplay evidence by provisioning server items or
  adding survival macros.

## Design

The public-agent helper owns generated-action request evidence. Its timeout
must be inside the Fabric smoke command timeout so it can catch failed HTTP
requests, append `public-agent-action` and `public-agent-blocked` artifacts,
and exit normally with `publicAgentState=BLOCKED`. The Fabric controller's
public-agent process timeout is separate and should come from the long outer
smoke timeout so normal generated-API exploration has enough wall-clock time.

`PublicAgentGameplayRunnerConfig.fromEnvironment()` should use this timeout
precedence:

1. `CRAFTLESS_PUBLIC_AGENT_ACTION_REQUEST_TIMEOUT_MS`;
2. `CRAFTLESS_FABRIC_SMOKE_ACTION_TIMEOUT_MS`;
3. `CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS`;
4. default.

The final gameplay Gradle task should set
`CRAFTLESS_PUBLIC_AGENT_ACTION_REQUEST_TIMEOUT_MS` to a guarded value below
`finalGameplayFabricActionTimeout()`, leaving at least a small margin for the
helper to write artifacts and exit if a single generated-action request stalls.
`runPublicAgentCommand()` should wait for the longer outer timeout, not the
short generated-action timeout.

## Verification

- Focused config tests prove Fabric timeout precedence and explicit override
  precedence.
- A build-script test proves final gameplay exports
  `CRAFTLESS_PUBLIC_AGENT_ACTION_REQUEST_TIMEOUT_MS`.
- A controller test proves the public-agent process timeout uses the long
  outer smoke timeout when final gameplay also sets a shorter Fabric action
  timeout.
- Existing public-agent failure tests prove failed generated action requests
  write blocker artifacts.
- `git diff --check`, focused tests, `mise run lint`,
  `mise run architecture-check`, and `mise run ci` pass before this phase is
  claimed complete.
