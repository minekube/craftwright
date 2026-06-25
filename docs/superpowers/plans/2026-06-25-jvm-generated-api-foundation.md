# JVM Generated API Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Craftless's Kotlin/JVM foundation with Craftless-owned generated API contracts and a documented bridge path to the first real-client smoke test.

**Architecture:** Use a Gradle Kotlin multi-project skeleton as the only implementation path. The first executable JVM slice contains protocol DTOs, OpenAPI route generation over fake Minecraft objects, a fake client/session model, and a bridge backend interface that hides HeadlessMC/HMC-Specifics command strings behind Craftless-owned routes.

**Tech Stack:** Gradle Kotlin DSL, Kotlin 2.4.0, kotlinx.serialization 1.11.0, kotlinx.coroutines 1.11.0, JUnit 5, Kotest 6.2.1, Clikt 5.1.0, Ktor Server/Client 3.5.0, Java 21, and Bun for TypeScript package/test execution. Fabric Loom and TypeScript packages are planned as later modules after this foundation compiles.

---

## File Structure

- Create `settings.gradle.kts`: Gradle project name and modules.
- Create `build.gradle.kts`: shared repositories, Kotlin/JVM defaults, Java 21 toolchain, test setup, dependency versions.
- Create `protocol/`: serializable API models, route metadata, OpenAPI emitter, fake root registration tests.
- Create `testkit/`: fake Minecraft client/session objects for route and API tests.
- Create `daemon/`: local session API route table and in-memory session service.
- Create `bridge-hmc/`: temporary HeadlessMC/HMC-Specifics bridge interface, limitation docs, and command mapping kept internal.
- Create `cli/`: JVM `craftless` entrypoint and first command tree tests.
- Create `docs/bridge-limitations.md`: public warning that bridge movement is simulated and not the final Fabric driver.
- `driver-api/`, `driver-runtime/`, `driver-fabric/`, and `playwright/` now
  exist. The Fabric module currently compiles as a Fabric/Loom scaffold with
  internal version-aware bindings unless separate artifacts become technically
  necessary.

## Task 1: Gradle/Kotlin Foundation

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `protocol/build.gradle.kts`
- Create: `protocol/src/test/kotlin/com/minekube/craftless/protocol/ApiRouteCatalogTest.kt`
- Create: `protocol/src/main/kotlin/com/minekube/craftless/protocol/ApiRoute.kt`
- Create: `protocol/src/main/kotlin/com/minekube/craftless/protocol/OpenApiDocument.kt`

- [ ] **Step 1: Write the failing route catalog test**

```kotlin
package com.minekube.craftless.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiRouteCatalogTest {
    @Test
    fun `catalog exposes required stable session routes`() {
        val catalog = ApiRouteCatalog.sessionDefaults()

        assertEquals("GET", catalog.route("/openapi.json").method)
        assertEquals("GET", catalog.route("/version").method)
        assertEquals("GET", catalog.route("/events").method)
        assertEquals("POST", catalog.route("/clients").method)
        assertEquals("GET", catalog.route("/clients/{id}/openapi.json").method)
        assertEquals("GET", catalog.route("/clients/{id}/actions").method)
        assertEquals("POST", catalog.route("/clients/{id}:run").method)
        assertEquals("POST", catalog.route("/clients/{id}:stop").method)
        assertEquals("GET", catalog.route("/clients/{id}/events").method)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mise exec -- gradle :protocol:test --tests com.minekube.craftless.protocol.ApiRouteCatalogTest`

Expected: FAIL because `ApiRouteCatalog` and Gradle module files are not implemented yet.

- [ ] **Step 3: Write minimal implementation**

Implement `ApiRoute`, `ApiRouteCatalog.sessionDefaults()`, route lookup, and a small serializable OpenAPI document model that can list operations for the default routes. Keep HTTP verbs as protocol data strings such as `"GET"` and `"POST"` rather than introducing a Craftless-owned HTTP method enum.

- [ ] **Step 4: Run test to verify it passes**

Run: `mise exec -- gradle :protocol:test --tests com.minekube.craftless.protocol.ApiRouteCatalogTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts build.gradle.kts protocol
git commit -m "feat: add JVM protocol foundation"
```

