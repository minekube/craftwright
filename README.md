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
| Latest and older lanes | Historical probes exist; active runtime code uses provider-backed lanes plus generic unsupported fallback until runnable support lands |
| Final gameplay evidence | Public API/CLI path completed survival evidence without server-provisioned inventory |
| Completion | Still active; see `docs/project-completion-checklist.md` |

## Quickstart

Install the released CLI on Linux or macOS:

```sh
curl -fsSL https://raw.githubusercontent.com/minekube/craftless/main/install.sh | sh
craftless server start --port 8080 --workspace .craftless
```

Install a specific release:

```sh
CRAFTLESS_VERSION=v0.1.1 \
CRAFTLESS_INSTALL_DIR="$HOME/.local/bin" \
sh -c "$(curl -fsSL https://raw.githubusercontent.com/minekube/craftless/main/install.sh)"
```

The installer writes the launcher symlink to `$HOME/.local/bin` by default.
Set `CRAFTLESS_INSTALL_DIR` to use another directory.

The installed CLI distribution carries
`mods/craftless-driver-fabric.jar`. `craftless server start` auto-discovers
that packaged driver mod unless `CRAFTLESS_FABRIC_DRIVER_MOD` is set
explicitly, so daemon-managed Fabric clients do not need extra driver-mod
configuration.

Check the CLI:

```sh
craftless --help
```

The CLI has a small stable core for daemon lifecycle, client lifecycle, cache,
runtime, discovery, output, and generic invocation. Gameplay commands and help
come from each live client's generated OpenAPI document.

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
      - uses: minekube/craftless/.github/actions/setup-craftless@v0.1.1
        id: craftless
        with:
          start: "true"
          workspace: .craftless

      - run: curl -fsSL "${{ steps.craftless.outputs.api-url }}/openapi.json"
```

The action installs the released CLI distribution and can optionally start
`craftless server start` for the job. Fabric driver-mod discovery follows the
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
runtime requirements, launch arguments, classpath handles, logging metadata,
asset indexes, and file layout inside a Craftless-owned workspace.

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
- bridge lifecycle-only behavior after removal of bridge-owned gameplay
  descriptors and helpers.

Current final gameplay evidence uses generated public APIs only. The external
public-agent path fetched generated OpenAPI/actions/resources, consumed SSE
evidence, collected materials, crafted and equipped a `Wooden Sword`, found
Cows through `entity.query`, killed a Cow through `entity.attack`, and observed
`Raw Beef`, `Leather`, and the Cow with `alive:false`. The run completed
without server-provisioned inventory or static survival macro evidence.

Still open before the broader project can be called complete:

- broaden Fabric discovery/projection across more Minecraft versions, mods,
  registries, screens, world/entity/inventory resources, permissions, and
  installed runtime affordances;
- turn future gameplay breadth into generic discovery/projection instead of
  transitional bootstrap bindings;
- strengthen navigation/pathfinding and building evidence through generated
  public API/CLI/SSE only;
- land real additional Fabric client lanes only when cache preparation,
  Java/runtime selection, loader/API resolution, launch metadata, compatibility
  matrix, and smoke evidence prove them;
- keep the completion audit current across CI, release, Docker, installer,
  compatibility, docs, and public gameplay gates.

Craftless is not considered complete until the active checklist proves every
remaining generic-discovery, multi-version, transport, CLI, docs, and gameplay
gate with current Codex-verifiable evidence.

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

Next work focuses on:

- completing the generic runtime capability graph so new public gameplay
  breadth comes from discovery/projection, not descriptor/binding catalog
  growth;
- landing real multi-version support with verified cache, Java, Fabric
  Loader/API, launch metadata, compatibility probes, and smoke evidence;
- improving pathfinding, block placement/building, longer gameplay, and
  failure recovery through public OpenAPI/CLI/SSE only;
- keeping install, Docker, GitHub Actions, release checks, and agent skill docs
  easy for external users;
- running the final completion audit only after current generic-discovery,
  multi-version, transport, CLI, docs, and gameplay gates are reverified.

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

The final gameplay task is a completion gate, not a normal CI check.

## Docs

- [Product positioning](docs/product-positioning.md)
- [Client file management](docs/client-file-management.md)
- [Agent skills](docs/agent-skills.md)
- [Roadmap](docs/roadmap.md)
- [Final gameplay runbook](docs/final-gameplay-runbook.md)

Historical specs, plans, and evidence live under `docs/superpowers/`.
