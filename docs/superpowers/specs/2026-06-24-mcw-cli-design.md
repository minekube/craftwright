# `mcw` CLI Design

Date: 2026-06-24

## Name

- Command: `mcw`
- Project: Craftwright
- One-liner: `mcw automates real Minecraft Java clients for tests, agents, and CI.`

`mcw` is the canonical interface for Craftwright. Human users, shell scripts,
test runners, SDKs, and AI agents should all be able to drive the same core
capabilities through the CLI or the daemon protocol it exposes.

## Design Principles

- Human-first default output, stable machine output on request.
- Every meaningful operation must work non-interactively for CI and agents.
- Primary result data goes to stdout; progress, diagnostics, and errors go to
  stderr.
- Text output may evolve; `--json`, `--jsonl`, and `--plain` are script
  contracts.
- Commands should be idempotent where practical, especially setup and cleanup.
- Long-running operations must accept explicit timeouts.
- Client names are stable handles for subsequent commands.

## Usage

```text
mcw [global flags] <command> [args]
mcw help [command]
mcw <command> --help
```

## Command Tree

```text
mcw init [--dir PATH] [--dry-run] [--force]
mcw cache prepare --mc VERSION [--loader LOADER] [--profile PROFILE]
mcw cache list [--json|--plain]
mcw cache clean [--profile PROFILE] [--dry-run] [--force]

mcw client launch NAME --mc VERSION [--loader LOADER] [--offline]
                      [--username USERNAME] [--server HOST[:PORT]]
                      [--profile PROFILE] [--timeout DURATION]
                      [--artifacts DIR]
mcw client list [--json|--plain]
mcw client status NAME [--json|--plain]
mcw client connect NAME HOST[:PORT] [--timeout DURATION]
mcw client disconnect NAME [--timeout DURATION]
mcw client chat NAME MESSAGE
mcw client command NAME COMMAND
mcw client wait NAME (--chat PATTERN | --rendered PATTERN | --screen PATTERN | --event EVENT)
                    [--timeout DURATION] [--json|--plain]
mcw client gui NAME [--json]
mcw client click NAME TARGET [--timeout DURATION]
mcw client key NAME KEY [--duration DURATION]
mcw client render NAME [--duration DURATION] [--jsonl]
mcw client logs NAME [--follow] [--jsonl]
mcw client stop NAME [--force] [--timeout DURATION]
mcw client stop-all [--force] [--timeout DURATION]

mcw scenario run FILE [--artifacts DIR] [--timeout DURATION] [--json|--jsonl]
mcw scenario validate FILE [--json|--plain]

mcw daemon --stdio
mcw daemon --tcp ADDR
```

## Global Flags

| Flag | Type | Default | Meaning |
| --- | --- | --- | --- |
| `-h, --help` | bool | false | Show help and ignore all other args. |
| `--version` | bool | false | Print version to stdout. |
| `--json` | bool | false | Emit one structured JSON result to stdout. |
| `--jsonl` | bool | false | Emit newline-delimited JSON events to stdout. |
| `--plain` | bool | false | Emit stable line-oriented text to stdout. |
| `-q, --quiet` | bool | false | Suppress non-essential human output. |
| `-v, --verbose` | count | 0 | Increase diagnostics on stderr. |
| `--debug` | bool | false | Include debug diagnostics and log file path. |
| `--no-input` | bool | false | Never prompt; fail if required input is missing. |
| `--no-color` | bool | false | Disable color in human output. |
| `--config PATH` | path | auto | Use an explicit project config file. |
| `--work-dir PATH` | path | current dir | Run relative to this project directory. |

Invalid combinations, such as `--json --plain`, fail with exit code `2`.

## Common Args And Types

| Name | Type | Examples | Notes |
| --- | --- | --- | --- |
| `NAME` | client id | `alice`, `proxy-smoke-1` | Lowercase letters, numbers, `_`, `-`, and `.`. |
| `VERSION` | Minecraft version | `1.21.6`, `1.20.4` | Exact versions only in Milestone 1. |
| `LOADER` | enum | `fabric`, `vanilla` | Milestone 1 supports `fabric`; `vanilla` is reserved for launch-only smoke tests. |
| `HOST[:PORT]` | address | `localhost:25565`, `gate.test` | Default port is `25565`. |
| `DURATION` | duration | `10s`, `2m`, `1500ms` | Must include a unit. |
| `PATTERN` | string/regex | `Welcome`, `/joined/` | Slash-delimited values are regexes. |
| `TARGET` | selector | `slot:13`, `text:Survival` | Target grammar can grow additively. |

## I/O Contract

### stdout

stdout is only for primary command output:

- IDs, summaries, and state in human mode.
- JSON result objects when `--json` is set.
- JSONL event streams when `--jsonl` is set.
- Stable line-oriented values when `--plain` is set.

### stderr

stderr is for:

- Progress messages.
- Cache/download status.
- Launch diagnostics.
- Warnings.
- Errors.
- Debug log paths.

Progress bars and spinners are allowed only when stderr is a TTY. They must be
disabled when stderr is not a TTY, `TERM=dumb`, `NO_COLOR` is set, or
`--no-color` is set.

### TTY Behavior

- If stdin is not a TTY, commands must not prompt.
- `--no-input` disables prompts even when stdin is a TTY.
- Human output may use tables and color when stdout is a TTY.
- Machine output must not include ANSI color or progress text.
- Long output may use a pager only in interactive human mode.

## Output Modes

### Human

Human output should be compact but useful:

```text
Launched client alice
Minecraft: 1.21.6
Mode: offline
Artifacts: .craftwright/artifacts/alice
Next: mcw client connect alice localhost:25565
```

### JSON

Finite commands emit one JSON object:

```json
{
  "ok": true,
  "client": {
    "name": "alice",
    "state": "running",
    "minecraftVersion": "1.21.6",
    "offline": true
  },
  "artifactsDir": ".craftwright/artifacts/alice"
}
```

Errors in JSON mode also emit one JSON object to stdout unless argument parsing
fails before output mode is known. The human-readable error still goes to stderr.

```json
{
  "ok": false,
  "error": {
    "code": "CLIENT_TIMEOUT",
    "message": "client alice did not reach the title screen within 2m",
    "retryable": true
  }
}
```

### JSONL

Streaming commands emit one event per line:

```jsonl
{"type":"client.log","client":"alice","level":"info","message":"Connecting to localhost:25565"}
{"type":"client.chat","client":"alice","message":"Welcome alice"}
{"type":"client.state","client":"alice","state":"connected"}
```

### Plain

Plain mode is for shell scripts and should be stable:

```text
alice running 1.21.6 offline
bob stopped 1.21.6 offline
```

## Exit Codes

| Code | Meaning |
| --- | --- |
| `0` | Success. |
| `1` | Generic failure. |
| `2` | Invalid usage, invalid flags, invalid config, or missing required input. |
| `3` | Scenario or test assertion failed. |
| `4` | Timeout. |
| `5` | Client launch failed. |
| `6` | Client crashed. |
| `7` | Daemon or protocol error. |
| `8` | Cache, asset, Java, or dependency preparation failed. |
| `130` | Interrupted by Ctrl-C. |

Exit codes are intentionally small and coarse. Detailed failure data belongs in
JSON output and artifact logs.

## Command Semantics

### `mcw init`

Creates project-local Craftwright config and directories.

- Writes `craftwright.yaml` unless `--dir` points elsewhere.
- Creates `.craftwright/` for cache metadata and artifacts.
- Must be safe to rerun.
- With `--dry-run`, prints intended changes and writes nothing.
- If files already exist, fail unless the existing files are compatible or
  `--force` is provided.

### `mcw cache prepare`

Prepares Minecraft assets, Java runtime metadata, modloader files, and
Craftwright bridge files for a version/profile.

- Must be idempotent.
- Should print progress to stderr.
- Should support CI caching by keeping deterministic paths.
- Fails with exit code `8` for dependency/cache failures.

### `mcw client launch`

Launches one real Minecraft Java client and registers it under `NAME`.

- Default mode is offline for Milestone 1.
- `--username` defaults to `NAME`.
- If `--server` is provided, launch then connect.
- The command returns when the client reaches the first controllable state, not
  merely when the JVM process starts.
- Artifacts are written under the provided `--artifacts` dir or the configured
  artifact root.
- If `NAME` already exists and is running, fail with exit code `2`.

### `mcw client connect`

Connects a launched client to a server.

- Returns when the connection is established or a disconnect/error occurs.
- Default timeout comes from config.
- Server addresses without a port use `25565`.

### `mcw client wait`

Waits for a bounded condition:

- `--chat PATTERN`: chat message appears.
- `--rendered PATTERN`: rendered text appears.
- `--screen PATTERN`: GUI/screen title or label appears.
- `--event EVENT`: low-level event type appears.

On timeout, returns exit code `4` and includes the last known client state in
JSON mode.

### `mcw client logs`

Prints client logs.

- Without `--follow`, prints the retained log and exits.
- With `--follow`, streams until interrupted or the client exits.
- `--jsonl` wraps each line as a structured log event.

### `mcw client stop`

Stops a client.

- First tries graceful in-game/client shutdown.
- With `--force`, kills the process if graceful shutdown does not complete.
- Returns success if the client is already stopped.

### `mcw scenario run`

Runs a declarative scenario file.

- Emits a final scenario result in `--json` mode.
- Emits step and client events in `--jsonl` mode.
- Uses exit code `3` for assertion failure and `4` for timeout.
- Always attempts bounded cleanup of launched clients.

### `mcw daemon`

Starts the machine protocol server.

- `--stdio` is the primary SDK and agent transport.
- `--tcp ADDR` is for local long-lived orchestration and must bind localhost by
  default.