## Task 2: OpenAPI Generation From Fake Objects

**Files:**
- Create: `testkit/build.gradle.kts`
- Create: `testkit/src/main/kotlin/com/minekube/craftless/testkit/FakeMinecraftClient.kt`
- Create: `protocol/src/test/kotlin/com/minekube/craftless/protocol/OpenApiGenerationTest.kt`
- Modify: `protocol/src/main/kotlin/com/minekube/craftless/protocol/ApiRoute.kt`
- Modify: `protocol/src/main/kotlin/com/minekube/craftless/protocol/OpenApiDocument.kt`

- [ ] **Step 1: Write the failing OpenAPI test**

```kotlin
@Test
fun `openapi document includes craftless metadata for fake player routes`() {
    val document = OpenApiDocument.from(ApiRouteCatalog.sessionDefaults())

    val operation = document.paths["/clients/{id}:run"]?.post
    assertEquals("runClientAction", operation?.operationId)
    assertEquals("clients", operation?.extensions?.get("x-craftless-owner"))
    assertEquals("run", operation?.extensions?.get("x-craftless-member"))
    assertEquals("client", operation?.extensions?.get("x-craftless-target"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mise exec -- gradle :protocol:test --tests com.minekube.craftless.protocol.OpenApiGenerationTest`

Expected: FAIL because OpenAPI conversion does not include operation metadata yet.

- [ ] **Step 3: Write minimal implementation**

Add operation IDs, tags, source class/method metadata, client-thread metadata, and JSON-serializable path entries.

- [ ] **Step 4: Run test to verify it passes**

Run: `mise exec -- gradle :protocol:test --tests com.minekube.craftless.protocol.OpenApiGenerationTest`

Expected: PASS.

## Task 3: Client/Profile/Version/Session Model

**Files:**
- Create: `daemon/build.gradle.kts`
- Create: `daemon/src/test/kotlin/com/minekube/craftless/daemon/ClientSessionServiceTest.kt`
- Create: `daemon/src/main/kotlin/com/minekube/craftless/daemon/ClientSessionService.kt`
- Create: `protocol/src/main/kotlin/com/minekube/craftless/protocol/ClientModels.kt`

- [ ] **Step 1: Write the failing session service test**

```kotlin
@Test
fun `offline session creates running client with generated api route`() {
    val service = ClientSessionService.inMemory()
    val client = service.createClient(
        CreateClientRequest(id = "alice", version = "1.21.4", loader = Loader.FABRIC, profile = Profile.offline("Alice"))
    )

    assertEquals("alice", client.id)
    assertEquals(ClientState.RUNNING, client.state)
    assertEquals("/clients/alice/events", service.routesFor("alice").first { it.path.endsWith("/events") }.path)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mise exec -- gradle :daemon:test --tests com.minekube.craftless.daemon.ClientSessionServiceTest`

Expected: FAIL because daemon models and service are missing.

- [ ] **Step 3: Write minimal implementation**

Add `Version`, `Loader`, `Profile`, `Instance`, `Client`, `ClientState`, `CreateClientRequest`, and an in-memory session service that creates per-client management routes.

- [ ] **Step 4: Run test to verify it passes**

Run: `mise exec -- gradle :daemon:test --tests com.minekube.craftless.daemon.ClientSessionServiceTest`

Expected: PASS.

## Task 4: Bridge Backend Boundary

**Files:**
- Create: `bridge-hmc/build.gradle.kts`
- Create: `bridge-hmc/src/test/kotlin/com/minekube/craftless/bridge/hmc/HmcBridgeBackendTest.kt`
- Create: `bridge-hmc/src/main/kotlin/com/minekube/craftless/bridge/hmc/HmcBridgeBackend.kt`
- Create: `docs/bridge-limitations.md`

- [ ] **Step 1: Write the failing bridge mapping test**

