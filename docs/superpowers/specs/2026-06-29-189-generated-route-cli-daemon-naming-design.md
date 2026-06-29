# Phase 189 Generated Route CLI And Daemon Naming Design

## Goal

Make the `craftless` CLI interpret Craftless OpenAPI routes instead of
hand-maintaining API command branches. The CLI should keep only a tiny static
kernel for work that exists before an API is reachable, and every reachable
supervisor or per-client API route should become a CLI command with generated
help.

Also rename the local Craftless API process from the misleading `server`
group to `daemon`, while keeping `server start` as a hidden compatibility
alias.

## Source Observations

Current `cli/src/main/kotlin/com/minekube/craftless/cli/Main.kt` has two
different shapes:

- supervisor/lifecycle commands are hand-dispatched with checks such as
  `args.take(2) == listOf("clients", "create")`;
- per-client gameplay aliases are already generated from live per-client
  OpenAPI action metadata.

The current generated gameplay syntax is:

```sh
craftless clients alice player chat --message "hello"
craftless clients alice player chat "hello"
craftless clients alice world block break --max-distance 4.0
craftless clients alice player --help
craftless clients alice player move --help
```

That syntax comes from action ids and route shapes:

- action id `player.chat` maps to command tokens `player chat`;
- action id `world.block.break` maps to command tokens `world block break`;
- route `/clients/{id}/player:chat` maps to
  `clients <id> player chat`.

`protocol/src/main/kotlin/com/minekube/craftless/protocol/ApiRoute.kt`
already has stable supervisor routes, including:

```text
GET  /openapi.json
GET  /version
GET  /events
GET  /events:stream
POST /cache:prepare
POST /cache:export
POST /cache:cleanup
GET  /runtimes/java
POST /runtimes/java:resolve
GET  /clients
POST /clients
GET  /clients/{id}
GET  /clients/{id}/openapi.json
POST /clients/{id}:attach
POST /clients/{id}:connect
GET  /clients/{id}/actions
GET  /clients/{id}/resources
POST /clients/{id}:run
POST /clients/{id}:stop
GET  /clients/{id}/events
GET  /clients/{id}/events:stream
```

`OpenApiDocument` already includes route extensions such as
`x-craftless-owner`, `x-craftless-member`, `x-craftless-source`,
`x-craftless-target`, and `x-craftless-action`, plus request body schemas.
Phase 188 added enum/default metadata for `presentation`, which a generated
CLI can use for help.

## Design Pressure

The CLI cannot be entirely generated from a daemon API before the daemon is
running. A small static kernel remains necessary:

- `craftless daemon start`;
- API URL resolution from `--api`, `CRAFTLESS`, and defaults;
- fetching `GET /openapi.json`;
- fetching `GET /clients/{id}/openapi.json`;
- OpenAPI cache handling;
- generic route interpretation and HTTP dispatch.

Everything else should be data-driven from OpenAPI. Hardcoding an OpenAPI
interpreter is acceptable. Hardcoding the OpenAPI surface in CLI branches is
not the target shape.

## Approaches Considered

### Approach A: Pure Path-Derived CLI

Convert every path segment and custom method into CLI tokens:

- `/clients/{id}:connect` -> `clients <id> connect`;
- `/runtimes/java:resolve` -> `runtimes java resolve`;
- `/events:stream` -> `events stream`.

This is simple and works for many routes, but it cannot preserve current
ergonomics where needed:

- `GET /clients` should be `clients list`, not just `clients`;
- `GET /clients/{id}/events:stream` should keep current
  `clients <id> events` streaming behavior;
- `POST /clients` should keep `clients create <id> --version ...`, with
  ergonomic flags for nested fields such as `--offline-name`, `--visible`,
  and `--audio`.

### Approach B: Handwritten CLI Wrappers Calling A Generic HTTP Core

Keep current command branches but route them through one generic request
builder. This reduces duplicated HTTP code but does not solve the main
problem: the CLI still hand-maintains the public API command tree.

### Approach C: OpenAPI CLI Metadata With Generic Fallback

Add a Craftless-owned `x-craftless-cli` operation extension for routes whose
best command syntax is not obvious from the path alone. The CLI reads live
OpenAPI, turns operations into command entries, and invokes matching commands
through a generic route engine. Routes without explicit CLI metadata use a
deterministic path-derived fallback.

This is the recommended approach. It keeps the syntax grounded in the current
generated route shape, preserves compatibility, and lets new API routes become
CLI commands by adding route metadata in protocol/daemon, not code branches in
the CLI.

## Selected CLI Syntax

Primary daemon command:

```sh
craftless daemon start --port 8080 --workspace .craftless
```

Compatibility alias:

```sh
craftless server start --port 8080 --workspace .craftless
```

`server start` remains valid but is hidden from primary help and docs. The
first implementation should not print a deprecation warning on stderr because
existing scripts may expect only JSON on stdout/stderr behavior to stay quiet.

Generated supervisor commands should preserve today’s user-facing syntax:

```sh
craftless openapi
craftless version
craftless events
craftless events list

craftless cache prepare --mc latest-release --loader fabric --workspace .craftless
craftless cache export --manifest <handle> --workspace .craftless
craftless cache cleanup --manifest <handle> --workspace .craftless

craftless runtimes java list
craftless runtimes java resolve --mc latest-release

craftless clients list
craftless clients create bot --version latest-release --loader fabric
craftless clients create robin --version latest-release --loader fabric --offline-name Robin --visible --audio default
craftless clients alice get
craftless clients alice openapi
craftless clients alice attach --endpoint http://127.0.0.1:49152
craftless clients alice connect --host localhost --port 25565
craftless clients alice actions
craftless clients alice resources
craftless clients alice run player.chat --arg message=hello
craftless clients alice stop
craftless clients alice events
craftless clients alice events list
```

