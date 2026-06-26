# Craftless Project Checklist

This checklist is the active project red line. Keep it short, current, and
honest. Update it whenever implementation or product status changes.

Legend:

- `[ ]` not started
- `[~]` in progress
- `[x]` done with evidence
- `[!]` blocked

## Current Baseline

- [x] Repository is renamed to Craftless and uses `com.minekube.craftless`.
- [x] Tooling is pinned through `mise`.
- [x] JVM HTTP surfaces use Ktor Server/Client.
- [x] Go implementation and removed TypeScript SDK are not active product
  surfaces.
- [x] Stable supervisor OpenAPI exists at `GET /openapi.json`.
- [x] Per-client OpenAPI route exists at `GET /clients/{id}/openapi.json`.
- [x] Generic action invocation exists at `POST /clients/{id}:run`.
- [x] Live resource projection exists at `GET /clients/{id}/resources` and in
  `x-craftless-resources`, derived from the same action snapshot as
  per-client OpenAPI.
- [x] CLI binary is `craftless` and uses adaptive action metadata.
- [x] CLI generic and generated-alias action dispatch use the live per-client
  OpenAPI action descriptor for argument schema validation, help, positional
  argument mapping, and nested generated aliases such as
  `/clients/{id}/world/block:break`, with `/clients/{id}/actions` treated as
  an availability projection. Invocation no longer gates on `/actions`; live
  OpenAPI is the action existence and schema authority.
- [x] Daemon generic and generated-alias action dispatch validate driver result
  payloads against the advertised action result descriptor before returning
  success.
- [x] Fabric smoke has proven real client launch, server join, generated chat,
  generated movement invocation, disconnect, and artifact capture.
- [~] Current Fabric driver has real chat, movement, connected-client
  `player.query`, connected-client `player.raycast`, connected-client
  `inventory.query`, connected-client `inventory.equip`, and connected-client
  `world.block.break` bindings. When the client is disconnected, player query,
  raycast, inventory query, inventory equip, and block break are exposed only
  through gateway-backed unavailable probe metadata. Broader gameplay discovery
  is not implemented yet and must not be represented as a static placeholder
  catalog.
- [ ] Craftless is complete.

Baseline evidence:

- Latest real-smoke evidence path:
  `driver-fabric/build/craftless-local-server-smoke/artifacts/`
- Latest static-placeholder cleanup smoke: `client-actions.json` contained
  only `player.chat` and `player.move`; `server-evidence.jsonl` contained
  join, chat, and disconnect for the same real client.
- Current smoke controller re-fetches connected client OpenAPI/actions before
  invoking gameplay actions and writes `client-openapi-connected.json`,
  `client-actions-connected.json`, `client-resources-connected.json`, and
  `gameplay-results.jsonl`.
- Key commands:
  - `mise run lint`
  - `mise run architecture-check`
  - `mise run ci`
  - `CRAFTLESS_FABRIC_CLIENT_SMOKE=1 mise exec -- gradle :driver-fabric:fabricClientSmoke`
- Current known local-only files: none in `git status`.

## 1. Product Positioning And README

- [x] Restore the richer README comparison structure from the earlier approved
  direction, updated to current truth.
- [x] Keep the README comparison focused on Craftless, Mineflayer, and
  Baritone unless another project is useful as clearly labelled evidence.
- [x] Do not advertise HeadlessMC/HMC-Specifics, Prism Launcher, removed SDKs,
  or bridge internals as active product surfaces.
- [x] README must clearly separate implemented features from roadmap.
- [x] README must describe Craftless as live generated OpenAPI over real
  Minecraft Java clients, not a static action SDK.

Verification:

- `git diff --check`
- `rg -n "minekube\\.dev|dev\\.minekube|player/sendChat|/player/sendChat" README.md docs --glob '!docs/superpowers/**' --glob '!docs/AGENTS.md' --glob '!docs/project-completion-checklist.md' -S`

## 2. API Layer Separation

- [x] Root agent rules explicitly separate supervisor API, live per-client
  generated API, descriptor projections, adaptive consumers, internal driver
  API, Fabric discovery/projection, and Fabric execution bindings.
- [x] README and architecture docs explain those layers without reintroducing
  stale static action routes.
- [x] Supervisor/client-management API remains lifecycle/setup/discovery only;
  gameplay does not move into the stable kernel route catalog.
- [x] Live per-client OpenAPI owns gameplay actions/resources, generated
  aliases, schemas, handles, availability, and runtime fingerprints.
- [x] `/clients/{id}/actions` remains a projection of per-client OpenAPI, not
  a separate source of truth.
