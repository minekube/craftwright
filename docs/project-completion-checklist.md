# Craftless Project Completion Checklist

This is the project red line. Agents must use it to choose the next concrete
step toward completing Craftless, and must update it when implementation status
changes.

Status legend:

- `[ ]` not started
- `[~]` in progress
- `[x]` done with evidence
- `[!]` blocked

## 0. Repository Discipline

- [x] Root `AGENTS.md` and submodule `AGENTS.md` files define current rules.
- [x] Tool versions are pinned in `.mise.toml`.
- [x] JVM work is documented as `mise exec -- gradle ...`.
- [x] JavaScript helper work is documented as `mise exec -- bun ...`.
- [x] CI entrypoint is documented as `mise run ci`.
- [ ] No npm, npx, yarn, pnpm, or global node workflow remains.
- [ ] No unrelated dirty files are reverted or cleaned.

Evidence:

- Last verified working-tree note: root checklist created while `testkit/` and
  `.vscode/` already had unrelated dirty entries.
- Commands:
  - `git status --short --branch`
  - `sed -n '1,220p' .mise.toml`
- Next action: run full policy searches before marking the remaining workflow
  discipline items complete.

## 1. Naming And Public Surface

- [x] Root project name is `craftless`.
- [x] Root Gradle group is `com.minekube.craftless`.
- [x] README presents Craftless as the active product.
- [x] Public domain references use `minekube.com`.
- [ ] No `craftwright` references remain.
- [ ] No removed TypeScript SDK is documented as active.
- [ ] README describes only current active architecture plus clearly marked
  roadmap.
- [ ] Public API does not expose HeadlessMC, HMC-Specifics, Fabric/Yarn,
  intermediary names, raw Minecraft internals, or launcher internals.

Evidence:

- Commands:
  - `sed -n '1,240p' README.md`
  - `sed -n '1,240p' build.gradle.kts`
  - `sed -n '1,220p' settings.gradle.kts`
- Next action: run the namespace/policy tests and repository searches before
  marking the remaining public-surface items complete.

## 2. HTTP And API Rules

- [x] Stable daemon OpenAPI route exists at `GET /openapi.json`.
- [x] Per-client OpenAPI route exists at `GET /clients/{id}/openapi.json`.
- [x] Per-client actions route exists at `GET /clients/{id}/actions`.
- [x] Generic action invocation route exists at `POST /clients/{id}:run`.
- [ ] Product HTTP server code uses Ktor Server only.
- [ ] Kotlin/JVM HTTP client code and tests use Ktor Client only.
- [ ] No OkHttp dependency exists.
- [ ] No Java `HttpClient` product path exists.
- [ ] No `com.sun.net.httpserver` product path exists.
- [ ] No custom HTTP method enum exists.
- [ ] Resource-oriented lifecycle routes use AIP-style standard and custom
  methods.

Evidence:

- Commands:
  - `sed -n '1,240p' protocol/src/main/kotlin/com/minekube/craftless/protocol/ApiRoute.kt`
- Next action: verify with protocol policy tests and targeted dependency/source
  searches.

## 3. Driver Contract

- [x] `DriverSession` contains lifecycle/session primitives.
- [x] `DriverSession` exposes `actions()` and generic `invoke(...)`.
- [x] `DriverSession` does not expose `sendChat`.
- [x] `DriverSession` does not expose `player()`.
- [x] `DriverSession` does not expose user-facing `capabilities()`.
- [x] Driver action descriptors validate Craftless-owned action IDs.
- [x] Runtime metadata rejects non-Craftless public driver names.
- [ ] Fake driver remains test-only and does not define public architecture.

Evidence:

- Commands:
  - `sed -n '1,220p' driver-api/src/main/kotlin/com/minekube/craftless/driver/api/DriverSession.kt`
- Tests to rerun before final completion:
  - `mise exec -- gradle :driver-api:test`

## 4. Generated OpenAPI And Actions

- [x] Kernel OpenAPI describes lifecycle and per-client spec discovery.
- [x] Per-client OpenAPI includes runtime metadata and available actions.
- [x] Action discovery and invocation use `/clients/{id}/actions` and
  `POST /clients/{id}:run`.
- [x] Driver backends dispatch generated actions through an action registry or
  binding table, not by growing one `if`/`when` branch per action in the main
  backend class.
- [x] Runtime gateways expose generic lifecycle, scheduling, and runtime access
  boundaries only; action-specific chat, move, inventory, look, raycast, and
  interaction behavior lives in discovered action bindings.
- [ ] Runtime/action fingerprint includes Minecraft version, loader, driver,
  mappings, mods, registries, server features, permissions, and schema versions.
