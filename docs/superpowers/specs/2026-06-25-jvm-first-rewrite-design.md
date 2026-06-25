# JVM-First Rewrite Design

Date: 2026-06-25

## Purpose

This spec defines Craftwright as a JVM-first Minecraft automation platform.

Craftwright still has the same product goal: automate real Minecraft Java
clients for tests, agents, and CI through a short CLI, typed fixtures, and stable
machine protocols. The architectural change is where the real automation core
lives. The core must run on the JVM, close to Minecraft classes, modloaders,
Mixins, mappings, ticks, GUI state, player state, and network state.

Kotlin/JVM is the only checked-in implementation path.

## Decision

Craftwright should be rebuilt as a Kotlin-first JVM project with Java reserved
for low-level Minecraft integration.

- Kotlin is the default language for product logic.
- Java is the default language for Mixins, accessors, and bytecode-sensitive
  Minecraft-version glue.
- Playwright remains the first JavaScript/TypeScript integration surface;
  generated external client code is postponed until the JVM protocol and
  generated API are steadier.
- HeadlessMC, HMC-Specifics, and MC-Runtime-Test are primary prior art and
  implementation references, but Craftwright should not expose their text
  console as its public automation contract.

## Why The Core Must Be JVM-First

External process control through HeadlessMC and HMC-Specifics is useful bridge
evidence, but it is not a strong final architecture. The durable control path
needs code running in the Minecraft JVM.

Source inspection of HeadlessMC and related projects showed:

- HeadlessMC has `-inmemory`, which launches Minecraft in the same JVM.
- HeadlessMC runtime wraps the real Minecraft main class and initializes a
  command runtime before calling Minecraft.
- HMC-Specifics is the actual in-client automation layer. It uses versioned
  Mixins against `net.minecraft.client.Minecraft`, `LocalPlayer`, screens,
  command dispatchers, packet listeners, and connection code.
- MC-Runtime-Test also uses client-side Mixins and tick hooks to create worlds,
  wait for chunks, run GameTests, and stop Minecraft in CI.

The pattern is clear: real control does not come from an external process
watching logs. It comes from code running in the Minecraft JVM with direct access
to Minecraft types.

## Language Model

### Kotlin Product Core

Use Kotlin for modules that benefit from compact, typed product logic:

- CLI command implementation.
- Configuration loading and precedence.
- Version/profile models.
- Dependency cache metadata.
- Launcher orchestration.
- Client lifecycle state machines.
- JSON-RPC and WebSocket protocol handling.
- Event models.
- Scenario runner.
- Artifact collection.
- Test fixtures and fake engines.

Kotlin gives the project data classes, sealed classes, null-safety, extension
functions, coroutines, and concise test DSLs. These are valuable for lifecycle
and protocol code.

### Java Minecraft Glue

Use Java by default for:

- Mixin classes.
- Mixin accessor interfaces.
- `@Shadow`, `@Inject`, `@Redirect`, and related bytecode-sensitive classes.
- Version-specific adapter implementations where exact Java signatures matter.
- Small bridge types that must be easy to compare with HeadlessMC,
  HMC-Specifics, Fabric, NeoForge, and Minecraft examples.

Kotlin can be used in Minecraft modules when it is clearly simpler, but Java is
the safe default for low-level integration because Mixins and Minecraft mappings
are already Java-centric.

### TypeScript External UX

TypeScript should not be the core. It cannot naturally provide Mixins, direct
Minecraft class access, Fabric lifecycle integration, or tick-thread execution.

TypeScript should instead provide:

- Playwright Test fixtures.
- Vitest unit helpers for the protocol.
- Agent-friendly API wrappers.
- Optional generated types from the JVM protocol schema.

## Target Repository Shape

The rewrite should use a Gradle multi-project build.

```text
craftwright/
  settings.gradle.kts
  build.gradle.kts

  cli/
    Kotlin CLI entrypoint for mcw.

  launcher/
    Kotlin launcher, cache, Java runtime, version, asset, and process logic.

  protocol/
    Kotlin protocol data classes, JSON codecs, event model, and schemas.

  daemon/
    Kotlin JSON-RPC and WebSocket daemon transports.

  scenario/
    Kotlin YAML scenario parser and runner.

  driver-api/
    Stable JVM interfaces exposed by the in-client driver. The repository now
    starts this boundary with `DriverSession` and `FakeDriverSession`.

  driver-runtime/
    Runtime adapter layer for `DriverSession` implementations. The repository
    now includes `BackendDriverSession`, a pluggable `DriverBackend`, and an HMC
    bridge adapter so daemon routes are no longer hard-wired to fake state.

  driver-fabric/
    Target Fabric Loom module. It should contain common runtime code plus
    internal version-aware bindings, reflection/mapping probes, metadata, mixin
    config, and gateway-backed runtime behavior.

  driver-fabric-1_21_6/
    Current transitional module name while the first Fabric driver slice is
    being consolidated into `driver-fabric`.

  testkit/
    Fake clients, fake servers, process fixtures, and integration helpers.

  playwright/
    Playwright fixtures and matchers.
```

