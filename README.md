# Craftless

[![CI](https://github.com/minekube/craftless/actions/workflows/ci.yml/badge.svg)](https://github.com/minekube/craftless/actions/workflows/ci.yml)
[![Docs](https://github.com/minekube/craftless/actions/workflows/docs-site.yml/badge.svg)](https://github.com/minekube/craftless/actions/workflows/docs-site.yml)
[![Release](https://img.shields.io/github/v/release/minekube/craftless?display_name=tag)](https://github.com/minekube/craftless/releases)
[![Docker](https://img.shields.io/badge/ghcr.io-minekube%2Fcraftless-blue)](https://github.com/minekube/craftless/pkgs/container/craftless)

![Craftless hero: generated local APIs controlling real Minecraft Java clients](docs/assets/craftless-hero.png)

Craftless is Browserless-style automation infrastructure for real Minecraft
Java clients. It launches or attaches to real clients, loads a small in-client
driver, discovers the live runtime capability graph, and exposes generated
local OpenAPI contracts for agents, tools, tests, and CI.

[Docs](https://craftless.minekube.com) ·
[API Reference](https://craftless.minekube.com/docs/api-reference) ·
[Releases](https://github.com/minekube/craftless/releases) ·
[Roadmap](docs/roadmap.md) ·
[Agent Skill](.agents/skills/craftless-public-gameplay-agent/SKILL.md)

## Why Craftless?

- **Real client automation.** Drive actual Minecraft Java clients, headless by
  default or visible when a human should join.
- **Generated APIs, not gameplay SDK drift.** The stable supervisor API manages
  lifecycle; each running client exposes its own generated OpenAPI document for
  gameplay, resources, schemas, availability, and events.
- **Agent-friendly control.** External agents can discover, stream, invoke, and
  debug without reading Craftless source or depending on Minecraft internals.
- **Packaged runtime.** The released CLI, Docker image, and GitHub Action carry
  the driver manifest and verified Fabric lanes.

## Quickstart

Install the latest released CLI on Linux or macOS:

```sh
curl -fsSL https://raw.githubusercontent.com/minekube/craftless/main/install.sh | sh
craftless daemon start --port 8080
export CRAFTLESS=http://127.0.0.1:8080
```

When `--workspace` is omitted, the daemon uses `CRAFTLESS_WORKSPACE` or
`~/.craftless/workspace`, so the default API can launch clients, prepare cache
state, and expose artifacts without extra setup.

Create an API-first client. By default, Craftless derives an offline profile
from `id`, requests a windowless Minecraft client, and mutes game audio. A
windowless launch requires a real windowless strategy such as Linux `xvfb-run`
or `CRAFTLESS_WINDOWLESS_WRAPPER`; use `presentation[window]=VISIBLE` when you
want a local window:

```sh
craftless api /clients --api "$CRAFTLESS" \
  -F id=bot -F version=latest-release -F loader=FABRIC
```

The same request over HTTP:

```sh
curl -sS "$CRAFTLESS/clients" \
  -H 'content-type: application/json' \
  -d '{"id":"bot","version": "latest-release","loader":"FABRIC"}'
```

Connect it to a Minecraft server:

```sh
craftless api /clients/bot:connect --api "$CRAFTLESS" \
  -F host=localhost -F port=25565
```

Discover the live contract before invoking actions:

```sh
craftless api /clients/bot/openapi.json --api "$CRAFTLESS"
craftless api /clients/bot/actions --api "$CRAFTLESS"
craftless api /clients/bot/resources --api "$CRAFTLESS"
```

Invoke only actions advertised by the generated client OpenAPI:

```sh
craftless api /clients/bot:run --api "$CRAFTLESS" \
  -F action=player.chat \
  -F args[message]="hello from Craftless"
```

Stream live events:

```sh
curl -N "$CRAFTLESS/clients/bot/events:stream"
```

## Install Options

Install a specific release:

```sh
CRAFTLESS_VERSION=v0.3.0 \
CRAFTLESS_INSTALL_DIR="$HOME/.local/bin" \
sh -c "$(curl -fsSL https://raw.githubusercontent.com/minekube/craftless/main/install.sh)"
```

Run the Docker image:

```sh
docker run --rm -p 8080:8080 \
  -v "$PWD/.craftless:/var/lib/craftless" \
  ghcr.io/minekube/craftless:latest
```

Use Craftless from GitHub Actions:

```yaml
jobs:
  minecraft:
    runs-on: ubuntu-latest
    steps:
      - uses: minekube/craftless/.github/actions/setup-craftless@v0.3.0
        id: craftless
        with:
          start: "true"
          workspace: .craftless

      - run: curl -fsSL "${{ steps.craftless.outputs.api-url }}/openapi.json"
```

The installer writes the launcher symlink to `$HOME/.local/bin` by default.
Set `CRAFTLESS_INSTALL_DIR` to use another directory. The installed
distribution carries the current, representative older, and latest/current
Fabric driver lanes, so `craftless daemon start` auto-discovers the packaged
driver manifest unless `CRAFTLESS_FABRIC_DRIVER_MOD` is set explicitly.
Minecraft artifacts are downloaded into the workspace at runtime; client and
server artifacts are resolved into the workspace and are not baked into the
Docker image.

## The `craftless` CLI

The CLI has a small stable core for daemon lifecycle plus one OpenAPI-backed
route invoker:

```sh
craftless --help
craftless daemon start --help
craftless api /clients --help --api "$CRAFTLESS"
craftless api /clients/bot/player:chat --method POST --help --api "$CRAFTLESS"
```

Use `craftless api <endpoint>` for supervisor and per-client routes. Help,
parameters, request fields, enums, and descriptions are inferred from the
daemon's OpenAPI schemas. When an endpoint has multiple methods, such as
`/clients`, `--help` lists each matching OpenAPI operation so agents can choose
the right `--method` without guessing.

## How Craftless Works

Craftless keeps the runtime shape deliberately thin:

1. **Supervisor API** manages workspaces, cache preparation, Java runtime
   resolution, clients, files, events, and per-client spec discovery.
2. **Generated per-client API** lives at `GET /clients/{id}/openapi.json` and
   reflects the running client's version, loader, driver runtime, installed
   mods, registries, server features, permissions, resources, actions, schemas,
   availability, events, and fingerprints.
3. **Runtime capability graph** is the internal source for discovered
   resources, operations, handles, schemas, event metadata, and source
   evidence.
4. **Generic invocation** uses `POST /clients/{id}:run` or generated routes
   derived from the live OpenAPI document.
5. **Live streams** use Server-Sent Events for lifecycle, runtime, capability,
   and gameplay observations. JSON-RPC-style HTTP calls handle invoke, query,
   subscribe, and unsubscribe control.
6. **Adaptive consumers** such as the CLI and agents fetch live specs instead
   of shipping static gameplay catalogs.

The HMC bridge code is lifecycle/launch evidence only. It is not the product
path for chat, movement, inventory, world, entity, screen, or building
behavior. It is not a gameplay adapter. Gameplay breadth belongs in the Fabric
runtime capability graph and generated per-client OpenAPI.

## Agent Contract

Agents should behave like outside Craftless users:

1. Fetch `GET /openapi.json`.
2. List existing clients with `GET /clients`; reuse a suitable client or stop
   abandoned clients before calling `POST /clients`.
3. Remember that `POST /clients` launches a new daemon-managed real Minecraft
   Java client process. It is not a selector, retry, or reuse operation.
4. Stop an abandoned client with
   `craftless api /clients/<id>:stop --api "$CRAFTLESS" -X POST`.
5. Create a fresh id only for a deliberate independent client, such as
   multi-client co-play or concurrency testing.
   Creating fresh timestamped ids for retries leaves multiple Minecraft
   clients running.
6. Fetch `GET /clients/{id}/openapi.json` and treat its `x-craftless-actions`,
   `x-craftless-resources`, route metadata, schemas, availability, and
   fingerprints as the gameplay authority.
7. Use `/clients/{id}/actions` and `/clients/{id}/resources` as projection
   evidence, not as independent catalogs.
8. Subscribe to `GET /clients/{id}/events:stream` before state-changing work.
9. Invoke only advertised actions through `POST /clients/{id}:run`, generated
   routes, or the API-aligned CLI.

The repo-local
[craftless-public-gameplay-agent](.agents/skills/craftless-public-gameplay-agent/SKILL.md)
skill captures the public-agent workflow. Final gameplay evidence must include
the connected OpenAPI document, projection artifacts, event capture, action
log, and public inventory/world/entity observations. Do not use
server-provisioned inventory, driver internals, Fabric internals, or hard-coded
scenario actions as product proof. Product proof must run without server-provisioned inventory.

## Two-Client Co-Play

For "let's play", keep lifecycle and gameplay separate:

```sh
craftless daemon start --port 8080
export CRAFTLESS=http://127.0.0.1:8080

craftless api /clients --api "$CRAFTLESS" \
  -F id=bot -F version=latest-release -F loader=FABRIC
craftless api /clients/bot:connect --api "$CRAFTLESS" \
  -F host=localhost -F port=25565
```

If Craftless should also manage the human-facing client window, opt into a
visible, normal-audio presentation:

```sh
craftless api /clients --api "$CRAFTLESS" \
  -F id=robin -F version=latest-release -F loader=FABRIC \
  -F profile[kind]=OFFLINE -F profile[name]=Robin \
  -F presentation[window]=VISIBLE -F presentation[audio]=DEFAULT
craftless api /clients/robin:connect --api "$CRAFTLESS" \
  -F host=localhost -F port=25565
```

If the human joins with their own launcher, skip the `robin` client and let the
bot coordinate through Minecraft chat and public Craftless events.

## Runtime Versions And Cache

Craftless can prepare repeatable launch/cache state before running clients:

```sh
craftless api /cache:prepare --api "$CRAFTLESS" \
  -F minecraftVersion=latest-release \
  -F loader=FABRIC
```

When omitted, client creation and cache preparation default to
`latest-release`. Use `latest-release` or `latest-snapshot` for Mojang aliases,
or pin a concrete Minecraft version when a run must be reproducible.

Discover runtime and loader support through the supervisor API before pinning:

```sh
craftless api /versions/support-targets --api "$CRAFTLESS"
craftless api /versions/runtime-targets --api "$CRAFTLESS"
craftless api /versions/loader-targets --api "$CRAFTLESS"
craftless api /versions/loaders --api "$CRAFTLESS"
craftless api /versions/driver-mods --api "$CRAFTLESS"
```

`/versions/support-targets` is the compatibility view agents should consult
first. It joins discovered Fabric Minecraft targets, game-scoped Fabric Loader
metadata, and the packaged Craftless driver manifest. Supported rows include
loader/runtime identity and driver-lane metadata; unsupported rows include
machine-readable reasons such as `NO_DRIVER_MOD`,
`NO_COMPATIBLE_DRIVER_MOD`, or `NO_COMPATIBLE_FABRIC_LOADER`.

Client creation rejects unsupported Fabric runtime requests before launch with
`UNSUPPORTED_RUNTIME_TARGET` and structured `details`, so callers can choose a
supported row without parsing the human message.

Java selection is part of the product runtime. Craftless evaluates configured,
managed, mise, and system runtime providers against Minecraft version
requirements.

## Current Verification

Verified product surfaces include:

- stable supervisor OpenAPI at `GET /openapi.json`;
- generated per-client OpenAPI at `GET /clients/{id}/openapi.json`;
- graph-projected actions, resources, handles, schemas, availability,
  fingerprints, generated routes, and event metadata;
- generic invocation through `POST /clients/{id}:run`;
- SSE event streams plus JSON-RPC-style HTTP control/query calls;
- API-aligned CLI discovery, OpenAPI-derived help, route invocation, action
  invocation, and event streaming;
- release workflow, install script, Docker runtime image, packaged CLI
  distribution, and reusable GitHub Action;
- Latest/current `26.2`, current `1.21.6`, and representative older `1.20.6`
  packaged lanes are verified with generated OpenAPI, projections, SSE,
  JSON-RPC, and `craftless api` invocation evidence;
- Fabric version support is exposed as a matrix at
  `GET /versions/support-targets`; supported runtime rows are probeable through
  the packaged matrix workflow, and unsupported runtime requests fail closed
  before launch with machine-readable reasons;
- final public gameplay evidence using generated public APIs only, with server
  provisioning disabled and no static survival macro.

The active completion checklist is the current source of truth:
[docs/project-completion-checklist.md](docs/project-completion-checklist.md).

## Comparison

| Area | Craftless | Mineflayer | Baritone | Prism Launcher |
| --- | --- | --- | --- | --- |
| Runs the real Minecraft Java client | Yes | No, protocol bot | Yes | Yes |
| Local OpenAPI control plane | Yes | No | No | No |
| Live per-client generated API | Yes | No | No | No |
| Runtime discovery from version/mod/server state | Core design | Protocol data oriented | In-client pathing state | Launcher/profile state |
| External agent and CI usage | Primary use case | Script/library use | Mod/API use | Launch management |
| Pathfinding maturity | Growing | Mature bot movement ecosystem | Mature pathfinding | Not a pathfinder |
| Public gameplay surface | Generated Craftless actions/resources | Library API | Java/mod commands/API | None |
| Best fit | Real-client automation infrastructure | Fast protocol bot scripts | In-game navigation/pathing | Client installation and launch UX |

Craftless is not trying to replace Mineflayer, Baritone, or Prism Launcher.
The goal is a Browserless-style runtime for real Minecraft clients where
agents can discover the live API and operate through stable local contracts.

## Development

Use pinned tools through `mise`:

```sh
mise install
mise run lint
mise run architecture-check
mise run ci
```

Focused commands:

```sh
mise exec -- gradle test
mise exec -- bun test playwright
mise run package-cli
mise run ci-craftless-smoke
```

Docs-only edits must at least pass:

```sh
git diff --check
```

Opt-in real Minecraft checks can download artifacts and launch server/client
processes:

```sh
CRAFTLESS_LOCAL_SERVER_SMOKE=1 mise exec -- gradle :testkit:localMinecraftServerSmoke
CRAFTLESS_FABRIC_CLIENT_SMOKE=1 mise exec -- gradle :driver-fabric:fabricClientSmoke
CRAFTLESS_FINAL_GAMEPLAY=1 mise exec -- gradle :driver-fabric:fabricFinalGameplay
```

Release Please opens or updates the release PR on pushes to `main`, manual
dispatch, and the weekly scheduled check when releasable changes exist since
the latest `v*` release. Merging that PR creates the next `vX.Y.Z` tag; the
tag-driven `release` workflow builds CLI archives, checksums, Docker image, and
GitHub release notes.

## Docs

- [Hosted docs](https://craftless.minekube.com)
- [API reference](https://craftless.minekube.com/docs/api-reference)
- [Product positioning](docs/product-positioning.md)
- [Client file management](docs/client-file-management.md)
- [Agent skills](docs/agent-skills.md)
- [Roadmap](docs/roadmap.md)
- [Final gameplay runbook](docs/final-gameplay-runbook.md)

Historical specs, plans, and evidence live under `docs/superpowers/`.
