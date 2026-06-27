# Public Agent Live Co-Play Guidance Design

## Intent

The latest held final gameplay run reached the Robin-ready window and an
external agent successfully used generated public actions, but it exposed two
agent-usability gaps:

- the agent initially guessed an invalid `navigation.plan` body before
  correcting it to the generated block-goal schema;
- the local follow helper interpreted the word `stop` inside an instruction as
  a stop command.

These are not reasons to add static gameplay APIs. They are evidence that the
repo-local public-agent skill must teach agents how to use the generated API
shape and live co-play instructions correctly.

## Product Rules

- Keep this phase in agent guidance, docs, specs, and checklist only.
- Do not add gameplay actions, generated route families, CLI gameplay catalogs,
  Fabric descriptor/binding pairs, scenario shortcuts, compiled lanes, public
  version-specific APIs, or new Minecraft support claims.
- Do not make `navigation.plan` a hard-coded CLI command or static SDK method.
- Do not use macOS `say` or desktop automation for gameplay coordination once
  a human is already in the Minecraft server.
- Preserve the final completion gate: Robin's exact Minecraft chat
  confirmation remains required.

## Design

Update `.agents/skills/craftless-public-gameplay-agent/SKILL.md` so future
agents controlling Craftless through the public API know to:

1. fetch the live per-client OpenAPI before invoking generated actions;
2. send `navigation.plan` using the generated block-goal shape with
   `goal.kind = "block"` and integer block coordinates when targeting a block;
3. verify `navigation.follow` with public `player.query`, `entity.query`, or
   block state before assuming movement succeeded;
4. treat clear standalone chat commands such as `stop`, `stopp`, or `halt` as
   stop commands, but not instruction text such as "follow me until I say
   stop";
5. use Minecraft chat and Craftless public events for live co-play
   coordination.

Update `docs/agent-skills.md`, `AGENTS.md`, and the completion checklist so
the guidance is visible in the same project-completion sequence as the other
final gameplay phases.

## Evidence

Evidence for this phase is:

- the repo-local public gameplay skill contains the live co-play and
  navigation-shape guidance;
- project docs/checklist register Phase 64;
- `git diff --check` passes.