- [ ] Action descriptors include all needed argument schemas.
- [ ] Action result schemas are represented where needed.
- [ ] Daemon rejects unavailable actions.
- [ ] Daemon rejects invalid or mismatched arguments.
- [ ] Generated aliases are derived from OpenAPI/action metadata only.
- [ ] No static route family is added for gameplay actions such as
  `/player/sendChat`.

Evidence:

- Commands:
  - `sed -n '1,240p' docs/roadmap.md`
  - `rg -n "interface .*Gateway|class .*Gateway|dispatchChatMessage|fun (chat|move|jump|look)\\(|when \\(invocation\\.action\\)|if \\(invocation\\.action|DriverActionDescriptor\\(|actions\\(clientId|ClientAction|player\\.chat|player\\.move" driver-api driver-runtime driver-fabric bridge-hmc daemon cli protocol --glob '!build/**' --glob '!**/.gradle/**'`
- Tests to rerun before final completion:
  - `mise exec -- gradle :protocol:test :daemon:test :cli:test`
Current action-boundary audit:

- Working-tree evidence: `driver-fabric/.../FabricActionBindings.kt` owns the
  internal chat/move bindings; `FabricDriverBackend` dispatches through a
  binding registry; `FabricClientGateway` exposes `executeOnClient(...)`
  instead of `dispatchChatMessage(...)` or `move(...)`.
- Verification:
  - `mise exec -- gradle :driver-fabric:test --tests com.minekube.craftless.driver.fabric.v1_21_6.FabricDriverModuleTest`
  - `rg -n "dispatchChatMessage|fun move\\(|when \\(invocation\\.action\\)|if \\(invocation\\.action|DriverActionDescriptor\\(|actions\\(clientId|player\\.chat|player\\.move|executeOnClient" driver-fabric/src/main driver-fabric/src/test -S`
- Allowed only as a fake fixture: `driver-api/.../FakeDriverSession` has a
  small hard-coded action switch for tests. It must not define public
  architecture or be copied into real drivers.
- Allowed only as evidence/legacy bridge: `driver-runtime/.../HmcBridgeDriverBackend.kt`
  and `bridge-hmc/.../HmcBridgeBackend.kt` contain static chat/move/jump/look
  methods or switches. They must remain isolated from the final Fabric path.
- Acceptable generic API usage: daemon, CLI, protocol, and tests may mention
  `player.chat` and `player.move` when proving generic action metadata,
  validation, aliases, or examples. They must not add static gameplay routes or
  SDK-style methods.

## 5. Daemon

- [x] Daemon can create, list, fetch, connect, stop, and inspect clients.
- [x] Daemon exposes client events.
- [x] Daemon serves stable `/openapi.json`.
- [x] Daemon serves live `/clients/{id}/openapi.json`.
- [x] Daemon serves `/clients/{id}/actions`.
- [x] Daemon dispatches `POST /clients/{id}:run` through the driver runtime.
- [ ] Daemon errors are structured for unsupported action, invalid input,
  missing client, and stopped client states.
- [ ] Daemon tests cover lifecycle, OpenAPI, action listing, action invocation,
  and error behavior.

Evidence:

- Tests to rerun before final completion:
  - `mise exec -- gradle :daemon:test`

## 6. CLI

- [x] CLI binary name is `craftless`.
- [x] CLI uses Ktor Client.
- [x] CLI can fetch `/openapi.json`.
- [x] CLI can fetch `/clients/{id}/openapi.json`.
- [x] CLI can fetch `/clients/{id}/actions`.
- [x] CLI can invoke generic actions.
- [x] CLI includes adaptive per-client action aliases and help from metadata.
- [ ] Static CLI core is limited to daemon startup, config, auth, output,
  lifecycle, discovery, and generic dispatch.
- [ ] CLI does not contain a hand-maintained command for every Minecraft action.

Evidence:

- Tests to rerun before final completion:
  - `mise exec -- gradle :cli:test`

## 7. Fabric Driver Real-Client Proof

- [x] Fabric driver module exists and builds in the JVM project.
- [x] Fabric driver exposes Craftless-owned runtime metadata.
- [~] Fabric driver has gateway-backed runtime hooks for current action
  evidence, but the gateway still exposes action-specific chat/move methods and
  must be narrowed before the action surface expands.
- [x] Fabric driver supports generated `player.chat`.
- [x] Fabric driver supports generated `player.move`.
- [x] Opt-in Fabric smoke task exists behind `CRAFTLESS_FABRIC_CLIENT_SMOKE`.
- [x] Fabric action implementations are separated into internal action binding
  objects such as chat and move bindings.
- [x] `FabricDriverBackend` discovers descriptors from those bindings and
  invokes them generically by action ID.
