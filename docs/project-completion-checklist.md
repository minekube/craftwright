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
- [x] Kotlin lint/static analysis is documented as `mise run lint`.
- [x] `mise run lint` enforces ktlint, detekt, and Kotlin compiler warnings as
  errors.
- [x] Kotlin formatting is documented as `mise run lint-fix`.
- [x] CI entrypoint is documented as `mise run ci`.
- [x] No npm, npx, yarn, pnpm, or global node workflow remains.
- [x] No unrelated dirty files are reverted or cleaned.

Evidence:

- Last verified working-tree note: `.vscode/` remains an untracked unrelated
  local entry and was preserved; generated Fabric/Loom `driver-fabric/run/` is
  ignored.
- Commands:
  - `git status --short --branch`
  - `sed -n '1,220p' .mise.toml`
  - `find . -path './.git' -prune -o -path './build' -prune -o -path '*/build' -prune -o -path './driver-fabric/run' -prune -o \( -name 'package.json' -o -name 'package-lock.json' -o -name 'npm-shrinkwrap.json' -o -name 'yarn.lock' -o -name 'pnpm-lock.yaml' -o -name '.npmrc' -o -name '.yarnrc' -o -name '.yarnrc.yml' \) -print`
  - `sed -n '1,220p' playwright/package.json`
  - `mise run lint`
  - `mise run ci`
  - `git diff --check`
  - Commit evidence: `.gitignore` ignores generated Fabric/Loom `run/`
    artifacts.

## 1. Naming And Public Surface

- [x] Root project name is `craftless`.
- [x] Root Gradle group is `com.minekube.craftless`.
- [x] README presents Craftless as the active product.
- [x] Public domain references use `minekube.com`.
- [x] No previous product-name references remain.
- [x] No removed TypeScript SDK is documented as active.
- [x] README describes only current active architecture plus clearly marked
  roadmap.
- [x] Public API does not expose HeadlessMC, HMC-Specifics, Fabric/Yarn,
  intermediary names, raw Minecraft internals, or launcher internals.

Evidence:

- Commands:
  - `sed -n '1,240p' README.md`
  - `sed -n '1,240p' build.gradle.kts`
  - `sed -n '1,220p' settings.gradle.kts`
  - `mise exec -- gradle :protocol:test --tests com.minekube.craftless.protocol.NamespacePolicyTest`
  - manual previous-name search using split literals to avoid embedding stale
    names in docs
- Current public-surface audit evidence:
  - `mise exec -- gradle :protocol:test --tests com.minekube.craftless.protocol.NamespacePolicyTest`
  - `rg -n "TypeScript SDK|typescript|TypeScript|HeadlessMC|HMC-Specifics|Fabric/Yarn|launcher internals|minekube\\.dev|player/sendChat|/player/sendChat" README.md docs --glob '!docs/superpowers/**' -S`
  - Remaining hits are policy text, roadmap non-goals, bridge limitation docs
    scoped as evidence-only, and checklist evidence. README active-product
    status does not advertise removed SDKs or bridge internals as product
    surfaces.

## 2. HTTP And API Rules

- [x] Stable daemon OpenAPI route exists at `GET /openapi.json`.
- [x] Per-client OpenAPI route exists at `GET /clients/{id}/openapi.json`.
- [x] Per-client actions route exists at `GET /clients/{id}/actions`.
- [x] Generic action invocation route exists at `POST /clients/{id}:run`.
- [x] Product HTTP server code uses Ktor Server only.
- [x] Kotlin/JVM HTTP client code and tests use Ktor Client only.
- [x] No OkHttp dependency exists.
- [x] No Java `HttpClient` product path exists.
- [x] No `com.sun.net.httpserver` product path exists.
- [x] No custom HTTP method enum exists.
- [x] Resource-oriented lifecycle routes use AIP-style standard and custom
  methods.

Evidence:

- Commands:
  - `sed -n '1,240p' protocol/src/main/kotlin/com/minekube/craftless/protocol/ApiRoute.kt`
  - `mise exec -- gradle :protocol:test --tests com.minekube.craftless.protocol.ApiRouteCatalogTest --tests com.minekube.craftless.protocol.NamespacePolicyTest`
  - `rg -n "java\\.net\\.http|com\\.sun\\.net\\.httpserver|okhttp|OkHttp|enum class HttpMethod|enum class HTTPMethod|sealed class HttpMethod|object HttpMethod|HttpMethod" . --glob '!build/**' --glob '!**/build/**' --glob '!**/.gradle/**' --glob '!driver-fabric/run/**' --glob '!.git/**'`
  - `rg -n "io\\.ktor|ktor-|ktor\\(" build.gradle.kts settings.gradle.kts */build.gradle.kts */src/main */src/test --glob '!**/build/**'`
  - `rg -n "HttpClient|embeddedServer|Application|routing|CIO|Netty|ktor" cli daemon testkit protocol driver-api driver-runtime driver-fabric bridge-hmc --glob '!**/build/**' --glob '!driver-fabric/run/**'`
  - `mise run ci`

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
- [x] Runtime/action fingerprint includes Minecraft version, loader, driver,
  mappings, mods, registries, server features, permissions, and schema versions.
- [x] Action descriptors include all needed argument schemas for current actions.
- [x] Action result schemas are represented in driver descriptors, action
  metadata, and generated alias OpenAPI responses.
- [x] Daemon rejects unavailable actions.
- [x] Daemon rejects invalid or mismatched arguments.
- [x] Generated aliases are derived from OpenAPI/action metadata only.
- [x] No static route family is added for gameplay actions such as
  `/player/sendChat`.

Evidence:

- Commands:
  - `sed -n '1,240p' docs/roadmap.md`
  - `rg -n "interface .*Gateway|class .*Gateway|dispatchChatMessage|fun (chat|move|jump|look)\\(|when \\(invocation\\.action\\)|if \\(invocation\\.action|DriverActionDescriptor\\(|actions\\(clientId|ClientAction|player\\.chat|player\\.move" driver-api driver-runtime driver-fabric bridge-hmc daemon cli protocol --glob '!build/**' --glob '!**/.gradle/**'`
- Tests to rerun before final completion:
  - `mise exec -- gradle :protocol:test :daemon:test :cli:test`
Current daemon rejection evidence:

- Working-tree evidence: `LocalSessionApiServer` returns structured
  `UNSUPPORTED_ACTION` errors for unavailable generated actions and
  `INVALID_ACTION_INPUT` errors for invalid IDs, undeclared arguments, missing
  required arguments, type mismatches, and driver-side action validation
  failures.
- Verification:
  - `mise exec -- gradle :protocol:test :daemon:test`
  - `mise run ci`
- Current schema/fingerprint evidence:
  - `DriverActionDescriptor.result` carries action result schema metadata.
  - `OpenApiAction.result` publishes result metadata through
    `x-craftless-actions` and `/clients/{id}/actions`.
  - Generated action alias responses use the action-specific result schema.
  - `x-craftless-action-fingerprint` includes action IDs, schema versions,
    argument schemas, and result schemas.
  - `ClientSessionServiceTest` asserts runtime fingerprints include Minecraft
    version, loader, loader version, driver, driver version, mappings, mods,
    registries, server features, permissions, and action schema fingerprints.
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
- [x] Daemon errors are structured for unsupported action, invalid input,
  missing client, and stopped client states.
- [x] Daemon tests cover lifecycle, OpenAPI, action listing, action invocation,
  and error behavior.

Evidence:

- Tests to rerun before final completion:
  - `mise exec -- gradle :daemon:test`
- Current structured error evidence:
  - `LocalSessionApiServerTest` asserts `MISSING_CLIENT`,
    `UNSUPPORTED_ACTION`, `INVALID_ACTION_INPUT`, and `STOPPED_CLIENT` JSON
    error codes at the Ktor route boundary.
  - `OpenApiDocument` advertises machine-readable error schemas for stable
    400, 404, and stopped-client 409 responses where applicable.
  - `mise exec -- gradle :protocol:test :daemon:test`
  - `mise exec -- gradle :cli:test`
  - `mise run ci`

## 6. CLI

- [x] CLI binary name is `craftless`.
- [x] CLI uses Ktor Client.
- [x] CLI can fetch `/openapi.json`.
- [x] CLI can fetch `/clients/{id}/openapi.json`.
- [x] CLI can fetch `/clients/{id}/actions`.
- [x] CLI can invoke generic actions.
- [x] CLI includes adaptive per-client action aliases and help from metadata.
- [x] Static CLI core is limited to daemon startup, config, auth, output,
  lifecycle, discovery, and generic dispatch.
