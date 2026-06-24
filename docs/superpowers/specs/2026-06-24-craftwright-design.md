# Craftwright Design

Date: 2026-06-24

## Purpose

Craftwright is a standalone open-source project for automating a real Minecraft
Java client from scripts, test runners, CI, and AI agents.

The first proving use case is Minekube end-to-end testing against Gate, Connect,
and ordinary Minecraft servers in offline mode. The broader goal is a reusable
automation platform: launch named clients, connect them to servers, send chat
and commands, inspect GUI/rendered text, collect artifacts, and expose those
capabilities through a short CLI and typed SDKs.

## Name And CLI

- Project: `Craftwright`
- Repository: `minekube/craftwright`
- Primary binary: `mcw`
- One-liner: `mcw automates real Minecraft Java clients for tests, agents, and CI.`
- License: Apache-2.0

The CLI is the canonical interface. SDKs and test adapters may wrap the same
daemon/protocol, but all important operations must remain possible from `mcw`.
The detailed CLI contract is tracked in
`docs/superpowers/specs/2026-06-24-mcw-cli-design.md`.

## Non-Goals

- Do not build a protocol bot like Mineflayer.
- Do not fork PrismLauncher as the product foundation.
- Do not make a Minekube-only test tool.
- Do not require online-mode Microsoft authentication for the first milestone.
- Do not start with a GUI.

## Prior Art

### HeadlessMC

HeadlessMC is the best foundation. It launches the real Minecraft Java client,
supports offline CI mode, can patch LWJGL for headless execution, can run with
Xvfb, manages Java/libraries/assets/modloaders, and has process lifecycle and a
JSON command-test runner.

### HMC-Specifics

HMC-Specifics supplies the in-game command bridge that makes real automation
possible. It exposes commands such as `connect`, `disconnect`, `msg`, `/`, `gui`,
`click`, `text`, `render`, `key`, and `quit` across supported Minecraft and
modloader versions.

### MC-Runtime-Test

MC-Runtime-Test is useful CI prior art. It wraps HeadlessMC and uses versioned
mods to validate client runtime behavior. Its scope is mostly mod/runtime
validation, not server/proxy E2E, so Craftwright should learn from its GitHub
Action and artifact patterns rather than depend on it as the core product.

### PortableMC And PrismLauncher

PortableMC has a clean launcher library model, offline UUID handling, and
quick-play support. PrismLauncher has a mature staged launch pipeline and
instance model. Both are references. PrismLauncher is GPL-3 and UI-heavy, so it
should not be the direct base for Craftwright.

## Architecture

Craftwright has four layers.

1. Engine supervisor

   The supervisor owns client processes and integrates with HeadlessMC and
   HMC-Specifics. It launches real clients, names them, wires stdin/stdout,
   parses logs/events, enforces timeouts, kills leaked processes, and collects
   crash reports, logs, screenshots, and test artifacts.

2. Daemon protocol

   `mcw daemon` exposes the same operations over a machine protocol. The first
   transport should be stdio JSONL or JSON-RPC because it works well for CLIs,
   test runners, and AI agents. A TCP transport can be added later for longer
   lived local services.

3. CLI

   `mcw` is human-first and script-friendly. It has stable JSON/JSONL output,
   deterministic exit codes, non-interactive operation, and composable
   subcommands.

4. SDKs and adapters

   TypeScript should be the first SDK because Playwright/Vitest and AI agent
   tooling are strong there. The SDK wraps the daemon protocol, not private CLI
   text output. Playwright Test is the first E2E adapter; Vitest is useful for
   unit and integration tests around scenario parsing and lightweight fixtures.

## CLI Contract

Global rules:

- `-h, --help` prints help and ignores other flags.
- `--version` prints the version to stdout.
- Primary data goes to stdout.
- Diagnostics and errors go to stderr.
- `--json` emits one JSON object for finite results.
- `--jsonl` emits one JSON object per event for streams.
- `--plain` emits stable line-based text for shell scripts.
- `--no-input` disables prompts and must be safe for CI.
- `--quiet` suppresses non-essential human output.
- `--verbose` increases diagnostics.
- Respect `NO_COLOR`, `TERM=dumb`, and `--no-color`.

Initial command tree:

```text
mcw init [--dir PATH]
mcw cache prepare --mc VERSION [--loader fabric|forge|neoforge|vanilla]
mcw client launch NAME --mc VERSION [--loader LOADER] [--offline] [--server HOST[:PORT]]
mcw client list
mcw client connect NAME HOST[:PORT]
mcw client disconnect NAME
mcw client chat NAME MESSAGE
mcw client command NAME COMMAND
mcw client wait NAME --event EVENT [--timeout DURATION]
mcw client gui NAME [--json]
mcw client click NAME TARGET
mcw client key NAME KEY [--duration DURATION]
mcw client render NAME [--duration DURATION] [--jsonl]
mcw client logs NAME [--follow] [--jsonl]
mcw client stop NAME [--force]
mcw scenario run FILE [--artifacts DIR] [--json]
mcw daemon [--stdio | --tcp ADDR]
```

