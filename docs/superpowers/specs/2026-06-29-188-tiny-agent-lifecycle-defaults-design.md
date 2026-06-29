# Phase 188 Tiny-Agent Lifecycle Defaults Design

## Goal

Make Craftless client creation easier for small agents before gameplay begins.
A tiny agent should be able to create an API-first automation client without
guessing profile names, window behavior, or audio behavior, while gameplay
remains entirely generated from the live per-client OpenAPI document.

## Self-Questioning

Could this become a scenario API? Only if the public contract starts saying
what to play or which Minecraft actions to invoke. This design does not add
`play`, `follow`, `collect`, `craft`, combat, navigation, recipe, or server
shortcut APIs. It only records lifecycle intent for a real client process.

Should Craftless expose a client `role`? No. The role would not map to a
concrete launch behavior and could invite agents to treat lifecycle intent as
gameplay semantics. The smaller contract is better: profile defaulting plus
presentation settings.

Could `window = NONE` overclaim true Minecraft headlessness? Yes. Minecraft
Java still has platform and graphics constraints. The contract says Craftless
requests an API-first, non-visible presentation by default. If a lane cannot
honor it, the effective response still exposes the requested presentation so
agents have a stable lifecycle intent to inspect. Runtime capability and
gameplay availability still come from generated OpenAPI.

Can muted audio be honored without pretending to solve all headless graphics
constraints? Yes. The process launcher can materialize Minecraft `options.txt`
sound categories at `0.0` when `audio = MUTED`, while keeping `window = NONE`
as lifecycle presentation intent until a lane proves stronger window control.

Could defaults break existing users? Existing JSON with an explicit profile
continues to work. Omitting `profile` becomes valid and derives a short offline
name from `id`. Existing CLI calls with `--offline-name` continue to work.

## Public Contract

Extend the stable supervisor client lifecycle model:

- `CreateClientRequest.profile` becomes optional.
- `CreateClientRequest.presentation` defaults to
  `{ "window": "NONE", "audio": "MUTED" }`.
- `Client` responses include the resolved `presentation`.
- Generated OpenAPI advertises the presentation enum values and defaults.

The public enum values are Craftless-owned:

- `ClientWindowMode.NONE`
- `ClientWindowMode.VISIBLE`
- `ClientAudioMode.MUTED`
- `ClientAudioMode.DEFAULT`

## Profile Defaults

When `profile` is omitted, the daemon derives an offline profile from the
client id:

- keep ASCII letters and digits from the id;
- capitalize the first character;
- truncate to Minecraft's 16-character offline name limit;
- if no letters or digits remain, use `Player`.

Examples:

- `bot` -> `Bot`
- `api-bot-01` -> `Apibot01`
- `robot_runner_123456789` -> `Robotrunner12345`

## CLI Contract

The CLI stays adaptive for gameplay. Static lifecycle flags are allowed:

```sh
craftless clients create bot --version latest-release --loader fabric
craftless clients create robin --version latest-release --loader fabric --visible --audio default
```

CLI behavior:

- `--offline-name` remains supported.
- `--visible` sets `presentation.window` to `VISIBLE`.
- `--audio muted|default` is optional and defaults to `muted`.
- usage text names these options and says `server start` is the Craftless
  supervisor, not a Minecraft game server.

## Docs And Skill Guidance

README and the repo-local public gameplay skill should include a two-client
co-play bootstrap:

1. start the Craftless supervisor;
2. create a bot client with defaults;
3. create a visible human client only when Craftless should manage the human
   window, otherwise let the human join with their own launcher;
4. connect clients to the server;
5. fetch the bot client's generated OpenAPI before gameplay;
6. coordinate through Minecraft chat and public events.

This recipe must not list static gameplay commands beyond generic discovery
and invocation.

## Testing

Use the lightest layered tests:

- protocol tests for optional profile/default presentation model and schemas;
- daemon tests for resolved profile and response metadata;
- CLI tests for default request payload and explicit human/visible/audio flags;
- docs checks with `git diff --check`.

Focused verification commands:

```sh
mise exec -- gradle :protocol:test --tests com.minekube.craftless.protocol.ClientModelsTest --tests com.minekube.craftless.protocol.OpenApiGenerationTest
mise exec -- gradle :daemon:test --tests com.minekube.craftless.daemon.ClientSessionServiceTest --tests com.minekube.craftless.daemon.LocalSessionApiServerTest
mise exec -- gradle :cli:test --tests com.minekube.craftless.cli.CraftlessCliTest
git diff --check
```
