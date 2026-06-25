# Client Management Decisions

Date: 2026-06-25

## Purpose

This note records the client-management decisions made after inspecting
PrismLauncher, HeadlessMC, and HMC-Specifics. It narrows the path for the next
implementation work: get a real Minecraft client under API control first, while
avoiding a dependency choice that makes CI/headless operation harder.

## Decision Summary

Craftwright should build its own JVM/Kotlin client-management core for Phase 1.

PrismLauncher should be treated as a high-value reference and a later optional
desktop integration, not as the Phase 1 runtime dependency.

HeadlessMC and HMC-Specifics should be used for the first real-client evidence
spike where that is faster than building the Fabric driver from scratch, but
their console text should not become the final public API.

## PrismLauncher Decision

PrismLauncher is excellent prior art for user-facing Minecraft instance
management:

- instance list and IDs;
- staging and commit during instance creation;
- per-instance settings with global overrides;
- Java detection and automatic Java selection;
- managed packs;
- component graph stored in `mmc-pack.json`;
- loader conflict modelling for Fabric, Forge, NeoForge, Quilt, and LiteLoader;
- offline accounts;
- join-on-launch settings;
- launch pipeline steps for folders, metadata, Java, libraries, assets, natives,
  mod scanning, and launch.

It should not be Craftwright's Phase 1 core dependency:

- PrismLauncher is a C++/Qt desktop application, not a small launcher library.
- The project is GPL-3.0-only at the application level.
- The available automation surface is CLI launch/import behavior, not a stable
  plugin or API server.
- It does not solve the server-side/headless Minecraft client problem by
  itself.

Craftwright should learn the model and reimplement the needed subset in
Kotlin/JVM.

## Prism-Compatible Later Track

After the real-client API path works, add an optional Prism adapter:

```text
mcw prism instances
mcw prism inspect INSTANCE
mcw prism install-driver INSTANCE
mcw prism launch INSTANCE --offline Bot1 --server localhost:25566
mcw prism import INSTANCE
```

The adapter may read Prism instance files such as `instance.cfg`,
`mmc-pack.json`, `patches/*.json`, and mods folders. It may launch Prism through
its existing CLI flags:

```text
--launch INSTANCE
--server ADDRESS
--profile PROFILE
--offline NAME
```

This gives desktop users a polished UI for managing instances, mods, packs, and
accounts while keeping Craftwright's CI/headless path independent.

If PrismLauncher ever accepts an upstream local automation API, Craftwright can
integrate with it. Until then, no Phase 1 work should depend on Prism internals
or a fork.

## Headless And CI Decision

The robust server/CI path must be Craftwright-owned.

PrismLauncher can help desktop users manage instances, but Craftwright needs to
launch real clients in environments where there may be no visible desktop
window. That means the Phase 1 supervisor must support:

- offline profiles;
- deterministic version/loader/cache state;
- automatic driver installation;
- virtual-display or HeadlessMC-style LWJGL/headless launch strategies;
- local server fixtures;
- artifact capture;
- process cleanup.

HeadlessMC is the better immediate reference for this path because it already
targets headless and CI usage.

## First Real Proof Of Concept Result

A throwaway PoC under `/tmp/craftwright-real-client-poc` proved the fastest
real-client loop:

1. Started a local Paper 1.21.4 server in offline mode on port `25567`.
2. Started a real Minecraft Java client through HeadlessMC.
3. Launched Fabric 1.21.4 with HMC-Specifics 2.4.0.
4. Exposed a small local HTTP API wrapper on `127.0.0.1:18080`.
5. Called the API to connect the real client to the local server.
6. Verified the server saw the joining player.

Observed server evidence:

```text
CwApiBot joined the game
CwApiBot[/127.0.0.1:...] logged in with entity id ...
<CwApiBot> api action after reconnect
CwApiBot has the following entity data: [-5.5d, -60.0d, 10.914621337840606d]
```

The wrapper exposed Craftwright-shaped routes such as:

- `POST /start`;
- `POST /launch`;
- `POST /connect`;
- `GET /events`;
- `POST /clients/default/player/sendChat`;
- `POST /clients/default/actions/jump`;
- `POST /clients/default/actions/move`;
- `POST /clients/default/perception/render-text`;
- `POST /clients/default/ui/dump`;
- `POST /clients/default/ui/click`;
- `POST /clients/default/input/keys`.

This must be labelled as a bridge PoC. The API surface was Craftwright-shaped,
but the implementation still drove HMC-Specifics commands over stdin.

### Bridge PoC Findings

The bridge is good enough to prove that a real Minecraft client can be launched,
joined to a server, and controlled from a local API. It is not good enough to be
the final automation engine.

What worked:

- offline profile launch with a real Minecraft client;
- Fabric 1.21.4 and HMC-Specifics initialization;
- joining an offline Paper server;
- chat through the client;
- rendered text capture from the client;
- UI inspection and button click for the first-run accessibility screen;
- keyboard-driven movement after the client was in-game.

What was fragile:

- Minecraft usernames longer than 16 characters fail during login packet
  encoding;
- first-run screens and title screens can swallow keyboard movement;
- movement through `key w` and `key space` is stateful and screen-dependent;
- "visible blocks" are not exposed as structured world data by HMC-Specifics;
- the API wrapper had to infer state from text logs and server observations.

Product implication: HeadlessMC/HMC-Specifics should remain a launcher and
comparison bridge. Craftwright's real product driver should be an in-client
Fabric mod with structured APIs for movement, look direction, raycasts, block
inspection, entities, inventory, chat, and lifecycle events.

## API Model Decision

Craftwright should separate persistent setup objects from live clients:

- `Version`: Minecraft version plus release metadata.
- `Loader`: Fabric, NeoForge, Forge, Quilt, Vanilla.
- `Profile`: offline or authenticated identity.
- `Instance`: installed client setup with version, loader, mods, driver, Java,
  assets, and config.
- `Client`: running Minecraft process created from an instance/profile.
- `Session API`: local API exposed by a running client.

This matches Prism's strongest modelling idea while preserving a CI-friendly
runtime boundary.

## Phase 1 Priority

Priority order:

1. Real client automation.
2. Headless/CI operation.
3. Generated OpenAPI session API.
4. Kotlin/JVM client manager.
5. Playwright/Vitest fixtures.
6. Prism compatibility/import/launch adapter.
7. Optional Prism upstream API/plugin exploration.

Do not spend Phase 1 building a polished Prism integration before a real client
can join a local server through Craftwright automation.

## Done Definition

These decisions are implemented when:

- Phase 1 can run without PrismLauncher installed;
- the first real-client PoC does not use Mineflayer or a protocol-only bot;
- Prism is documented as reference and optional adapter, not core dependency;
- HeadlessMC/HMC-Specifics are used only as evidence or bridge implementation;
- the final target remains a Craftwright-owned in-client API and supervisor.

## References

- JVM-first rewrite:
  `docs/superpowers/specs/2026-06-25-jvm-first-rewrite-design.md`
- Generated client API:
  `docs/superpowers/specs/2026-06-25-generated-client-api-design.md`
- PrismLauncher:
  https://github.com/PrismLauncher/PrismLauncher
- HeadlessMC:
  https://github.com/headlesshq/headlessmc
- HMC-Specifics:
  https://github.com/headlesshq/hmc-specifics