- [x] `FabricClientGateway` does not grow one method per generated action.
- [~] Smoke harness can start a local Minecraft server and run a bounded client
  command while the server is alive.
- [ ] Opt-in Fabric smoke launches or attaches to a real Minecraft Java client
  successfully.
- [ ] Smoke client joins the local Minecraft server.
- [ ] Smoke flow fetches per-client OpenAPI.
- [ ] Smoke flow invokes at least one generated action through the daemon API.
- [ ] Server-side or driver-side evidence proves the action effect.
- [ ] Evidence artifacts are collected and documented.
- [ ] Bridge/HMC evidence remains separate and cannot count as Fabric
  completion.

Evidence:

- Last known pushed work:
  - Working-tree note: Fabric chat/move moved into internal action bindings;
    gateway narrowed to generic client execution; focused Fabric tests passed.
- Tests to rerun before final completion:
  - `CRAFTLESS_FABRIC_CLIENT_SMOKE=1 mise exec -- gradle :driver-fabric:fabricClientSmoke`
- Next action: refactor Fabric chat/move into internal action bindings, then
  complete and record a successful real-client Fabric smoke run.

## 8. Testkit And Local Server Evidence

- [x] Testkit provisions Minecraft server jars through Ktor Client.
- [x] Testkit accepts EULA only in fixture scope.
- [x] Testkit starts a local server with bounded timeouts.
- [x] Testkit keeps the server alive while smoke actions run.
- [x] Testkit collects server logs.
- [x] Testkit extracts join evidence.
- [x] Testkit extracts chat evidence.
- [ ] Testkit extracts movement or position evidence.
- [x] Testkit extracts disconnect evidence.
- [x] Smoke tasks are opt-in and do not run in normal unit tests.

Evidence:

- Tests to rerun before final completion:
  - `mise exec -- gradle :testkit:test`
  - `CRAFTLESS_LOCAL_SERVER_SMOKE=1 mise exec -- gradle :testkit:localMinecraftServerSmoke`
- Next action: add or verify movement/position evidence before marking this
  section complete.

## 9. Client File Management

- [x] Craftless-owned instance file layout is modeled.
- [x] Client responses expose instance root, game root, mods, config, saves,
  resource packs, and shader packs.
- [ ] Prism Launcher source findings are captured as design input.
- [ ] Prism import/adapter remains optional and not a core runtime dependency.
- [ ] Public API does not expose Prism internals.

Evidence:

- Tests to rerun before final completion:
  - `mise exec -- gradle :protocol:test :daemon:test`
- Next action: capture Prism-informed client file management decisions in docs
  or tests without making Prism a runtime dependency.

## 10. Documentation

- [x] README matches the generated OpenAPI/action architecture.
- [x] README clearly separates implemented features from roadmap.
- [x] README includes generated image assets.
- [x] `docs/product-positioning.md` remains the Craftless naming/product
  record.
- [x] `docs/roadmap.md` reflects the current completion status.
- [x] `docs/bridge-limitations.md` keeps bridge scoped as evidence-only.
- [ ] README has no stale SVG dependency.
- [ ] Docs do not advertise inactive TypeScript SDK, bridge internals, or
  launcher internals as product surfaces.

Evidence:

- Commands:
  - `sed -n '1,240p' README.md`
  - `sed -n '1,260p' docs/roadmap.md`
- Next action: run final docs searches before marking the remaining items
  complete.

## 11. CI And Verification

- [ ] `mise run ci` passes.
- [ ] `mise exec -- gradle test` passes.
- [ ] `mise exec -- bun test playwright` passes.
- [ ] Protocol policy tests cover naming, HTTP bans, and SDK boundaries.
- [ ] Driver contract tests cover stale method bans and action model.
- [ ] Daemon tests cover OpenAPI/action routes.
- [ ] CLI tests cover adaptive dispatch/help.
- [ ] Fabric module tests cover metadata, action gateway, and smoke plan.
- [ ] Opt-in real-client smoke has a documented successful run.

Evidence:

- Next action: rerun narrow tests after resolving the current `testkit/` dirty
  work, then run `mise run ci`.

## Final Completion Gate

Craftless is complete for this milestone only when all are true:

- [ ] Real Fabric client smoke passed.
- [ ] Generated action invocation through daemon API is proven.
- [ ] Server-side or driver-side evidence artifacts exist.
- [ ] README and roadmap reflect that exact state.
- [ ] `mise run ci` passes.
- [ ] No `AGENTS.md` violations remain.
- [ ] `main` contains the completed work.

Final evidence:

- Commit:
- Commands:
- Artifact paths:
- Remaining known gaps:
