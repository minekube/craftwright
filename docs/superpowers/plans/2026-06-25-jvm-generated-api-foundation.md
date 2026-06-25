# JVM Generated API Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Craftwright's Kotlin/JVM foundation with Craftwright-owned generated API contracts and a documented bridge path to the first real-client smoke test.

**Architecture:** Use a Gradle Kotlin multi-project skeleton as the only implementation path. The first executable JVM slice contains protocol DTOs, OpenAPI route generation over fake Minecraft objects, a fake client/session model, and a bridge backend interface that hides HeadlessMC/HMC-Specifics command strings behind Craftwright-owned routes.

**Tech Stack:** Gradle Kotlin DSL, Kotlin 2.4.0, kotlinx.serialization 1.11.0, kotlinx.coroutines 1.11.0, JUnit 5, Kotest 6.2.1, Clikt 5.1.0, Ktor Server/Client 3.5.0, Java 21, and Bun for TypeScript package/test execution. Fabric Loom and TypeScript packages are planned as later modules after this foundation compiles.

---

## File Structure

- Create `settings.gradle.kts`: Gradle project name and modules.
- Create `build.gradle.kts`: shared repositories, Kotlin/JVM defaults, Java 21 toolchain, test setup, dependency versions.
- Create `protocol/`: serializable API models, route metadata, OpenAPI emitter, fake root registration tests.
- Create `testkit/`: fake Minecraft client/session objects for route and API tests.
- Create `daemon/`: local session API route table and in-memory session service.
- Create `bridge-hmc/`: temporary HeadlessMC/HMC-Specifics bridge interface, limitation docs, and command mapping kept internal.
- Create `cli/`: JVM `mcw` entrypoint and first command tree tests.
- Create `docs/bridge-limitations.md`: public warning that bridge movement is simulated and not the final Fabric driver.
- Later create `driver-api/`, `driver-runtime/`, `driver-fabric-1_21_6/`, `ts-sdk/`, and `playwright/` once the bridge-backed skeleton is verified.

## Task 1: Gradle/Kotlin Foundation

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `protocol/build.gradle.kts`
- Create: `protocol/src/test/kotlin/dev/minekube/craftwright/protocol/ApiRouteCatalogTest.kt`
- Create: `protocol/src/main/kotlin/dev/minekube/craftwright/protocol/ApiRoute.kt`
- Create: `protocol/src/main/kotlin/dev/minekube/craftwright/protocol/OpenApiDocument.kt`

- [ ] **Step 1: Write the failing route catalog test**

```kotlin
package dev.minekube.craftwright.protocol

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
        assertEquals("GET", catalog.route("/client").method)
        assertEquals("GET", catalog.route("/client/state").method)
        assertEquals("GET", catalog.route("/player").method)
        assertEquals("GET", catalog.route("/player/name").method)
        assertEquals("POST", catalog.route("/player/sendChat").method)
        assertEquals("POST", catalog.route("/clients/{id}/connection/connect").method)
        assertEquals("POST", catalog.route("/clients/{id}/player/sendChat").method)
        assertEquals("GET", catalog.route("/clients/{id}/player").method)
        assertEquals("POST", catalog.route("/clients/{id}/stop").method)
        assertTrue(catalog.routes.any { it.path == "/o/{handle}" })
        assertTrue(catalog.routes.any { it.path == "/c/{className}" })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mise exec -- gradle :protocol:test --tests dev.minekube.craftwright.protocol.ApiRouteCatalogTest`

Expected: FAIL because `ApiRouteCatalog` and Gradle module files are not implemented yet.

- [ ] **Step 3: Write minimal implementation**

Implement `ApiRoute`, `ApiRouteCatalog.sessionDefaults()`, route lookup, and a small serializable OpenAPI document model that can list operations for the default routes. Keep HTTP verbs as protocol data strings such as `"GET"` and `"POST"` rather than introducing a Craftwright-owned HTTP method enum.

- [ ] **Step 4: Run test to verify it passes**

Run: `mise exec -- gradle :protocol:test --tests dev.minekube.craftwright.protocol.ApiRouteCatalogTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts build.gradle.kts protocol
git commit -m "feat: add JVM protocol foundation"
```

## Task 2: OpenAPI Generation From Fake Objects

**Files:**
- Create: `testkit/build.gradle.kts`
- Create: `testkit/src/main/kotlin/dev/minekube/craftwright/testkit/FakeMinecraftClient.kt`
- Create: `protocol/src/test/kotlin/dev/minekube/craftwright/protocol/OpenApiGenerationTest.kt`
- Modify: `protocol/src/main/kotlin/dev/minekube/craftwright/protocol/ApiRoute.kt`
- Modify: `protocol/src/main/kotlin/dev/minekube/craftwright/protocol/OpenApiDocument.kt`

