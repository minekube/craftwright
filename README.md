# Craftless

![Craftless hero: generated local APIs controlling real Minecraft Java clients](docs/assets/craftless-hero.png)

Craftless is automation infrastructure for real Minecraft Java clients,
headless or visible, with generated local APIs for agents, tools, tests, and
CI.

It runs a real Minecraft Java client, loads a small in-client driver, discovers
what that exact runtime can do, and exposes local OpenAPI contracts for
automation. The available gameplay surface is generated from the live client
runtime instead of maintained as a hand-written SDK or a fixed command catalog.

If Browserless gives automation systems a real browser behind an API,
Craftless is the same kind of infrastructure for Minecraft Java clients.

## Why Craftless

Minecraft automation usually starts in one of two places:

- protocol bots that speak Minecraft without running the real client;
- in-game mods or pathfinders that are powerful, but not packaged as a clean
  local API for external agents and CI.

Craftless takes a different path. It keeps the real client as the source of
truth and adds a thin control plane around it:

1. launch or attach to a Minecraft Java client;
2. load the Craftless driver;
3. discover the runtime capability graph from the client, server, loader, mods,
   registries, screens, inventory, world, entities, permissions, and events;
4. generate per-client OpenAPI/action/resource contracts;
5. let agents and tools discover, invoke, stream, and verify behavior through
   those contracts.

The durable API is Craftless-owned. Fabric, Yarn, Minecraft internals, launcher
details, and mod implementation names are inputs to discovery, not public
contracts.

## Quickstart

Install the released CLI on Linux or macOS:

```sh
curl -fsSL https://raw.githubusercontent.com/minekube/craftless/main/install.sh | sh
craftless server start --port 8080 --workspace .craftless
```

Install a specific release:

```sh
CRAFTLESS_VERSION=v0.1.0 \
CRAFTLESS_INSTALL_DIR="$HOME/.local/bin" \
sh -c "$(curl -fsSL https://raw.githubusercontent.com/minekube/craftless/main/install.sh)"
```

The installer writes the launcher symlink to `$HOME/.local/bin` by default.
Set `CRAFTLESS_INSTALL_DIR` to use another directory.

Check the CLI:

```sh
craftless --help
```

The CLI has a small stable core for lifecycle, cache, runtime, and server
management. Gameplay commands are loaded from each live client's generated
OpenAPI document.

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
      - uses: minekube/craftless/.github/actions/setup-craftless@v0.1.0
        id: craftless
        with:
          start: "true"
          workspace: .craftless

      - run: curl -fsSL "${{ steps.craftless.outputs.api-url }}/openapi.json"
```

The action installs the released CLI and can optionally start
`craftless server start` for the job.

## API Model

Craftless has two API layers.

`GET /openapi.json` is the stable supervisor API. It owns lifecycle,
workspaces, clients, cache preparation, Java runtime resolution, files, events,
and discovery of per-client API documents.

`GET /clients/{id}/openapi.json` is generated for one live client. It reflects
that client's Minecraft version, loader, driver runtime, mappings, installed
mods, registries, server features, permissions, screens, inventory, entities,
resources, handles, actions, schemas, availability, and fingerprints.

Convenience projections come from that same generated live contract:

- `GET /clients/{id}/actions`
- `GET /clients/{id}/resources`
- `GET /clients/{id}/events`
- `GET /clients/{id}/events:stream`
- `POST /clients/{id}:run`

Agents should discover first, then invoke. Do not assume a static action list.

## Example: Discover Then Invoke

Start the supervisor:

```sh
craftless server start --port 8080 --workspace .craftless
```

Set the API URL:

```sh
CRAFTLESS=http://127.0.0.1:8080
```

Create a daemon-managed client session:

```sh
curl -sS "$CRAFTLESS/clients" \
  -H 'content-type: application/json' \
  -d '{
    "id": "alice",
    "version": "1.21.6",
    "loader": "FABRIC",
    "profile": { "kind": "OFFLINE", "name": "Alice" }
  }'
```

Connect it to a server:

```sh
curl -sS "$CRAFTLESS/clients/alice:connect" \
  -H 'content-type: application/json' \
  -d '{"host":"localhost","port":25565}'
```

Discover the generated live API:

```sh
curl -sS "$CRAFTLESS/clients/alice/openapi.json"
curl -sS "$CRAFTLESS/clients/alice/actions"
curl -sS "$CRAFTLESS/clients/alice/resources"
```

Invoke an action by id through the generic endpoint:

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
```

Generated aliases such as `craftless clients alice player chat` are derived
from the live OpenAPI document. They are not source-maintained gameplay
commands.

## Cache And Runtime Preparation

Craftless can prepare repeatable launch/cache state before running clients:

```sh
craftless cache prepare \
  --mc 1.21.6 \
  --loader fabric \
  --loader-version 0.19.3 \
  --workspace .craftless
```

Cache preparation resolves Minecraft metadata, the selected client jar,
libraries, asset objects, native libraries, Fabric loader metadata, Java
runtime requirements, launch arguments, classpath handles, and file layout
inside a Craftless-owned workspace.

Java selection is a product runtime concern, separate from repository build
tooling. Craftless can evaluate configured, managed, mise, and system runtime
providers against Minecraft version requirements.

## Architecture

![Craftless architecture: generated APIs and control plane driving attached real Minecraft Java clients](docs/assets/craftless-architecture-generated.png)

The internal shape is deliberately layered:

- supervisor API: stable lifecycle, files, cache, runtimes, events, and
  per-client spec discovery;