Exit codes:

- `0`: success
- `1`: generic failure
- `2`: invalid usage or config
- `3`: test/scenario assertion failed
- `4`: timeout
- `5`: client launch failed
- `6`: client crashed
- `7`: daemon/protocol error

Configuration precedence:

1. Flags
2. Environment variables
3. Project config, for example `craftwright.yaml`
4. User config
5. Built-in defaults

Secrets must not be passed through flags. Online-mode support, when added, must
use environment variables, credential stores, or explicit auth files.

## TypeScript SDK

The first SDK should expose a small high-level API:

```ts
const mc = await craftwright.start()
const alice = await mc.launch({ name: "alice", version: "1.21.6", offline: true })

await alice.connect("localhost", 25565)
await alice.chat("hello")
await alice.waitForChat(/Welcome/)
await alice.command("/server lobby")
await alice.stop()
```

The SDK should preserve access to lower-level events so test runners and agents
can inspect logs, GUI state, rendered text, and raw command results.

## Playwright Adapter

Playwright Test should be the primary full E2E adapter because it already solves
parallel workers, retries, fixtures, test steps, timeouts, reporters, traces, and
artifact retention.

Example target API:

```ts
import { test, expect } from "@minekube/craftwright/playwright"

test("player can join through Gate", async ({ mc }) => {
  const alice = await mc.launch("alice")
  await alice.connect("localhost", 25565)
  await expect(alice).toHaveChat(/Welcome/)
})
```

The adapter should add custom matchers for common Minecraft conditions:

- `toBeConnected()`
- `toHaveChat(pattern)`
- `toHaveRenderedText(pattern)`
- `toShowScreen(pattern)`
- `toDisconnectWith(pattern)`

## Vitest Adapter

Vitest is secondary for full E2E but important for fast tests. Use it for:

- scenario parser tests
- CLI contract tests
- daemon protocol tests
- fake-client/fake-server integration tests
- TypeScript SDK unit tests

If a Vitest adapter is added, it should share the same SDK and fixture concepts
as Playwright, but not become the default long-running process orchestrator.

## Scenario Files

Craftwright should support declarative scenario files for users who do not want
to write TypeScript.

Example:

```yaml
version: 1
clients:
  alice:
    mc: "1.21.6"
    offline: true
steps:
  - launch: alice
  - connect:
      client: alice
      server: "localhost:25565"
  - wait:
      client: alice
      chat: "/Welcome/"
      timeout: 30s
  - chat:
      client: alice
      message: "hello from craftwright"
```

The scenario runner should produce the same event stream as imperative SDK
tests.

## AI Agent Control

The daemon protocol should be suitable for agents from the beginning. A later MCP
server can expose tools such as:

- `launch_client`
- `connect_client`
- `send_chat`
- `send_command`
- `wait_for_chat`
- `dump_gui`
- `click_gui`
- `press_key`
- `collect_artifacts`
- `stop_client`

Agent-facing tools must be bounded by explicit timeouts and should return compact
structured summaries rather than unbounded logs by default.

## First Milestone

Milestone 1 proves that Craftwright can run a real client against an offline-mode
server in CI:

- `mcw cache prepare`
- `mcw client launch --offline`
- `mcw client connect`
- `mcw client chat`
- `mcw client wait --event chat`
- `mcw client stop`
- `mcw scenario run`
- TypeScript SDK wrapper for the same operations
- Playwright fixture for one launched client
- artifacts directory with logs and crash reports

The implementation should prefer wrapping HeadlessMC and HMC-Specifics first. A
custom launcher core can be reconsidered only if HeadlessMC blocks critical
features.

## Risks

- HeadlessMC command tests are log/stdin-driven and may be too limited for rich
  assertions. Craftwright should add its own daemon event model instead of
  exposing raw HeadlessMC JSON tests as the main UX.
- HMC-Specifics support varies by Minecraft and modloader version. The first
  supported matrix should be narrow and explicit.
- Real clients are heavy. Parallelism must be opt-in and controlled by resource
  limits.
- Offline mode is ideal for first tests, but online-mode support needs careful
  secret handling later.
- Minecraft client startup is slow. Caching and prepare steps are part of the
  core UX, not an optimization afterthought.

## Milestone 1 Decisions

- Implement the `mcw` CLI and daemon supervisor in Go so users and agents get a
  small single binary.
- Implement the first SDK and test adapters in TypeScript, backed by the daemon
  protocol rather than shelling out to human CLI text.
- Use JSON-RPC 2.0 over stdio for `mcw daemon --stdio`; reserve JSONL for event
  streams inside JSON-RPC subscriptions or CLI `--jsonl` commands.
- Support Fabric plus HMC-Specifics first. Vanilla quick-play and Xvfb-only
  flows can be added after the command bridge is proven.
- Treat a reusable GitHub Action as Milestone 2. Milestone 1 should work in CI
  through explicit shell commands and Playwright config before wrapping that UX.