- [ ] **Step 1: Write the failing OpenAPI test**

```kotlin
@Test
fun `openapi document includes craftwright metadata for fake player routes`() {
    val document = OpenApiDocument.from(ApiRouteCatalog.sessionDefaults())

    val operation = document.paths["/player/sendChat"]?.post
    assertEquals("playerSendChat", operation?.operationId)
    assertEquals("dev.minekube.craftwright.player", operation?.extensions?.get("x-craftwright-java-class"))
    assertEquals("client", operation?.extensions?.get("x-craftwright-thread"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mise exec -- gradle :protocol:test --tests dev.minekube.craftwright.protocol.OpenApiGenerationTest`

Expected: FAIL because OpenAPI conversion does not include operation metadata yet.

- [ ] **Step 3: Write minimal implementation**

Add operation IDs, tags, source class/method metadata, client-thread metadata, and JSON-serializable path entries.

- [ ] **Step 4: Run test to verify it passes**

Run: `mise exec -- gradle :protocol:test --tests dev.minekube.craftwright.protocol.OpenApiGenerationTest`

Expected: PASS.

## Task 3: Client/Profile/Version/Session Model

**Files:**
- Create: `daemon/build.gradle.kts`
- Create: `daemon/src/test/kotlin/dev/minekube/craftwright/daemon/ClientSessionServiceTest.kt`
- Create: `daemon/src/main/kotlin/dev/minekube/craftwright/daemon/ClientSessionService.kt`
- Create: `protocol/src/main/kotlin/dev/minekube/craftwright/protocol/ClientModels.kt`

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

Run: `mise exec -- gradle :daemon:test --tests dev.minekube.craftwright.daemon.ClientSessionServiceTest`

Expected: FAIL because daemon models and service are missing.

- [ ] **Step 3: Write minimal implementation**

Add `Version`, `Loader`, `Profile`, `Instance`, `Client`, `ClientState`, `CreateClientRequest`, and an in-memory session service that creates per-client management routes.

- [ ] **Step 4: Run test to verify it passes**

Run: `mise exec -- gradle :daemon:test --tests dev.minekube.craftwright.daemon.ClientSessionServiceTest`

Expected: PASS.

## Task 4: Bridge Backend Boundary

**Files:**
- Create: `bridge-hmc/build.gradle.kts`
- Create: `bridge-hmc/src/test/kotlin/dev/minekube/craftwright/bridge/hmc/HmcBridgeBackendTest.kt`
- Create: `bridge-hmc/src/main/kotlin/dev/minekube/craftwright/bridge/hmc/HmcBridgeBackend.kt`
- Create: `docs/bridge-limitations.md`

- [ ] **Step 1: Write the failing bridge mapping test**

```kotlin
@Test
fun `bridge maps craftwright chat action without exposing hmc command names`() {
    val backend = HmcBridgeBackend.dryRun()
    val result = backend.chat("alice", "hello")

    assertEquals(ClientAction.CHAT, result.action)
    assertFalse(result.publicDescription.contains("hmc", ignoreCase = true))
    assertTrue(result.internalCommand.redacted().contains("<internal bridge command>"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mise exec -- gradle :bridge-hmc:test --tests dev.minekube.craftwright.bridge.hmc.HmcBridgeBackendTest`

Expected: FAIL because the bridge module does not exist.

- [ ] **Step 3: Write minimal implementation**

Add internal-only command mapping for connect, chat, move, jump, look, render text, UI dump, and UI click. Public return values use Craftwright action names only.

- [ ] **Step 4: Run test to verify it passes**

Run: `mise exec -- gradle :bridge-hmc:test --tests dev.minekube.craftwright.bridge.hmc.HmcBridgeBackendTest`

Expected: PASS.

## Task 5: Minimal Local Minecraft Server Fixture Strategy

**Files:**
- Create: `testkit/src/main/kotlin/dev/minekube/craftwright/testkit/LocalServerFixture.kt`
- Create: `testkit/src/test/kotlin/dev/minekube/craftwright/testkit/LocalServerFixtureTest.kt`

- [ ] **Step 1: Write failing fixture metadata test**

Test that the fixture writes `server.properties` with `online-mode=false`, a pinned port, a logs directory, and a bounded stop timeout.

- [ ] **Step 2: Run test to verify it fails**

Run: `mise exec -- gradle :testkit:test --tests dev.minekube.craftwright.testkit.LocalServerFixtureTest`

Expected: FAIL because the fixture is missing.

- [ ] **Step 3: Write minimal implementation**

Create a fixture model and file writer. Do not download Paper in the default unit test.

- [ ] **Step 4: Run test to verify it passes**

Run: `mise exec -- gradle :testkit:test --tests dev.minekube.craftwright.testkit.LocalServerFixtureTest`

