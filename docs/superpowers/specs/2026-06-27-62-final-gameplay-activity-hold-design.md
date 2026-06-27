# Final Gameplay Activity Hold Design

## Intent

The held final gameplay session reached Robin and a Spark subagent could use the
generated public API, but the controller shut the client/API down when the
fixed confirmation deadline expired during active play. Robin had explicitly
asked to keep playing before sending the final confirmation phrase, so a fixed
deadline from the ready artifact is too rigid for the final acceptance gate.

## Product Rules

- Keep the public API surface unchanged.
- Do not add gameplay actions, descriptors, generated route families, CLI
  gameplay catalogs, Fabric descriptor/binding pairs, or scenario shortcuts.
- Do not add Minecraft version support or public version-specific APIs.
- Keep timeout as a non-success outcome. Activity may extend the live handoff,
  but only Robin's configured Minecraft chat confirmation may complete the
  goal.
- Treat this as final-gameplay harness/evidence behavior only.
- Activity evidence may include Craftless-owned run metadata and Minecraft
  chat player/message fields already present in `server-evidence.jsonl`.

## Design

The Fabric smoke controller should support an optional
`CRAFTLESS_FABRIC_SMOKE_ACTIVITY_EXTENDS_HOLD_MS` duration. While waiting for
the configured confirmation phrase, the controller polls server chat evidence.
For each newly observed non-confirmation chat line, it extends the current
deadline to at least `now + activityExtension`.

This lets Robin or an external public-agent/subagent keep the live API session
open while actively playing, without treating activity as success and without
changing generated APIs. If no further activity arrives, the controller still
writes `final-gameplay-confirmation-timeout.json` and stops normally.

The ready and timeout artifacts should include the activity extension duration
so the held-session budget is auditable. Optional extension events should be
written to a JSONL artifact for post-run diagnosis.

## Evidence

Tests and artifacts must show:

- with `CRAFTLESS_FABRIC_SMOKE_ACTIVITY_EXTENDS_HOLD_MS`, a non-confirmation
  chat line observed before the original hold deadline keeps the controller
  from stopping at the original deadline;
- the controller eventually times out if no further confirmation or activity
  appears;
- the timeout artifact includes the configured activity extension duration;
- no public gameplay API breadth is added.
