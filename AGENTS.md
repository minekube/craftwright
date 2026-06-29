# Craftless Agent Instructions

This file is the repository-wide entrypoint for agents. Keep it short enough to
read every time, but explicit enough that the project shape cannot be missed.
It is not the active roadmap, phase log, detailed rule inventory, or module
contract.

Do not append per-phase history, roadmap checkboxes, temporary tasks,
completion evidence, module rules, or long guardrail lists here.

## Read First, Then Update There

Before changing code or docs, read:

- `docs/agent-operating-contract.md` for durable API, driver, transport,
  versioning, and workflow guardrails;
- `docs/agent-module-contracts.md` for durable module-local guardrails;
- the nearest subdirectory `AGENTS.md` as a short pointer to the relevant
  module section;
- `docs/project-completion-checklist.md` for active completion gates, blockers,
  and evidence status;
- `docs/superpowers/phase-index.md` for the maintained phase index.

When an instruction needs to grow, update the owning doc above. Keep every
`AGENTS.md` as a stable routing file with only the most important local
guardrails.

## Project Essence

Craftless is Browserless-style automation infrastructure for real Minecraft
Java clients, headless or visible:

1. launch or attach to a real Minecraft Java client;
2. load a small in-client driver;
3. discover the live client/runtime capability graph;
4. expose local APIs whose machine contracts are generated OpenAPI documents.

Work on the system that discovers, projects, invokes, streams, resolves,
packages, and verifies Minecraft runtime affordances. Do not work inside the
system by adding one more bespoke gameplay action, scenario shortcut, route, or
CLI command.

## Non-Negotiables

- Public gameplay actions/resources/OpenAPI/CLI help come from the running
  client's runtime capability graph. Do not add static gameplay catalogs,
  placeholder descriptors, static route families, or descriptor plus binding
  pairs as the durable product shape.
- Keep API layers separate: stable supervisor API, live per-client generated
  API, descriptor projections, adaptive CLI/agents, small driver contract, and
  internal Fabric discovery/projection/execution adapters.
- Version breadth is a system property. Shared discovery, projection,
  invocation, attach transport, OpenAPI generation, artifact resolution, Java
  selection, cache layout, and packaging are the default. Add per-version code
  only for proven Minecraft, Fabric API, mapping, loader, or bytecode-signature
  divergence, and isolate only that adapter/accessor/provider behind a lane
  boundary.
- Acceptance gameplay such as collecting materials, crafting, combat,
  navigation, and building is proof that external users and agents can compose
  behavior through public generated APIs. It is not a reason to create
  `task.*`, survival, recipe, combat, navigation, or material-specific product
  APIs.
- Public names must be Craftless-owned. Do not expose HeadlessMC,
  HMC-Specifics, Fabric/Yarn/intermediary, raw Minecraft implementation,
  launcher internals, or Minecraft console command names as public API, CLI,
  SDK, README, or docs contracts.
- Use Ktor for JVM HTTP/SSE/client code. Do not add OkHttp,
  `com.sun.net.httpserver`, Java `HttpClient`, hand-rolled HTTP clients, or
  custom HTTP method enums in product code.
- The CLI binary is `craftless` and must stay adaptive from `/openapi.json`,
  `/clients/{id}/openapi.json`, and `/clients/{id}/actions` for gameplay.
- Live status claims must be fresh. Before saying a server, daemon, client, or
  Minecraft process is running, stopped, connected, or broken, re-check the
  live process/API state in the same turn and treat older agent transcripts,
  session notes, and process snapshots as stale.

## Immutable Basics

Craftless uses `com.minekube.craftless` and `minekube.com`. Use `mise` for all
repo tooling, including Bun. Do not use npm, npx, yarn, pnpm, or globally
installed Node tooling in repo workflows. Push directly to `main` when asked.
Preserve unrelated user work.