```kotlin
@Test
fun `bridge maps craftless chat action without exposing hmc command names`() {
    val backend = HmcBridgeBackend.dryRun()
    val result = backend.chat("alice", "hello")

    assertEquals(ClientAction.CHAT, result.action)
    assertFalse(result.publicDescription.contains("hmc", ignoreCase = true))
    assertTrue(result.internalCommand.redacted().contains("<internal bridge command>"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mise exec -- gradle :bridge-hmc:test --tests com.minekube.craftless.bridge.hmc.HmcBridgeBackendTest`

Expected: FAIL because the bridge module does not exist.

- [ ] **Step 3: Write minimal implementation**

Add internal-only command mapping for connect, chat, move, jump, look, render text, UI dump, and UI click. Public return values use Craftless action names only.

- [ ] **Step 4: Run test to verify it passes**

Run: `mise exec -- gradle :bridge-hmc:test --tests com.minekube.craftless.bridge.hmc.HmcBridgeBackendTest`

Expected: PASS.

## Task 5: Minimal Local Minecraft Server Fixture Strategy

**Files:**
- Create: `testkit/src/main/kotlin/com/minekube/craftless/testkit/LocalServerFixture.kt`
- Create: `testkit/src/test/kotlin/com/minekube/craftless/testkit/LocalServerFixtureTest.kt`

- [ ] **Step 1: Write failing fixture metadata test**

Test that the fixture writes `server.properties` with `online-mode=false`, a pinned port, a logs directory, and a bounded stop timeout.

- [ ] **Step 2: Run test to verify it fails**

Run: `mise exec -- gradle :testkit:test --tests com.minekube.craftless.testkit.LocalServerFixtureTest`

Expected: FAIL because the fixture is missing.

- [ ] **Step 3: Write minimal implementation**

Create a fixture model and file writer. Do not download Paper in the default unit test.

- [ ] **Step 4: Run test to verify it passes**

Run: `mise exec -- gradle :testkit:test --tests com.minekube.craftless.testkit.LocalServerFixtureTest`

Expected: PASS.

## Task 6: Real Offline Client Launch Plan And Smoke Test Harness

**Files:**
- Create: `bridge-hmc/src/main/kotlin/com/minekube/craftless/bridge/hmc/RealClientSmoke.kt`
- Create: `bridge-hmc/src/test/kotlin/com/minekube/craftless/bridge/hmc/RealClientSmokePlanTest.kt`
- Modify: `docs/bridge-limitations.md`

- [ ] **Step 1: Write failing smoke plan test**

Test that an opt-in smoke plan includes server start, client launch, API start, connect, chat, move, server join assertion, chat assertion, position-change assertion, and artifact collection.

- [ ] **Step 2: Run test to verify it fails**

Run: `mise exec -- gradle :bridge-hmc:test --tests com.minekube.craftless.bridge.hmc.RealClientSmokePlanTest`

Expected: FAIL because `RealClientSmokePlan` is missing.

- [ ] **Step 3: Write minimal implementation**

Add an opt-in plan object and guard execution behind `CRAFTLESS_REAL_CLIENT_SMOKE=1`. The first implementation may describe commands and artifact paths without launching Minecraft in unit tests.

- [ ] **Step 4: Verify unit tests pass**

Run: `mise exec -- gradle :bridge-hmc:test`

Expected: PASS without `CRAFTLESS_REAL_CLIENT_SMOKE`.

## Task 7: Fabric Driver Spike Plan

**Files:**
- Create: `driver-api/build.gradle.kts`
- Create: `driver-api/src/main/kotlin/com/minekube/craftless/driver/api/DriverApi.kt`
- Create: `docs/superpowers/specs/2026-06-25-fabric-driver-spike-notes.md`

- [ ] **Step 1: Write failing API shape test**

Test that the driver API exposes ready, connect, disconnect, chat, player position, move, jump, look, raycast, nearby blocks, nearby entities, screen, click, events, and stop as discovered actions rather than static public action routes.

- [ ] **Step 2: Run test to verify it fails**

Run: `mise exec -- gradle :driver-api:test`

Expected: FAIL because the driver API module is missing.

- [ ] **Step 3: Write minimal interfaces**