Module names may be refined during implementation, but the boundary should
remain: product logic outside Minecraft, version-sensitive binding code behind
the Fabric driver contract, and helper tests outside the JVM core.

## Runtime Architecture

Craftwright has four layers.

### 1. JVM Supervisor

The supervisor owns installed versions, Java runtimes, profiles, modloader
setup, launch arguments, process lifecycle, logs, artifacts, and crash cleanup.

It may learn heavily from HeadlessMC and PrismLauncher. It should not initially
try to support every loader and Minecraft version. Start with one Fabric version
that is known to work in offline mode.

### 2. In-Client Driver

The driver runs inside the Minecraft client JVM. It is the real automation
engine.

The supervisor and daemon should talk to this layer through `driver-api/` and
the `driver-runtime/` backend adapter. Current default daemon state still uses
`FakeDriverSession`, but `ClientSessionService` can now be constructed with an
injected runtime driver factory. The Fabric module now has a real
client-thread gateway for connect, chat, command, stop, and generated action
invocation such as `player.move` and `player.chat`. Player state, connection
state, and position should come back through generated actions/resources rather
than static driver methods or daemon routes.

Responsibilities:

- Observe title screen, connection state, player state, chat, disconnect
  reasons, current screen, inventory screens, and rendered text where possible.
- Execute commands on the Minecraft client thread.
- Connect and disconnect through real Minecraft APIs.
- Invoke chat and command actions through real `LocalPlayer`/connection APIs.
- Expose GUI and input actions through stable Craftwright abstractions.
- Set player movement intent through generated/discovered actions. The current
  `player.move` action reaches the Fabric gateway and writes
  movement intent to `ClientPlayerEntity.input`.
- Set look direction directly through yaw and pitch.
- Expose player position, velocity, health, game mode, selected hotbar slot, and
  connection state as structured data.
- Expose perception primitives such as raycast target, nearby blocks, nearby
  entities, fluid state, light level, biome, inventory, and current screen.
- Execute interaction primitives such as use, attack, pick block, hotbar select,
  drop item, inventory click, and screen click.
- Emit structured events without requiring external log parsing.

The HeadlessMC/HMC-Specifics bridge PoC proved that keyboard-driven movement can
work against a real client, but also showed why it is not the final product
shape. Simulated keys are sensitive to first-run screens, title screens, focus,
and client state. The Craftwright Fabric driver should operate at the
Minecraft-client API level instead of relying on `key w`, `key space`, or parsed
console text.

### 3. Local Protocol

The supervisor and in-client driver communicate through a structured protocol.
The first transport should be chosen by implementation evidence. Acceptable
first transports:

- localhost WebSocket opened by the driver;
- localhost TCP JSON-RPC;
- stdio or named pipe if easier for same-process control;
- an in-memory bridge for embedded/same-JVM launches.

The public contract should be JSON-RPC-style request/response plus event
streams. The protocol must be independent of HeadlessMC console text.

The generated local client API direction is specified separately in
`docs/superpowers/specs/2026-06-25-generated-client-api-design.md`. That spec
refines this protocol layer toward a per-client OpenAPI surface generated from
the running Minecraft client, generated action/resource routes below
`/clients/{id}`, opaque handle schemas, and Craftwright-owned runtime metadata.

### 4. External UX

The same protocol powers:

- `mcw` CLI.
- `mcw daemon`.
- Playwright fixtures.
- future MCP/agent tools.

CLI remains canonical for human and script usage, but it should not duplicate
the API as static Kotlin commands. Keep a small static core for daemon startup,
configuration, auth, output modes, and generic dispatch. Load `/openapi.json`,
`/clients/{id}/openapi.json`, and `/clients/{id}/actions` at runtime to adapt
per-client command aliases and help to the running client's actual action set.
TypeScript helpers should speak the daemon/protocol contract and should not
parse human CLI text.