- [x] CLI does not contain a hand-maintained command for every Minecraft action.

Evidence:

- Tests to rerun before final completion:
  - `mise exec -- gradle :cli:test`
- Current static/adaptive command evidence:
  - `CraftlessCli.registeredCommandPaths()` contains only `server start`,
    client lifecycle, discovery, OpenAPI/actions fetch, generic
    `clients <id> run <action>`, and adaptive
    `clients <id> <namespace> <action>`.
  - `CraftlessCliTest` rejects registered static gameplay commands such as
    `sendChat`, `player chat`, `player move`, inventory, world/entity, and
    raycast paths.
  - Generated alias tests prove dispatch and help are loaded from
    `/clients/{id}/actions` and `/clients/{id}/openapi.json`.
  - `mise exec -- gradle :cli:test --tests com.minekube.craftless.cli.CraftlessCliTest`

## 7. Fabric Driver Real-Client Proof

- [x] Fabric driver module exists and builds in the JVM project.
- [x] Fabric driver exposes Craftless-owned runtime metadata.
- [x] Fabric driver has gateway-backed runtime hooks for current action
  evidence without exposing action-specific chat/move gateway methods.
- [x] Fabric driver supports generated `player.chat`.
- [x] Fabric driver supports generated `player.move`.
- [x] Opt-in Fabric smoke task exists behind `CRAFTLESS_FABRIC_CLIENT_SMOKE`.
- [x] Fabric action implementations are separated into internal action binding
  objects such as chat and move bindings.
- [x] `FabricDriverBackend` discovers descriptors from those bindings and
  invokes them generically by action ID.
- [x] `FabricClientGateway` does not grow one method per generated action.
- [x] Smoke harness can start a local Minecraft server and run a bounded client
  command while the server is alive.
- [x] Opt-in Fabric smoke launches or attaches to a real Minecraft Java client
  successfully.
- [x] Smoke client joins the local Minecraft server.
- [x] Smoke flow fetches per-client OpenAPI.
- [x] Smoke flow invokes at least one generated action through the daemon API.
- [x] Smoke flow invokes generated `player.move` through the daemon API.
- [x] Server-side or driver-side evidence proves the action effect.
- [x] Evidence artifacts are collected and documented.
- [x] Bridge/HMC evidence remains separate and cannot count as Fabric
  completion.

Evidence:

- Last known pushed work:
  - Working-tree note: Fabric chat/move moved into internal action bindings;
    gateway narrowed to generic client execution; focused Fabric tests passed.
- Current daemon-backed smoke evidence:
  - `FabricClientSmokeController` starts `LocalSessionApiServer` with a
    Fabric-backed `BackendDriverSession`, then uses Ktor Client against
    `/clients`, `/clients/{id}/openapi.json`, `/clients/{id}/actions`,
    `/clients/{id}:connect`, and `/clients/{id}:run`.
  - The testkit server smoke passes `CRAFTLESS_SMOKE_ARTIFACTS_DIR` to the
    configured client command so the Fabric client process can write
    `client-openapi.json`, `client-actions.json`, `client-events.jsonl`, and
    `runtime-metadata.json` next to server evidence.
  - Successful real smoke on 2026-06-25:
    `CRAFTLESS_FABRIC_CLIENT_SMOKE=1 mise exec -- gradle :driver-fabric:fabricClientSmoke`
    launched Minecraft `1.21.6` through `:driver-fabric:runClient`, started
    the in-client daemon API, fetched per-client OpenAPI/actions, joined the
    local Minecraft server, invoked generated `player.chat` and `player.move`
    through `/clients/{id}:run`, and wrote client/server artifacts under
    `driver-fabric/build/craftless-local-server-smoke/artifacts/`.
  - Clean server evidence from that run:
    `server-evidence.jsonl` contains `PLAYER_JOINED`, `CHAT` with
    `hello from Craftless Fabric smoke`, and `PLAYER_DISCONNECTED` for the
    same real client player.
  - Client artifacts from that run:
    `client-openapi.json`, `client-actions.json`, `client-events.jsonl`,
    `runtime-metadata.json`, `server-evidence.jsonl`, and `server.log`.
    `client-events.jsonl` contains a `movement` event for generated
    `player.move` with the real Fabric driver.
  - Root-cause evidence: direct local connections must pass `null`
    `CookieStorage`; passing an empty non-null cookie store put Minecraft on
    the transfer connection path. The offline local smoke server also disables
    secure-profile enforcement and clears stale evidence before each run.