Expected: PASS.

## Task 6: Real Offline Client Launch Plan And Smoke Test Harness

**Files:**
- Create: `bridge-hmc/src/main/kotlin/dev/minekube/craftwright/bridge/hmc/RealClientSmoke.kt`
- Create: `bridge-hmc/src/test/kotlin/dev/minekube/craftwright/bridge/hmc/RealClientSmokePlanTest.kt`
- Modify: `docs/bridge-limitations.md`

- [ ] **Step 1: Write failing smoke plan test**

Test that an opt-in smoke plan includes server start, client launch, API start, connect, chat, move, server join assertion, chat assertion, position-change assertion, and artifact collection.

- [ ] **Step 2: Run test to verify it fails**

Run: `mise exec -- gradle :bridge-hmc:test --tests dev.minekube.craftwright.bridge.hmc.RealClientSmokePlanTest`

Expected: FAIL because `RealClientSmokePlan` is missing.

- [ ] **Step 3: Write minimal implementation**

Add an opt-in plan object and guard execution behind `CRAFTWRIGHT_REAL_CLIENT_SMOKE=1`. The first implementation may describe commands and artifact paths without launching Minecraft in unit tests.

- [ ] **Step 4: Verify unit tests pass**

Run: `mise exec -- gradle :bridge-hmc:test`

Expected: PASS without `CRAFTWRIGHT_REAL_CLIENT_SMOKE`.

## Task 7: Fabric Driver Spike Plan

**Files:**
- Create: `driver-api/build.gradle.kts`
- Create: `driver-api/src/main/kotlin/dev/minekube/craftwright/driver/api/DriverApi.kt`
- Create: `docs/superpowers/specs/2026-06-25-fabric-driver-spike-notes.md`

- [ ] **Step 1: Write failing API shape test**

Test that the driver API exposes ready, connect, disconnect, send chat, player position, move, jump, look, raycast, nearby blocks, nearby entities, screen, click, events, and stop capabilities.

- [ ] **Step 2: Run test to verify it fails**

Run: `mise exec -- gradle :driver-api:test`

Expected: FAIL because the driver API module is missing.

- [ ] **Step 3: Write minimal interfaces**

Add stable interfaces only. Do not add Fabric Loom until a real Fabric module is ready.

- [ ] **Step 4: Verify**

Run: `mise exec -- gradle :driver-api:test`

Expected: PASS.

## Task 8: JVM CLI Command Design And Output Contracts

**Files:**
- Create: `cli/build.gradle.kts`
- Create: `cli/src/test/kotlin/dev/minekube/craftwright/cli/McwCliTest.kt`
- Create: `cli/src/main/kotlin/dev/minekube/craftwright/cli/Main.kt`

- [ ] **Step 1: Write failing CLI help test**

Test `mcw --help`, `mcw versions`, `mcw profiles`, `mcw clients create`, `mcw clients list`, `mcw clients connect`, `mcw clients api`, `mcw server start`, and `mcw test run` command registration.

- [ ] **Step 2: Run test to verify it fails**

Run: `mise exec -- gradle :cli:test --tests dev.minekube.craftwright.cli.McwCliTest`

Expected: FAIL because CLI module is missing.

- [ ] **Step 3: Write minimal implementation**

Use Clikt to register commands and output JSON/plain help-safe placeholders. Do not claim real Minecraft launch from the CLI until the bridge smoke is wired.

- [ ] **Step 4: Verify**

Run: `mise exec -- gradle :cli:test`

Expected: PASS.

## Task 9: TypeScript SDK And Playwright/Vitest Integration Plan

**Files:**
- Create: `ts-sdk/package.json`
- Create: `ts-sdk/src/index.ts`
- Create: `playwright/package.json`
- Create: `playwright/src/index.ts`
- Create: `docs/superpowers/specs/2026-06-25-typescript-sdk-plan.md`

- [x] **Step 1: Write failing package smoke tests**

Test that SDK methods map to daemon/session routes and Playwright fixtures call the SDK rather than parsing human CLI output.

- [x] **Step 2: Run test to verify it fails**

Run: `mise exec -- bun test ts-sdk && mise exec -- bun test playwright`

Expected: FAIL until packages are scaffolded.

- [x] **Step 3: Write minimal implementation**

Create typed client stubs for start, launch, connect, chat, waitForChat, player, and stop.

- [x] **Step 4: Verify**

Run: `mise exec -- bun test ts-sdk && mise exec -- bun test playwright`

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
CRAFTWRIGHT_REAL_CLIENT_SMOKE=1 mise exec -- gradle :bridge-hmc:realClientSmoke
```

The default `gradle test` command must not launch Minecraft. The opt-in smoke is the only command allowed to download server/client artifacts and launch a real client.
