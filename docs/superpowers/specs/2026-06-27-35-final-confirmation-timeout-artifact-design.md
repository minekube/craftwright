# Phase 35: Final Confirmation Timeout Artifact Design

## Problem

The final gameplay harness can reach the human-ready window, prove the public
agent survival scenario, and then exit cleanly when Robin does not join or
confirm before the hold window expires. Today the missing
`final-gameplay-confirmation.json` is the only machine-readable signal that the
final completion gate was not satisfied.

Absence-only evidence is easy for agents and CI summaries to misread. The
harness should explicitly record that it waited for the configured Minecraft
chat confirmation and timed out without seeing it.

## Design

When the final gameplay hold reaches its deadline without a matching chat
confirmation, write `final-gameplay-confirmation-timeout.json` beside the other
final gameplay artifacts.

The artifact is evidence plumbing only. It must not:

- mark Craftless complete;
- fail a successful public-agent gameplay run by itself;
- add gameplay actions, routes, CLI commands, or public descriptors;
- bypass Robin's required Minecraft chat confirmation.

The artifact should include:

- event: `final-gameplay-confirmation-timeout`;
- base URL;
- client id;
- server address;
- hold duration;
- configured confirmation phrase, when present;
- artifacts directory.

If Robin's confirmation is observed, the harness writes
`final-gameplay-confirmation.json` and must not write the timeout artifact.

## Acceptance

- Focused test proves a hold with a configured phrase and no matching chat
  writes `final-gameplay-confirmation-timeout.json`.
- Existing confirmation test still proves a matching chat writes
  `final-gameplay-confirmation.json`.
- No generated gameplay action catalog or public API surface changes.