- [x] `/clients/{id}/resources` remains a live projection derived from the
  per-client OpenAPI action snapshot, not an independent source of truth.
- [x] Adaptive CLI generic and generated-alias action paths use the live
  per-client OpenAPI descriptor as the argument/help schema authority,
  including nested resource aliases derived from action ids, and do not treat
  `/clients/{id}/actions` as an invocation precondition.
- [~] CLI and external helper consumers use OpenAPI/descriptors at runtime
  instead of hard-coding gameplay commands. The Playwright helper now has a
  thin OpenAPI action client that fetches `/clients/{id}/openapi.json` before
  invoking `POST /clients/{id}:run`; generated clients and agent-tool
  packaging remain roadmap.
- [x] `DriverSession` remains lifecycle/events/runtime metadata plus
  `actions()` and `invoke(...)`; no static player/world/inventory methods.
- [x] Fabric discovery/projection and execution bindings stay internal and
  client-thread safe.

Verification:

- `git diff --check`
- `rg -n "fun (sendChat|player|inventory|raycast)\\(|/clients/\\{id\\}/player/sendChat|/player/sendChat|GET /clients/\\{id\\}/player" README.md docs driver-api/src/main driver-runtime/src/main driver-fabric/src/main daemon/src/main cli/src/main protocol/src/main --glob '!docs/superpowers/**' --glob '!docs/AGENTS.md' --glob '!docs/project-completion-checklist.md' -S`

## 3. Runtime Discovery Architecture

- [x] Remove static placeholder action descriptors from product code and tests.
- [x] Action descriptors and per-client OpenAPI carry action source,
  availability, and machine-readable availability reasons.
- [~] Design the Fabric runtime discovery/projection layer. A minimal internal
  discovery abstraction exists for binding-backed actions, connected-client
  `player.query`, connected-client `player.raycast`, connected-client
  `inventory.query`, connected-client `inventory.equip`,
  connected-client `world.block.break`, and
  disconnected-client unavailable probe metadata; broader
  client/world/inventory/screen probes are still roadmap.
- [~] Define how internal Fabric/Minecraft/mod/registry/server data becomes
  Craftless-owned actions, resources, handles, schemas, availability, and
  events. Action-derived resource projection is implemented; richer handles,
  object schemas, registry/server-feature resources, and event/resource
  relationships are still roadmap.
- [x] Define the rule for unavailable-but-detected operations: they may appear
  in OpenAPI only when a runtime probe discovered them and produced a
  machine-readable availability reason.
- [x] Daemon generic and alias action routes reject unavailable action
  descriptors before driver invocation.
- [x] Daemon generic and alias action routes reject driver results that do not
  match the advertised action result descriptor.
- [x] Ensure generated aliases are derived only from the running client's
  OpenAPI/action descriptors.
- [x] Ensure public OpenAPI does not expose Fabric/Yarn/intermediary names,
  raw Minecraft implementation names, mod package names, commands, or launcher
  internals.
- [x] Add tests that fail if public descriptors leak implementation names.

Verification:

- `mise exec -- gradle :protocol:test :driver-fabric:test`
- `mise exec -- gradle :protocol:test --tests com.minekube.craftless.protocol.NamespacePolicyTest`
- `mise exec -- gradle :driver-fabric:test --tests com.minekube.craftless.driver.fabric.v1_21_6.FabricDriverModuleTest`

## 4. Fabric Driver Action Bindings

- [x] `player.chat` has a real Fabric binding.
- [x] `player.move` has a real Fabric binding and driver-side event evidence.
- [x] Fabric action listing goes through an internal discovery snapshot instead
  of directly returning the binding map.
- [~] Real look/perception/block/inventory/screen capabilities are discovered
  from the running client before they are advertised. `player.query`,
  `player.raycast`, `inventory.query`, `inventory.equip`, and
  `world.block.break` now change from unavailable probe metadata to available
  bindings based on connected-client state; broader block/inventory/screen
  discovery is still missing.
- [x] Each advertised gameplay action has either a real Fabric execution
  binding or probe-backed unavailable metadata.
- [x] No future gameplay action is added as a hand-written placeholder
  descriptor.
- [x] `FabricClientGateway` stays generic and does not grow one method per
  gameplay action.

Verification:

- `mise exec -- gradle :driver-fabric:test`
- `CRAFTLESS_FABRIC_CLIENT_SMOKE=1 mise exec -- gradle :driver-fabric:fabricClientSmoke`

## 5. Real Gameplay Vertical Slice