Generated per-client gameplay commands keep the existing syntax:

```sh
craftless clients alice player chat --message hello
craftless clients alice player chat hello
craftless clients alice world block break --max-distance 4.0
craftless clients alice player --help
craftless clients alice player move --help
```

## OpenAPI Metadata Contract

Add a serializable `x-craftless-cli` operation extension:

```json
{
  "command": ["clients", "{id}", "connect"],
  "aliases": [],
  "hidden": false,
  "stream": false,
  "body": {
    "bindings": [
      { "flag": "--host", "pointer": "/host", "type": "string", "required": true },
      { "flag": "--port", "pointer": "/port", "type": "integer", "required": true }
    ]
  }
}
```

The extension is Craftless-owned public metadata. It is not gameplay
authority. It tells adaptive consumers how to render command syntax for an
operation whose machine contract remains OpenAPI.

Fields:

- `command`: CLI tokens. Path parameters use `{name}` tokens.
- `aliases`: additional command token lists for compatibility.
- `hidden`: true for compatibility aliases or routes that should remain
  callable but not shown in primary help.
- `stream`: true when the command should stream the body instead of treating
  the response as a finite JSON value.
- `body.bindings`: optional ergonomic request body bindings.

When `body.bindings` is absent, the CLI derives flags from a JSON object
request schema:

- top-level scalar fields become kebab-case flags;
- required schema fields become required flags unless supplied by a path or
  positional binding;
- enum/default/nullable metadata appears in help;
- unsupported nested object fields require `--json <object>` or `--body
  <file>`.

For `POST /clients`, use explicit bindings so the existing ergonomic command
survives:

- `id` comes from the positional `{id}` command token;
- `version` comes from `--version`;
- `loader` comes from `--loader`;
- `loaderVersion` comes from `--loader-version`;
- `profile.name` comes from `--offline-name`, with `profile.kind` fixed to
  `OFFLINE` when supplied;
- `presentation.window` is `VISIBLE` when `--visible` is present and omitted
  otherwise so the API default applies;
- `presentation.audio` comes from `--audio muted|default`.

## Matching And Dispatch

The generated route engine should:

1. load the supervisor OpenAPI for commands outside a selected client runtime;
2. load the per-client OpenAPI for commands under `clients <id> ...` when the
   supervisor route table does not match;
3. match command tokens against `x-craftless-cli.command` and aliases first;
4. use path-derived fallback only for routes without explicit CLI metadata;
5. build path values, query values, and JSON request bodies from schema
   metadata;
6. invoke the operation with Ktor Client;
7. forward successful JSON responses to stdout and failures to stderr;
8. generate help from the same operation metadata used for invocation.

For per-client action aliases, keep the current safety checks:

- fetch `GET /clients/{id}/openapi.json`;
- require the action id in `x-craftless-actions`;
- require the alias route’s `x-craftless-action` to match that action id;
- build typed arguments from the action schema;
- call the generated alias route or generic run route only after those checks.

## Help Behavior

`craftless --help` and group help should use generated route metadata when the
API is reachable. If the API is not reachable, help may fall back to a bundled
supervisor spec generated from `ApiRouteCatalog.sessionDefaults()` so offline
help stays useful without hand-maintaining the route tree in CLI source.

Help must make the boundary visible:

- `daemon start` is static because it starts the API daemon;
- supervisor commands are generated from `GET /openapi.json`;
- per-client commands are generated from `GET /clients/{id}/openapi.json`.

## Compatibility

Keep these existing commands working:

- `craftless server start ...`;
- all current `clients ...` lifecycle commands;
- `clients <id> run <action>`;
- all current generated gameplay aliases;
- `cache ...`;
- `runtimes java ...`.

Primary help and README should prefer `daemon start`; `server start` should be
absent from primary help except in a compatibility/deprecation note.

## Non-Goals

- Do not add static gameplay commands.
- Do not add scenario, recipe, combat, navigation, or material-specific CLI
  commands.
- Do not replace OpenAPI with a separate CLI catalog.
- Do not remove current commands in the first implementation slice.
- Do not require a live daemon for basic offline CLI help.

## Tests

Protocol tests should prove supervisor OpenAPI contains `x-craftless-cli`
metadata for the stable route set.

CLI tests should prove:

- root help prefers `daemon start`;
- `server start` still works but is hidden from primary help;
- `clients create`, `clients list`, `clients <id> get`, `connect`, `stop`,
  `openapi`, `actions`, `resources`, `events`, and `run` are invoked through
  generated route metadata;
- `clients <id> player chat` and nested generated gameplay aliases still work;
- generated command help uses request schemas, enum values, defaults, and
  required fields from OpenAPI;
- stale `/actions` projections do not become command authority.

Focused verification:

```sh
mise exec -- gradle :protocol:test --tests com.minekube.craftless.protocol.OpenApiGenerationTest
mise exec -- gradle :cli:test --tests com.minekube.craftless.cli.CraftlessCliTest
git diff --check
```

Before completion or push:

```sh
mise run ci
git status --short --branch
```
