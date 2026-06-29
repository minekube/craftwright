# Craftless

![Craftless hero: generated local APIs controlling real Minecraft Java clients](docs/assets/craftless-hero.png)

Craftless is automation infrastructure for real Minecraft Java clients,
headless or visible. It runs or attaches to real clients, loads a small
in-client driver, discovers the live runtime capability graph, and exposes
generated local APIs for agents, tools, tests, and CI.

The product shape is intentionally thin: stable lifecycle control outside the
client, generated per-client OpenAPI inside the live runtime, and adaptive
consumers that discover before they invoke. Craftless should feel like
Browserless-style infrastructure for Minecraft Java, not a hand-written
gameplay SDK.

## Status At A Glance

| Area | Current state |
| --- | --- |
| Runtime | Kotlin/JVM under `com.minekube.craftless` |
| Tooling | Pinned with `mise`; Bun only through `mise exec -- bun` |
| HTTP | Ktor Server and Ktor Client |
| CLI | Released `craftless` binary with install script and packaged driver-mod discovery |
| Distribution | CLI tar/zip, runtime Docker image, and reusable GitHub Action |
| API | Stable supervisor OpenAPI plus generated per-client OpenAPI |
| Gameplay surface | Runtime capability graph projection, not a static catalog |
| Events | SSE streams plus JSON-RPC-style HTTP control/query calls |
| Current Fabric lane | Verified for the compiled lane |
| Latest and older lanes | Latest/current `26.2` and representative older `1.20.6` packaged lanes are verified through create, attach, connect, generated OpenAPI, projections, SSE, JSON-RPC query/subscription, JSON-RPC invocation, and adaptive CLI invocation |
| Final gameplay evidence | Public API/CLI survival evidence passed with server provisioning disabled |
| Completion | All CL gates are closed; see `docs/project-completion-checklist.md` |

## Quickstart

Install the released CLI on Linux or macOS:

```sh
curl -fsSL https://raw.githubusercontent.com/minekube/craftless/main/install.sh | sh
craftless daemon start --port 8080 --workspace .craftless
```

Install a specific release:

```sh
CRAFTLESS_VERSION=v0.1.2 \
CRAFTLESS_INSTALL_DIR="$HOME/.local/bin" \
sh -c "$(curl -fsSL https://raw.githubusercontent.com/minekube/craftless/main/install.sh)"
```

The installer writes the launcher symlink to `$HOME/.local/bin` by default.
Set `CRAFTLESS_INSTALL_DIR` to use another directory.

The installed CLI distribution carries
`mods/craftless-driver-fabric.jar`, the representative older
`mods/fabric-1.20.6/craftless-driver-fabric.jar`, and the latest/current
`mods/fabric-26.2/craftless-driver-fabric-official.jar` lane. `craftless daemon
start` auto-discovers the packaged driver manifest unless
`CRAFTLESS_FABRIC_DRIVER_MOD` is set explicitly, so daemon-managed Fabric
clients do not need extra driver-mod configuration.

Check the CLI:

```sh
craftless --help
```

The CLI has a small stable core for daemon lifecycle, client lifecycle, cache,
runtime, discovery, output, and generic invocation. Gameplay commands and help
come from each live client's generated OpenAPI document.

Create an API-first automation client with lifecycle defaults:

```sh
craftless clients create bot --version latest-release --loader fabric --api "$CRAFTLESS"
```

`craftless clients create` launches a new daemon-managed real Minecraft Java
client process. It is not a selector, retry, or reuse operation. In an existing
daemon or workspace, run `craftless clients list --api "$CRAFTLESS"` and
`craftless clients <id> get --api "$CRAFTLESS"` first, then reuse a suitable
client or stop an abandoned one with
`craftless clients <id> stop --api "$CRAFTLESS"`. Creating fresh timestamped
ids for retries leaves multiple Minecraft clients running.

When `--offline-name` is omitted, Craftless derives a safe offline profile
from the client id. The default presentation requests no visible window and
materializes muted Minecraft sound options for API-first automation. Opt into
a visible, normal-audio client when Craftless should manage a human-facing
window:

```sh
craftless clients create robin --version latest-release --loader fabric \
  --offline-name Robin --visible --audio default --api "$CRAFTLESS"
```

## Docker

Run the runtime image:

```sh
docker run --rm -p 8080:8080 \
  -v "$PWD/.craftless:/var/lib/craftless" \
  ghcr.io/minekube/craftless:latest
```

The image contains the packaged Craftless CLI/runtime and required OS
libraries. Minecraft artifacts are downloaded into the workspace at runtime;
client/server artifacts are resolved into the mounted workspace and are not
baked into the image.

## GitHub Actions

Use Craftless from another workflow:

```yaml
jobs:
  minecraft:
    runs-on: ubuntu-latest
    steps:
      - uses: minekube/craftless/.github/actions/setup-craftless@v0.1.2
        id: craftless
        with:
          start: "true"
          workspace: .craftless

      - run: curl -fsSL "${{ steps.craftless.outputs.api-url }}/openapi.json"
```