- [x] Define the first useful end-to-end gameplay slice: discover a target
  inventory item, equip it through live action metadata, and exercise a world
  block action through generated API contracts.
- [~] Recommended target: obtain/equip an iron sword using real client actions,
  without Minecraft console commands as the public API. The smoke can now
  equip an `Iron Sword` when it appears in `inventory.query`; real acquisition
  is still missing.
- [~] The slice uses generated OpenAPI/action metadata as the client contract.
  The smoke controller now re-fetches connected client OpenAPI and gates
  `player.query`, `inventory.query`, `inventory.equip`, and
  `world.block.break` invocations on available actions from
  `x-craftless-actions` before calling generic `POST /clients/{id}:run`;
  `/clients/{id}/actions` remains an evidence/projection artifact.
- [~] The slice discovers the needed actions/resources from the running client;
  it does not call hard-coded Kotlin methods or static CLI commands for current
  smoke gameplay actions. The smoke chooses the equip slot from live
  `inventory.query` data and records `player.query` telemetry. The final
  iron-sword workflow still needs real acquisition evidence.
- [ ] The slice runs against a real Fabric client and local server fixture.
- [~] Evidence proves observable game effects through server logs, client
  telemetry, or both. Current smoke artifacts include connected OpenAPI/actions
  resources and gameplay action result telemetry, including target-item slot
  selection when present; observable iron-sword acquisition is still missing.

Verification:

- `CRAFTLESS_FABRIC_CLIENT_SMOKE=1 mise exec -- gradle :driver-fabric:fabricClientSmoke`
- Add a narrower smoke command when the slice exists.

## 6. Client Runtime And Files

- [x] Craftless-owned instance file layout is modeled.
- [x] The instance file contract exposes separate game, runtime, cache, logs,
  screenshots, and artifacts handles.
- [x] The daemon can materialize those directories under a configured workspace
  root without clearing existing runtime files.
- [x] `craftless server start --workspace <path>` wires that workspace into the
  local daemon startup path and reports it in startup metadata.
- [x] `craftless cache prepare --mc <version> --loader <loader> --workspace
  <path>` and `POST /cache:prepare` prepare Craftless-owned cache directories
  and a setup manifest.
- [x] Cache preparation resolves and stores the Minecraft version index and the
  selected Minecraft version manifest through Ktor Client with offline test
  fakes.
- [x] Cache preparation resolves Fabric compatible loader versions, records the
  resolved loader version, supports an optional loader-version pin, and stores
  the Fabric loader profile JSON through Ktor Client with offline test fakes.
- [x] Prism Launcher source was cloned under `/tmp/prismlauncher-source` for
  research, outside Minekube repos.
- [x] Prism findings are captured as design input, not a core dependency.
- [~] Client runtime/file management is strong enough for repeated local and CI
  runs. Instance directories and cache preparation are now repeatable and
  idempotent, with Minecraft version metadata and Fabric loader profile
  metadata resolved into the workspace; Java/runtime artifact resolution,
  Minecraft client jar/Fabric artifact downloads, and explicit cleanup/export
  flows are still roadmap.
- [x] Public APIs expose Craftless-owned file handles only.

Verification:

- `mise exec -- gradle :protocol:test :daemon:test`

## 7. Quality Gates

- [x] `mise run lint` exists.
- [x] `mise run ci` exists.
- [x] Kotlin lint includes ktlint, detekt, and compiler warnings as errors.
- [x] Bun helper tests run through `mise`.
- [x] `mise run architecture-check` covers the live OpenAPI/action architecture
  across protocol, daemon, CLI, Fabric driver, and Bun helper tests.

Verification:

- `mise run architecture-check`
- `mise run lint`
- `mise run ci`
- `git diff --check`

## Completion Gate

Craftless is complete only when all are true:

- [ ] README and docs match the restored product direction.
- [ ] Public docs and code preserve the API layer separation described in
  `AGENTS.md`.
- [ ] Per-client OpenAPI is generated from runtime discovery and real bindings,
  not a static placeholder action list.
- [ ] Public API names are Craftless-owned and policy tests enforce that.
- [ ] Advertised player/world/inventory/screen actions have real Fabric
  execution bindings or probe-backed unavailable metadata.
- [ ] A real gameplay vertical slice passes through API/CLI the way a user or
  agent would use it.
- [ ] `mise run lint` passes.
- [ ] `mise run ci` passes.
- [ ] Opt-in Fabric real-client smoke passes and writes evidence artifacts.
- [ ] The completed work is pushed to `main` when requested.