- Milestone 1 protocol is JSON-RPC 2.0 over stdio.
- Daemon logs go to stderr; protocol messages go to stdout/stdin.

## Daemon Protocol Boundary

SDKs and test adapters must use `mcw daemon --stdio`, not parse human CLI text.

Minimum JSON-RPC methods:

- `cache.prepare`
- `client.launch`
- `client.list`
- `client.status`
- `client.connect`
- `client.disconnect`
- `client.chat`
- `client.command`
- `client.wait`
- `client.gui`
- `client.click`
- `client.key`
- `client.render`
- `client.logs`
- `client.stop`
- `scenario.run`
- `artifact.collect`

Event names should match CLI JSONL event names where possible.

## Config And Env

Precedence, highest to lowest:

1. Flags.
2. Process environment.
3. Project config, default `craftwright.yaml`.
4. User config, default XDG config path.
5. System config.
6. Built-in defaults.

Project config example:

```yaml
version: 1
defaults:
  minecraft: "1.21.6"
  loader: fabric
  offline: true
  timeout: 2m
paths:
  artifacts: .craftwright/artifacts
  cache: .craftwright/cache
```

Environment variables:

| Env var | Meaning |
| --- | --- |
| `MCW_CONFIG` | Config file path. |
| `MCW_WORK_DIR` | Project working directory. |
| `MCW_CACHE_DIR` | Cache directory override. |
| `MCW_ARTIFACTS_DIR` | Artifact root override. |
| `MCW_TIMEOUT` | Default operation timeout. |
| `MCW_NO_INPUT` | Treat as `--no-input` when set to `1` or `true`. |
| `NO_COLOR` | Disable color. |
| `FORCE_COLOR` | Force color in TTY human output. |
| `DEBUG` | Enable debug diagnostics when it includes `mcw` or `craftwright`. |
| `HTTP_PROXY`, `HTTPS_PROXY`, `NO_PROXY` | Network proxy settings for downloads. |

Secrets must not be accepted through flags. Online-mode support should use
credential stores, auth files, or explicit stdin/file flows when added.

## Safety Rules

- Destructive cache cleanup requires confirmation in interactive mode.
- Non-interactive destructive cleanup requires `--force`.
- `--dry-run` is required on commands that create or delete project files where
  previewing is useful.
- `--no-input` must never hang waiting for a prompt.
- Ctrl-C should print a short interruption message to stderr and exit with
  `130`.
- Cleanup must be bounded. A second Ctrl-C may skip cleanup and exit quickly.
- Process cleanup should be safe to resume with `mcw client stop-all --force`.

## Help Requirements

All of these must work:

```text
mcw --help
mcw -h
mcw help
mcw help client launch
mcw client launch --help
mcw client launch -h
```

Top-level help should include:

- One-line purpose.
- Common examples first.
- Common commands grouped by workflow.
- Link to GitHub repo and docs.
- Pointer to `mcw help <command>`.

Command help should include:

- Usage synopsis.
- Required args and flags.
- Output modes.
- Exit codes specific to the command.
- Two or three realistic examples.

## Examples

Initialize a repo:

```sh
mcw init
```

Prepare Minecraft and bridge files for CI:

```sh
mcw cache prepare --mc 1.21.6 --loader fabric --no-input
```

Launch an offline client:

```sh
mcw client launch alice --mc 1.21.6 --offline
```

Launch and connect in one command:

```sh
mcw client launch alice --mc 1.21.6 --offline --server localhost:25565
```

Wait for a proxy/server welcome message:

```sh
mcw client wait alice --chat /Welcome/ --timeout 30s
```

Send a chat message:

```sh
mcw client chat alice "hello from craftwright"
```

Run a scenario and collect artifacts:

```sh
mcw scenario run scenarios/gate-smoke.yaml --artifacts test-results/mcw --json
```

Use with shell scripts:

```sh
mcw client list --plain | awk '$2 == "running" { print $1 }'
```

Start the SDK/agent daemon:

```sh
mcw daemon --stdio
```

Stream logs as JSONL:

```sh
mcw client logs alice --follow --jsonl
```

## Milestone 1 CLI Surface

Milestone 1 should implement only the smallest useful subset:

```text
mcw init
mcw cache prepare
mcw client launch
mcw client list
mcw client status
mcw client connect
mcw client chat
mcw client wait --chat
mcw client logs
mcw client stop
mcw scenario validate
mcw scenario run
mcw daemon --stdio
```

Everything else in this spec is reserved shape. New commands and flags should be
additive and should preserve the output contracts above.

## Open Design Constraints

- Fabric plus HMC-Specifics is the first supported bridge path.
- Vanilla control is allowed only for launch/connect smoke tests until a command
  bridge exists.
- Online-mode authentication is out of scope for Milestone 1.
- Rich GUI selectors can start narrow and grow after real HMC-Specifics behavior
  is verified.