- Tests to rerun before final completion:
  - `mise exec -- gradle :driver-fabric:test :testkit:test`
  - `CRAFTLESS_FABRIC_CLIENT_SMOKE=1 mise exec -- gradle :driver-fabric:fabricClientSmoke`
- Next action: strengthen movement proof from accepted driver telemetry to
  server-side position deltas or measured in-client position telemetry.

## 8. Testkit And Local Server Evidence

- [x] Testkit provisions Minecraft server jars through Ktor Client.
- [x] Testkit accepts EULA only in fixture scope.
- [x] Testkit starts a local server with bounded timeouts.
- [x] Testkit keeps the server alive while smoke actions run.
- [x] Testkit collects server logs.
- [x] Testkit extracts join evidence.
- [x] Testkit extracts chat evidence.
- [x] Testkit extracts movement or position evidence.
- [x] Testkit extracts disconnect evidence.
- [x] Smoke tasks are opt-in and do not run in normal unit tests.

Evidence:

- Current movement/position evidence:
  - `LocalServerEvidence.movement(...)` records `from` and `to`
    `LocalServerPosition` coordinates.
  - `LocalServerFixture.recordEvidenceFromLogLine(...)` imports
    `[Craftless] <player> moved from <x> <y> <z> to <x> <y> <z>` server log
    lines as `LocalServerEvidenceType.MOVEMENT`.
  - `LocalServerFixtureTest` covers both direct JSONL movement evidence and
    parsed server-log movement evidence.
- Verification:
  - `mise exec -- gradle :testkit:test`
  - `CRAFTLESS_LOCAL_SERVER_SMOKE=1 mise exec -- gradle :testkit:localMinecraftServerSmoke`
  - `CRAFTLESS_FABRIC_CLIENT_SMOKE=1 mise exec -- gradle :driver-fabric:fabricClientSmoke`

## 9. Client File Management

- [x] Craftless-owned instance file layout is modeled.
- [x] Client responses expose instance root, game root, mods, config, saves,
  resource packs, and shader packs.
- [x] Prism Launcher source findings are captured as design input.
- [x] Prism import/adapter remains optional and not a core runtime dependency.
- [x] Public API does not expose Prism internals.

Evidence:

- Tests to rerun before final completion:
  - `mise exec -- gradle :protocol:test :daemon:test`
- Current Prism-informed file-management evidence:
  - `docs/client-file-management.md` records findings from
    `/tmp/prismlauncher-source` at commit
    `9c2c6415310a0f36f9a9c48f3ee4901ba20bb139`.
  - `InstanceFiles` keeps a Craftless-owned layout with `root`, `gameRoot`,
    `mods`, `config`, `saves`, `resourcePacks`, and `shaderPacks`.
  - `NamespacePolicyTest` verifies public Kotlin sources do not expose Prism,
    MultiMC, or launcher-internal file names.
  - `rg -n "Prism|PrismLauncher|MultiMC|MMC|instance\\.cfg|mmc-pack|patches/|ManagedPack" protocol/src/main daemon/src/main cli/src/main driver-api/src/main driver-runtime/src/main driver-fabric/src/main bridge-hmc/src/main testkit/src/main --glob '!**/build/**' --glob '!driver-fabric/run/**'`
  - `mise exec -- gradle :protocol:test --tests com.minekube.craftless.protocol.ClientModelsTest --tests com.minekube.craftless.protocol.NamespacePolicyTest`
  - `mise run ci`

## 10. Documentation

- [x] README matches the generated OpenAPI/action architecture.
- [x] README clearly separates implemented features from roadmap.
- [x] README includes generated image assets.
- [x] `docs/product-positioning.md` remains the Craftless naming/product
  record.
