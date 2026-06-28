# Craftless Agent Instructions

## Read First

This file is the repository-wide entrypoint for agents. Also read:

- the nearest subdirectory `AGENTS.md` before editing a module or docs
  directory;
- `docs/agent-operating-contract.md` for the durable API, driver, transport,
  module, versioning, and workflow guardrails;
- `docs/project-completion-checklist.md` for active completion gates, blockers,
  and evidence status;
- `docs/superpowers/phase-index.md` for the maintained phase index.

Craftless uses `com.minekube.craftless` for JVM packages, Gradle coordinates,
OpenAPI metadata, Fabric entrypoints, and implementation docs. The public
domain is `minekube.com`.

Keep this file stable. Do not add per-phase history, roadmap checkboxes,
temporary tasks, or completion evidence here. Put changing work items in the
checklist, phase index, specs, plans, and evidence files referenced above.
Root `AGENTS.md` should not grow for every new item.

## Non-Negotiables

Craftless is Browserless-style automation infrastructure for real Minecraft
Java clients. Its durable loop is: launch or attach to a real client, load a
small in-client driver, discover the live client/runtime capability graph, and
expose local APIs through generated OpenAPI.

Do not turn Craftless into a hand-written SDK with one static method, route, or
CLI command per Minecraft action.

Work on the system that discovers, projects, invokes, and streams Minecraft
runtime affordances; do not work inside the system by adding one more bespoke
gameplay action.

Do not add static placeholder action descriptors, and do not add new
hand-written public gameplay descriptor/binding pairs. Public gameplay
actions/resources must come from the live runtime capability graph and
Craftless-owned projection.

Craftless has two OpenAPI surfaces:

- `GET /openapi.json`: stable supervisor API for lifecycle, client creation,
  events, and per-client spec discovery.
- `GET /clients/{id}/openapi.json`: generated live API for one client
  instance. It reflects that client's Minecraft version, loader, driver
  runtime, mappings, installed mods, registries, server/game features,
  permissions, and discovered actions/resources.

Public names must be Craftless-owned projections. Fabric, Yarn, intermediary,
raw Minecraft, HeadlessMC, launcher, mod, and Minecraft console-command names
are implementation inputs only, not public API/CLI/README contracts.

Survival gameplay such as "collect wood, craft a weapon, find a cow, kill it,
and show loot" is an acceptance scenario, not a product API. If a scenario
needs a missing primitive, improve the generic graph/projection/invocation/
streaming/CLI/docs system that lets a normal agent compose the behavior.

Use Ktor Server/Client for JVM HTTP, SSE, and client code. Do not add OkHttp,
`com.sun.net.httpserver`, Java `HttpClient`, custom HTTP method enums, or
hand-rolled HTTP clients in product code.

Prefer SSE for server-to-client live streams and HTTP `POST` JSON-RPC-style
requests for client-to-server control. Use WebSocket only for features that
genuinely require bidirectional low-latency interactive control.

Version breadth is a system property. Shared runtime discovery, projection,
invocation, attach transport, OpenAPI generation, artifact resolution, Java
selection, and cache layout are the default. Add per-version code only for a
documented Minecraft, Fabric API, mapping, loader, or bytecode-signature
divergence, isolated behind a lane boundary.

Use `mise` for pinned dependencies and commands. Use `mise exec -- gradle ...`
for JVM work and `mise exec -- bun ...` for Bun work. Do not use npm, npx,
yarn, pnpm, or globally installed Node tooling in repo workflows.

## Where Updates Go

Use `docs/project-completion-checklist.md` as the active project checklist.
Update it when project status changes. Do not duplicate its completion gates,
phase list, blockers, or evidence in this file.

When adding a new phase, update the spec, plan, evidence, checklist, and
`docs/superpowers/phase-index.md`. Do not copy the checklist, roadmap, phase
history, or completion evidence back into this file.

- Keep README and docs aligned with current architecture.
- Preserve `docs/product-positioning.md` as the Craftless naming and
  Browserless/CDP analogy record.
- Keep historical plans under `docs/superpowers/` as traceability, not current
  product truth.
- Make implemented state versus roadmap explicit.

## Workflow

- Prefer test-first changes for behavior, bug fixes, and API changes.
- Keep edits scoped to the requested behavior and current module boundaries.
- Preserve unrelated dirty files; do not revert user work.
- If asked to push, push directly to `main`; do not create a PR unless asked.
- Before claiming a code change is complete, run focused tests and then
  `mise run ci` when practical.
- For docs-only edits, run at least `git diff --check`.