The action installs the released CLI distribution and can optionally start
`craftless daemon start` for the job. Fabric driver-mod discovery follows the
same installed-distribution path as the install script.

## How Craftless Works

Craftless keeps the API layers separate:

1. Supervisor API: stable lifecycle, workspaces, clients, cache preparation,
   Java runtime resolution, files, events, and per-client spec discovery.
2. Live generated API: one `GET /clients/{id}/openapi.json` document per
   running client, reflecting that client's version, loader, driver runtime,
   installed mods, registries, server features, permissions, screens,
   inventory, entities, resources, actions, schemas, availability, and
   fingerprints.
3. Runtime capability graph: internal graph of discovered resources,
   operations, handles, schemas, availability, events, fingerprints, and source
   evidence.
4. Fabric discovery and projection: client-thread probes inspect the real
   Minecraft runtime and project Craftless-owned graph nodes.
5. Generic invocation: `POST /clients/{id}:run` dispatches graph-projected
   operations through private client-thread adapters.
6. Live streams: Server-Sent Events carry lifecycle, runtime, capability, and
   gameplay observations; JSON-RPC-style HTTP calls handle invoke, subscribe,
   unsubscribe, and query control.
7. Adaptive consumers: the CLI, agent skills, exported tools, and future
   generated clients fetch live specs instead of shipping gameplay catalogs.

The HMC bridge code is lifecycle/launch evidence only and is not a gameplay adapter.
It is not the product path for chat, movement, inventory, world,
entity, screen, or building behavior. Gameplay breadth belongs in the Fabric
runtime capability graph and generated per-client OpenAPI.

## Use The Generated API

Start the supervisor:

```sh
craftless daemon start --port 8080 --workspace .craftless
```

Set the API URL:

```sh
CRAFTLESS=http://127.0.0.1:8080
```

Create a daemon-managed client session. This API-first form derives an offline
profile from `id` and defaults to a non-visible, muted presentation:

```sh
curl -sS "$CRAFTLESS/clients" \
  -H 'content-type: application/json' \
  -d '{
    "id": "alice",
    "version": "latest-release",
    "loader": "FABRIC"
  }'
```

Set `profile` explicitly when a specific offline name is needed. Set
`presentation` to `{ "window": "VISIBLE", "audio": "DEFAULT" }` only when a
visible, normal-audio client is desired. `presentation.window` is lifecycle
intent; generated per-client OpenAPI remains the gameplay and runtime
capability authority.

`POST /clients` launches a new daemon-managed real Minecraft Java client
process. It is not a selector, retry, or reuse operation. In an existing daemon
or workspace, call `GET /clients` and `GET /clients/{id}` first, then reuse a
suitable client or stop an abandoned one with `POST /clients/{id}:stop`.
Creating fresh timestamped ids for retries leaves multiple Minecraft clients
running.

Connect it to a server:

```sh
curl -sS "$CRAFTLESS/clients/alice:connect" \
  -H 'content-type: application/json' \
  -d '{"host":"localhost","port":25565}'
```

Discover the live contract before invoking actions:

```sh
curl -sS "$CRAFTLESS/clients/alice/openapi.json"
curl -sS "$CRAFTLESS/clients/alice/actions"
curl -sS "$CRAFTLESS/clients/alice/resources"
```

Invoke an action only after the generated live API advertises it:

```sh
curl -sS "$CRAFTLESS/clients/alice:run" \
  -H 'content-type: application/json' \
  -d '{"action":"player.chat","args":{"message":"hello from Craftless"}}'
```

Stream live events:

```sh
curl -N "$CRAFTLESS/clients/alice/events:stream"
```

Use the adaptive CLI against the same generated metadata:

```sh
craftless clients alice actions --api "$CRAFTLESS"
craftless clients alice resources --api "$CRAFTLESS"
craftless clients alice run player.chat --api "$CRAFTLESS" --arg message="hello from Craftless"
craftless clients alice actions --help --api "$CRAFTLESS"
craftless clients alice player chat --help --api "$CRAFTLESS"
```

Generated aliases such as `craftless clients alice player chat` are derived
from the live OpenAPI document. They are not source-maintained gameplay
commands.

## Two-Client Co-Play Bootstrap

For a simple "let's play" setup, keep lifecycle and gameplay separate:

```sh
craftless daemon start --port 8080 --workspace .craftless
export CRAFTLESS=http://127.0.0.1:8080

craftless clients create bot --version latest-release --loader fabric --api "$CRAFTLESS"
craftless clients bot connect --host localhost --port 25565 --api "$CRAFTLESS"
```

If Craftless should also manage the human-facing client window, create it
explicitly as visible and normal-audio:

```sh
craftless clients create robin --version latest-release --loader fabric \
  --offline-name Robin --visible --audio default --api "$CRAFTLESS"
craftless clients robin connect --host localhost --port 25565 --api "$CRAFTLESS"
```

If the human is joining with their own launcher, skip the `robin` client and
let the bot coordinate through Minecraft chat and public Craftless events.
Before the bot acts, fetch `GET /clients/bot/openapi.json` and use only the
generated `x-craftless-actions`, schemas, handles, and events as gameplay
authority.

## Agent Usage

Agents should behave like external Craftless users:

1. Fetch the supervisor spec with `GET /openapi.json`.
2. List existing clients with `GET /clients`; select or stop abandoned clients
   before calling `POST /clients`.
3. Fetch `GET /clients/{id}/openapi.json`.
4. Treat `x-craftless-actions`, `x-craftless-resources`, route metadata,
   schemas, availability, and fingerprints in that document as the authority.
5. Use `/clients/{id}/actions` and `/clients/{id}/resources` as projection
   evidence, not as an independent catalog.
6. Subscribe to `GET /clients/{id}/events:stream` before state-changing work.
7. Invoke only advertised actions through `POST /clients/{id}:run`, generated
   alias routes, or the adaptive CLI.

The repo-local skill
`.agents/skills/craftless-public-gameplay-agent/SKILL.md` captures this
workflow for agents. Final gameplay evidence must include the connected
OpenAPI document, action/resource projections, SSE or JSONL event capture,
public action log, and public inventory/world/entity observations. Do not use
server-provisioned inventory, driver internals, Fabric internals, or
hard-coded scenario actions as product proof.

## Cache And Runtime Preparation

Craftless can prepare repeatable launch/cache state before running clients:

```sh
craftless cache prepare \
  --mc latest-release \
  --loader fabric \
  --workspace .craftless
```

Cache preparation resolves Minecraft metadata, the selected client jar,
libraries, asset objects, native libraries, Fabric loader metadata, Java
runtime requirements, launch arguments, classpath handles, logging metadata,
asset indexes, and file layout inside a Craftless-owned workspace.
Use `latest-release` or `latest-snapshot` for moving Mojang aliases, or a
concrete Minecraft version id when a run must be pinned.

Java selection is a product runtime concern, separate from repository build
tooling. Craftless can evaluate configured, managed, mise, and system runtime
providers against Minecraft version requirements.

## Current Verification

Verified surfaces:

- stable supervisor OpenAPI at `GET /openapi.json`;
- generated per-client OpenAPI at `GET /clients/{id}/openapi.json`;
- graph-projected actions, resources, handles, schemas, availability,
  fingerprints, generated aliases, and event metadata;
- generic action invocation through `POST /clients/{id}:run`;
- SSE event streams plus JSON-RPC-style HTTP control/query calls;
- adaptive CLI discovery, generated help, generated aliases, action
  invocation, event watching, tools export, and OpenAPI cache revalidation;
- cache preparation for Minecraft/Fabric metadata, libraries, assets, natives,
  Java runtime files, launch arguments, classpaths, logging, and instance file
  layout;
- release workflow, install script, Docker runtime image, packaged CLI
  distribution with the Fabric driver mod, and reusable GitHub Action;
- packaged latest/current `26.2` and representative older `1.20.6` product
  lanes with generated OpenAPI/projection/SSE/JSON-RPC/CLI invocation
  evidence;
- bridge lifecycle-only behavior after removal of bridge-owned gameplay
  descriptors and helpers.

Historical final gameplay evidence uses generated public APIs only. The
external public-agent path fetched generated OpenAPI/actions/resources,
consumed SSE evidence, collected materials, crafted and equipped a
`Wooden Sword`, found Cows through `entity.query`, killed a Cow through
`entity.attack`, and observed `Raw Beef`, `Leather`, and the Cow with
`alive:false`. That run completed without server-provisioned inventory or
static survival macro evidence. The active completion checklist is the current
source of truth for closed gates and any new post-completion usability slices.

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

## Roadmap

The source of truth is:

- [docs/project-completion-checklist.md](docs/project-completion-checklist.md)
- [docs/roadmap.md](docs/roadmap.md)
- [docs/final-gameplay-runbook.md](docs/final-gameplay-runbook.md)

Post-completion work focuses on:

- keeping the verified latest/current and representative older lanes current;
- broadening Fabric discovery/projection across more Minecraft versions, mods,
  registries, screens, world/entity/inventory resources, permissions, and
  installed runtime affordances;
- turning future gameplay breadth into generic discovery/projection instead of
  transitional bootstrap bindings;
- strengthening navigation/pathfinding and building evidence through generated
  public API/CLI/SSE only.

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

`ci-craftless-smoke` runs the packaged CLI distribution, starts
`craftless daemon start`, and probes the live supervisor API.

Release Please opens or updates the release PR on pushes to `main`, on manual
dispatch, and on the weekly scheduled check when releasable changes exist since
the latest `v*` release. Merging that PR creates the next `vX.Y.Z` tag; the
tag-driven `release` workflow then builds the CLI archives, checksums, Docker
runtime image, and GitHub release notes.

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

The final gameplay task is a completion gate, not a normal CI check.

## Docs

- [Product positioning](docs/product-positioning.md)
- [Client file management](docs/client-file-management.md)
- [Agent skills](docs/agent-skills.md)
- [Roadmap](docs/roadmap.md)
- [Final gameplay runbook](docs/final-gameplay-runbook.md)

Historical specs, plans, and evidence live under `docs/superpowers/`.