## Client Management Decision

Client management is specified further in
`docs/superpowers/specs/2026-06-25-client-management-decisions.md`.

The short version:

- Craftwright's Phase 1 core must be independent of PrismLauncher.
- PrismLauncher is valuable research and a later optional desktop adapter, not
  the CI/headless runtime dependency.
- HeadlessMC/HMC-Specifics are valid bridge-spike tools for proving a real
  client can be automated, but their console text must not become the final
  public API.
- Persistent setup objects and live runtime objects must stay separate:
  versions, loaders, profiles, and instances are setup state; clients and
  session APIs are running state.

This keeps the first milestone focused on real client automation and headless
CI operation before UI integrations.

## HeadlessMC Learning Plan

Craftwright should study and selectively reuse ideas from HeadlessMC rather than
blindly fork it.

### Learn Directly

- Version installation and cache layout.
- Java runtime discovery and selection.
- Launch argument construction.
- LWJGL/headless strategies.
- Offline mode behavior.
- Modloader preparation.
- Classpath/resource instrumentation patterns.
- Runtime main wrapping.
- Command runtime initialization.
- CI cache practices from MC-Runtime-Test.

### Avoid In Final Public Contract

- Parsing HMC console text as the main event API.
- Exposing HeadlessMC command names as Craftwright's public UX.
- Depending on an external interactive process as the only command bridge.
- Letting upstream implementation details define Craftwright's protocol.

### Possible Upstream Strategy

If HeadlessMC APIs are clean enough for a component, use them as libraries where
licenses and stability allow. If the APIs are internal or too console-oriented,
copy the design pattern and reimplement the boundary in Craftwright.

Craftwright should keep upstream references in docs and tests so future changes
can be compared against working HeadlessMC behavior.

## Public CLI Direction

Keep `mcw` as the primary command name unless renamed separately. The CLI should
continue following the existing `mcw` UX contract:

- stdout for primary data.
- stderr for diagnostics and progress.
- `--json`, `--jsonl`, and `--plain` for machine output.
- stable exit codes.
- `--no-input` for CI.
- explicit timeouts.
- idempotent cache/setup commands.

The JVM rewrite should use a static core plus adaptive action dispatch. Static
commands should cover setup, daemon/supervisor lifecycle, config, profiles,
client creation/listing, raw OpenAPI/action discovery, generic action
invocation, and output modes. Per-client action aliases and generated alias
`--help` should be loaded from OpenAPI/action descriptors on demand.

```text
mcw init
mcw cache prepare --mc VERSION --loader fabric
mcw daemon start
mcw clients create --mc VERSION --offline --name NAME
mcw clients list
mcw clients NAME openapi
mcw clients NAME actions
mcw clients NAME run player.move --arg forward=true --arg ticks=20
mcw clients NAME player move --forward --ticks 20
mcw clients NAME player chat "hello"
mcw clients NAME player move --help
mcw clients NAME stop
mcw scenario run FILE
```

Standalone multi-invocation client commands require a persistent daemon or
background supervisor. The project must not fake persistence by launching
orphaned Minecraft processes.

## Playwright Direction

JavaScript/TypeScript test helpers are still useful for adoption.

The checked-in external SDK package has been removed for now. Keep the
Playwright package as a thin helper layer around injected automation clients
until the JVM protocol is ready for generated client code.

Playwright should be the primary test runner integration:

```ts
import { test, expect } from "@craftwright/playwright"

test("player can join through Gate", async ({ mc }) => {
  const alice = await mc.launch("alice")
  await alice.connect("localhost", 25565)
  await expect(alice).toHaveChat(/Welcome/)
})
```

The Playwright adapter should speak to injected automation clients. It should
not shell out and parse human CLI output.

## Implementation Strategy

Implementation should be incremental but honest. The repository should keep one
runtime owner for client lifecycle semantics: the JVM codebase.

### Phase 1: JVM Skeleton

Create the Gradle/Kotlin project with:

- `mcw --help`.
- config loading.
- protocol data classes.
- fake in-memory client engine.
- basic daemon transport.
- unit tests.

The first milestone is a working fake-client API and CLI loop, not Minecraft
launch.

### Phase 2: HeadlessMC Evidence Spike

Create a small spike module or research harness that runs one offline Fabric
client using HeadlessMC behavior and documents:

- exact launch command;
- cache files;
- process model;
- headless mode requirements;
- how HMC-Specifics initializes;
- how commands reach the client;
- where text parsing fails as a stable API.

This spike informs the Craftwright implementation. It should not become the
public abstraction.

### Phase 3: First In-Client Driver

Fill out the Fabric driver beyond the current Loom scaffold by extending the
client-thread gateway with Java Mixins, a small Kotlin/Java driver API, and
internal version-aware bindings.

Minimum discovered actions and driver lifecycle support:

- driver starts and connects to supervisor;
- emits `client.ready`;
- connects to offline server;
- sends chat;
- observes chat;
- returns player position;
- moves the player forward and verifies position changed;
- jumps or sets jump intent and verifies the client accepted the action;
- sets yaw/pitch and returns the updated look direction;
- returns raycast target or an explicit no-target result;
- returns a bounded nearby-block sample around the player;
- disconnects/stops cleanly;
- writes logs and structured events.

### Phase 4: Scenario And Test Helpers

Add:

- `mcw scenario run` using the JVM supervisor and driver;
- Playwright fixture smoke test;
- artifact collection.

### Phase 5: Version Expansion

Add version support one row at a time through the consolidated Fabric driver
where possible. Each version requires:

- runtime fingerprint and binding support checks;
- real smoke test;
- documented supported actions through that client's OpenAPI;
- update path for mappings and modloader versions.

Only split into a dedicated Gradle module or source set if Minecraft, Fabric
Loom, mappings, or classpath constraints make a consolidated artifact
unreliable.

## Testing Strategy

Default tests should not require a real Minecraft client.

Test bands:

- unit tests for protocol models, config, CLI parsing, cache metadata, and event
  state machines;
- fake-driver integration tests for daemon, scenarios, and Playwright
  fixtures;
- opt-in real-client smoke tests behind an environment variable;
- CI job for one pinned Minecraft/Fabric version after local reliability is
  proven;
- upstream comparison tests or fixtures based on HeadlessMC/HMC-Specifics output
  where useful.

The first real smoke should use:

- offline mode;
- one pinned Minecraft version;
- Fabric;
- local offline-mode server;
- expected welcome/chat assertion;
- deterministic artifact output.

## Risks

- Kotlin adds another language next to Java and TypeScript. Mitigation: Kotlin
  owns product logic; Java owns Mixins; TypeScript owns external test helpers.
- Minecraft version drift will break Mixins. Mitigation: version modules and
  explicit support matrix.
- Reimplementing launcher behavior can become large. Mitigation: learn from
  HeadlessMC first and keep the first version narrow.
- Headless rendering is hard. Mitigation: start with HeadlessMC's LWJGL/Xvfb
  evidence and preserve an escape hatch for Xvfb.
- Public protocol design can overgrow early. Mitigation: support only
  launch/connect/chat/wait/stop before GUI and inventory work.

## Done Definition For This Rewrite Design

The JVM-first rewrite is ready to enter implementation planning when:

- this spec is accepted as the project direction;
- the first implementation plan targets a Kotlin/JVM skeleton with Java Mixin
  support prepared but not overbuilt;
- the first real-client milestone is limited to one offline Fabric version;
- TypeScript and Playwright are defined as protocol consumers, not core runtime.

## Full Project Done Definition

Craftwright is complete enough for its original goal when:

- `mcw` can launch and supervise real Minecraft Java clients in offline mode;
- tests can connect clients to Gate, Connect, and ordinary Minecraft servers;
- clients can invoke chat and command actions, wait for chat/disconnect/screen
  state, and stop reliably;
- structured events and artifacts are emitted without relying on human log text;
- Playwright fixtures are stable enough for Minekube E2E tests;
- at least one Minecraft/Fabric version is supported in CI;
- adding a new Minecraft version is documented and tested through a repeatable
  driver-module process.

## References

- Generated local client API design:
  `docs/superpowers/specs/2026-06-25-generated-client-api-design.md`
- Client management decisions:
  `docs/superpowers/specs/2026-06-25-client-management-decisions.md`
- HeadlessMC:
  https://github.com/headlesshq/headlessmc
- HMC-Specifics:
  https://github.com/headlesshq/hmc-specifics
- PrismLauncher:
  https://github.com/PrismLauncher/PrismLauncher
- MC-Runtime-Test:
  https://github.com/headlesshq/mc-runtime-test
- Fabric Loom:
  https://fabricmc.net/develop/
- Playwright:
  https://playwright.dev/