Add stable interfaces only. Keep Fabric Loom details behind the Fabric driver
module and avoid adding one public driver subproject per Minecraft version
unless build/runtime constraints require it.

- [ ] **Step 4: Verify**

Run: `mise exec -- gradle :driver-api:test`

Expected: PASS.

## Task 8: Adaptive JVM CLI Command Design And Output Contracts

**Files:**
- Create: `cli/build.gradle.kts`
- Create: `cli/src/test/kotlin/com/minekube/craftless/cli/CraftlessCliTest.kt`
- Create: `cli/src/main/kotlin/com/minekube/craftless/cli/Main.kt`

- [ ] **Step 1: Write failing CLI help and dynamic dispatch tests**

Test `craftless --help`, `craftless server start`, `craftless clients create`, `craftless clients list`,
`craftless clients NAME get`, `craftless clients NAME connect`, `craftless clients NAME stop`,
`craftless clients NAME openapi`, `craftless clients NAME actions`, and the stable generic
runner `craftless clients NAME run player.move --arg forward=true --arg ticks=20`.
Add a fake per-client OpenAPI/actions fixture and test that
`craftless clients NAME player move --forward --ticks 20` and
`craftless clients NAME player move --help` are resolved from that runtime metadata
rather than from static action commands.

- [ ] **Step 2: Run test to verify it fails**

Run: `mise exec -- gradle :cli:test --tests com.minekube.craftless.cli.CraftlessCliTest`

Expected: FAIL because CLI module is missing.

- [ ] **Step 3: Write minimal implementation**

Use Clikt for the static core and Mordant for terminal output. Keep setup,
daemon, config, client creation/listing, OpenAPI/action discovery, output flags,
and generic `clients NAME run ACTION` as handwritten commands. Add a dynamic
dispatcher after the stable `clients NAME` prefix that loads the target
client's OpenAPI/actions metadata, maps remaining tokens to action aliases, and
generates `--help` from action summaries/descriptions/arg schemas. Do not
generate Kotlin CLI source per action, and do not claim real Minecraft launch
from the CLI until the bridge smoke is wired.

- [ ] **Step 4: Verify**

Run: `mise exec -- gradle :cli:test`

Expected: PASS.

## Task 9: Playwright/Vitest Integration Plan

**Files:**
- Create: `playwright/package.json`
- Create: `playwright/src/index.ts`

- [x] **Step 1: Write failing package smoke tests**

Test that Playwright fixtures accept injected automation clients rather than
parsing human CLI output.

- [x] **Step 2: Run test to verify it fails**

Run: `mise exec -- bun test playwright`

Expected: FAIL until the package is scaffolded.

- [x] **Step 3: Write minimal implementation**

Create fixture and matcher helpers that do not depend on a checked-in external
SDK package.

- [x] **Step 4: Verify**

Run: `mise exec -- bun test playwright`

Expected: PASS.

## Risks, Blockers, And Research Checkpoints

- Project dependencies and tool versions are managed through `.mise.toml`; verification must use `mise exec -- gradle` and `mise exec -- bun`, not globally installed Gradle, Java, Bun, npm, or node.
- The bridge backend cannot be called robust movement. It uses simulated commands underneath and must stay labelled as bridge-only.
- The real-client smoke requires Java, network downloads, a local server jar, Fabric/HMC-Specifics artifacts, and possibly Xvfb or HeadlessMC headless mode. It must be opt-in.
- Research checkpoints: inspect HeadlessMC launch/cache code, HMC-Specifics command runtime, Fabric Loom examples, MC-Runtime-Test CI setup, and Playwright fixture patterns before implementing the real smoke runner.
- Do not add PrismLauncher integration in this phase.
- Do not use Mineflayer or any protocol-only bot as milestone evidence.

## Exact Verification Commands

```bash
mise exec -- gradle test
mise exec -- gradle :protocol:test :daemon:test :bridge-hmc:test
CRAFTLESS_REAL_CLIENT_SMOKE=1 mise exec -- gradle :bridge-hmc:realClientSmoke
```

The default `gradle test` command must not launch Minecraft. The opt-in smoke is the only command allowed to download server/client artifacts and launch a real client.