- [x] `docs/roadmap.md` reflects the current completion status.
- [x] `docs/bridge-limitations.md` keeps bridge scoped as evidence-only.
- [x] README has no stale SVG dependency.
- [x] Docs do not advertise inactive TypeScript SDK, bridge internals, or
  launcher internals as product surfaces.

Evidence:

- Commands:
  - `sed -n '1,240p' README.md`
  - `sed -n '1,260p' docs/roadmap.md`
  - `find . -path './.git' -prune -o -path './build' -prune -o -path '*/build' -prune -o -path './driver-fabric/run' -prune -o -name '*.svg' -print`
  - `rg -n "svg|\\.svg|typescript|TypeScript|sdk|SDK|bridge|HeadlessMC|HMC|Prism|PrismLauncher|MultiMC|MMC|launcher internals|minekube\\.dev|dev\\.minekube|player/sendChat|/player/sendChat" README.md docs -S --glob '!docs/superpowers/**'`
  - manual previous product-name search using split literals to avoid embedding
    stale names in docs
  - `mise exec -- gradle :protocol:test --tests com.minekube.craftless.protocol.NamespacePolicyTest`
  - `mise run ci`

## 11. CI And Verification

- [x] `mise run ci` passes.
- [x] `mise run lint` passes.
- [x] `mise exec -- gradle test` passes.
- [x] `mise exec -- bun test playwright` passes.
- [x] Protocol policy tests cover naming, HTTP bans, and SDK boundaries.
- [x] Driver contract tests cover stale method bans and action model.
- [x] Daemon tests cover OpenAPI/action routes.
- [x] CLI tests cover adaptive dispatch/help.
- [x] Fabric module tests cover metadata, action gateway, and smoke plan.
- [x] Opt-in real-client smoke has a documented successful run.

Evidence:

- Current verification:
  - `mise exec -- gradle :driver-api:test --tests com.minekube.craftless.driver.api.DriverSessionContractTest :protocol:test --tests com.minekube.craftless.protocol.OpenApiGenerationTest :daemon:test --tests com.minekube.craftless.daemon.ClientSessionServiceTest`
  - `mise exec -- gradle :testkit:test :driver-fabric:test`
  - `CRAFTLESS_FABRIC_CLIENT_SMOKE=1 mise exec -- gradle :driver-fabric:fabricClientSmoke`
  - `mise run lint`
  - `git diff --check`
  - `mise run ci`
  - `mise run ci` executed `mise exec -- gradle lint`,
    `mise exec -- gradle test`, and `mise exec -- bun test playwright`.
- Coverage audit is complete for the current protocol, driver, daemon, and CLI
  gates listed above.

## Final Completion Gate

Craftless is complete for this milestone only when all are true:

- [x] Real Fabric client smoke passed.
- [x] Generated action invocation through daemon API is proven.
- [x] Server-side or driver-side evidence artifacts exist.
- [x] README and roadmap reflect that exact state.
- [x] `mise run lint` passes.
- [x] `mise run ci` passes.
- [ ] No `AGENTS.md` violations remain.
- [ ] `main` contains the completed work.

Final evidence:

- Commit: `0c18fcb` (`driver-fabric: include movement in real smoke`).
- Commands:
  - `mise exec -- gradle :testkit:test :driver-fabric:test`
  - `CRAFTLESS_FABRIC_CLIENT_SMOKE=1 mise exec -- gradle :driver-fabric:fabricClientSmoke`
  - `mise run lint`
  - `git diff --check`
  - `mise run ci`
- Artifact paths:
  - `driver-fabric/build/craftless-local-server-smoke/logs/server.log`
  - `driver-fabric/build/craftless-local-server-smoke/artifacts/server-evidence.jsonl`
  - `driver-fabric/build/craftless-local-server-smoke/artifacts/client-openapi.json`
  - `driver-fabric/build/craftless-local-server-smoke/artifacts/client-actions.json`
  - `driver-fabric/build/craftless-local-server-smoke/artifacts/client-events.jsonl`
  - `driver-fabric/build/craftless-local-server-smoke/artifacts/runtime-metadata.json`
- Remaining known gaps:
  - Generated `player.move` has real-client driver-side event telemetry; the
    stronger position-delta proof is still roadmap.
  - The remaining checklist items in sections 1, 3, 4, and 11 still need
    requirement-specific audits before the overall goal can be marked complete.