- runtime capability graph: internal graph of resources, operations, handles,
  schemas, availability, events, fingerprints, and source evidence;
- Fabric discovery/projection: runtime probes inspect the real client and
  project Craftless-owned graph nodes;
- live per-client OpenAPI: generated machine contract for one running client;
- generic invocation: `POST /clients/{id}:run` dispatches graph-projected
  operations through internal client-thread adapters;
- SSE and JSON-RPC-style control: event streams and structured control calls
  for agents and tools;
- adaptive consumers: CLI, agent skills, and future generated clients fetch
  the live specs instead of shipping gameplay catalogs.

The driver contract stays small: lifecycle, runtime metadata, events, action
discovery, and generic invocation. Public gameplay breadth belongs in generated
per-client OpenAPI, not in static driver methods.

## Current Status

Verified now:

- Kotlin/JVM project under `com.minekube.craftless`, with all repository
  tooling pinned through `mise`.
- Ktor supervisor API, Ktor Client based CLI/runtime helpers, and no
  product-side OkHttp, Java `HttpClient`, `com.sun.net.httpserver`, npm, yarn,
  pnpm, or Node workflows.
- Released `craftless` CLI, install script, Docker runtime image, and reusable
  GitHub Action.
- Stable supervisor OpenAPI at `GET /openapi.json`.
- Generated per-client OpenAPI at `GET /clients/{id}/openapi.json`.
- Runtime-graph-projected actions, resources, handles, schemas,
  availability, fingerprints, and event metadata.
- Generic action invocation through `POST /clients/{id}:run`.
- SSE event streams plus JSON-RPC-style HTTP control/query calls.
- Adaptive CLI discovery, generated help, generated aliases, action
  invocation, event watching, tool export, and OpenAPI cache revalidation.
- Cache preparation for Minecraft/Fabric metadata, libraries, assets, natives,
  Java runtime files, launch arguments, classpaths, and instance file layout.
- Version-aware runtime metadata and compatibility probes. The current
  compiled Fabric client lane is verified; latest `26.2` and representative
  older `1.20.6` lanes are resolved from live Mojang metadata and currently
  reported as explicit unsupported Fabric client lanes with machine-readable
  reasons, not as supported client breadth.
- Current final gameplay evidence uses generated public APIs only: the
  external public-agent path fetched generated OpenAPI/actions/resources,
  consumed SSE evidence, collected materials, crafted and equipped a
  `Wooden Sword`, found Cows through `entity.query`, killed a Cow through
  `entity.attack`, and observed `Raw Beef`, `Leather`, and the Cow with
  `alive:false`. The run completed without server-provisioned inventory or
  static survival macro evidence.

Still open before the broader project can be called complete:

- Broaden Fabric discovery/projection across more Minecraft versions, mods,
  registries, screens, world/entity/inventory resources, permissions, and
  installed runtime affordances.
- Turn more gameplay breadth into generic discovery/projection instead of
  relying on transitional bootstrap bindings.
- Strengthen navigation/pathfinding and building evidence through the same
  generated public API path.
- Finish the completion audit across CI, release, Docker, installer,
  compatibility, docs, and public API/CLI gameplay gates.

Craftless is not considered complete until the active checklist proves every
remaining generic-discovery, multi-version, transport, CLI, docs, and gameplay
gate with current Codex-verifiable evidence.

## Comparison

| Area | Craftless | Mineflayer | Baritone |
| --- | --- | --- | --- |
| Runs the real Minecraft Java client | Yes | No, protocol bot | Yes |
| Local OpenAPI control plane | Yes | No | No |
| Live per-client generated API | Yes | No | No |
| Runtime discovery from version/mod/server state | Core design | Protocol data oriented | In-client pathing state |
| External agent and CI usage | Primary use case | Script/library use | Mod/API use |
| Pathfinding maturity | Growing | Mature bot movement ecosystem | Mature pathfinding |
| Public gameplay surface | Generated Craftless actions/resources | Library API | Java/mod commands/API |
| Best fit | Real-client automation infrastructure | Fast protocol bot scripts | In-game navigation/pathing |

Craftless is not trying to replace Mineflayer or Baritone. The goal is a
Browserless-style runtime for real Minecraft clients where agents can discover
the live API and operate through stable local contracts.

## Roadmap

The active roadmap is tracked in:

- [docs/roadmap.md](docs/roadmap.md)
- [docs/project-completion-checklist.md](docs/project-completion-checklist.md)
- [docs/final-gameplay-runbook.md](docs/final-gameplay-runbook.md)

Next work focuses on:

- making the runtime capability graph more complete so new public gameplay
  breadth comes from discovery/projection, not descriptor/binding catalog
  growth;
- landing real additional Fabric client lanes only when cache preparation,
  Java/runtime selection, loader/API resolution, launch metadata, and smoke
  evidence prove them;
- strengthening navigation/pathfinding, block placement/building, and
  longer-running gameplay through public OpenAPI/CLI/SSE only;
- keeping install, Docker, GitHub Actions, release checks, and agent skill docs
  easy for external users;
- running the final completion audit and keeping the active checklist as the
  source of truth.

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

The final gameplay task is the completion gate, not a normal CI check.

## Docs

- [Product positioning](docs/product-positioning.md)
- [Client file management](docs/client-file-management.md)
- [Agent skills](docs/agent-skills.md)
- [Roadmap](docs/roadmap.md)
- [Final gameplay runbook](docs/final-gameplay-runbook.md)

Historical specs, plans, and evidence live under `docs/superpowers/`.
