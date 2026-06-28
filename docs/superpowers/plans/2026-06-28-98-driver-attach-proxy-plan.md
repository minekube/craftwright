# Driver Attach Proxy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a launched prepared client attach a generic HTTP-backed driver session to the supervisor.

**Architecture:** Add a daemon attach boundary that replaces the current `DriverSession` for an existing client. Add `HttpDriverSession` as a Ktor Client proxy to a loopback in-client driver endpoint. Add `POST /clients/{id}:attach` as supervisor lifecycle plumbing; generated gameplay APIs still come from the attached driver's runtime graph/actions.

**Tech Stack:** Kotlin/JVM, Ktor Server, Ktor Client, kotlinx.serialization JSON, existing daemon tests through Gradle and mise.

---

### Task 1: Add Red Service Attach Test

**Files:**
- Modify: `daemon/src/test/kotlin/com/minekube/craftless/daemon/ClientSessionServiceTest.kt`

- [x] **Step 1: Add test**

  Add a test named `attached driver replaces prepared session as openapi authority`:

  ```kotlin
  val service =
      ClientSessionService.inMemory(
          DriverSessionFactory { request -> PreparedOnlyTestDriverSession(request.id) },
      )
  service.createClient(CreateClientRequest(id = "alice", version = "1.21.6", loader = Loader.FABRIC, profile = Profile.offline("Alice")))
  assertEquals("craftless-prepared-client-runtime", service.openApiFor("alice").extensions["x-craftless-driver"])

  service.attachDriver("alice", AttachedTestDriverSession("alice"))

  val document = service.openApiFor("alice")
  assertEquals("craftless-driver-fabric", document.extensions["x-craftless-driver"])
  assertTrue(document.actions.any { it.id == "player.chat" })
  ```

- [x] **Step 2: Run red test**

  ```sh
  mise exec -- gradle :daemon:test --tests '*ClientSessionServiceTest.attached driver replaces prepared session as openapi authority*'
  ```

  Expected: compile failure because `attachDriver` does not exist.

### Task 2: Implement Session Replacement

**Files:**
- Modify: `daemon/src/main/kotlin/com/minekube/craftless/daemon/ClientSessionService.kt`

- [x] **Step 1: Add `attachDriver`**

  Add:

  ```kotlin
  fun attachDriver(clientId: String, driver: DriverSession): Client {
      require(clients.containsKey(clientId)) { "client $clientId not found" }
      require(driver.clientId == clientId) { "attached driver client id must match $clientId" }
      drivers[clientId] = driver
      return updateState(clientId, driver.snapshot().state)
  }
  ```

- [x] **Step 2: Run focused green**

  ```sh
  mise exec -- gradle :daemon:test --tests '*ClientSessionServiceTest.attached driver replaces prepared session as openapi authority*'
  ```

### Task 3: Add HTTP Driver Session Proxy

**Files:**
- Create: `daemon/src/main/kotlin/com/minekube/craftless/daemon/HttpDriverSession.kt`
- Modify: `daemon/src/test/kotlin/com/minekube/craftless/daemon/LocalSessionApiServerTest.kt`

- [x] **Step 1: Add red HTTP attach test**

  Add a test named `server attach proxies generated run calls to remote driver endpoint`.
  It should start a fake Ktor loopback driver endpoint with:

  - `GET /driver/snapshot`
  - `GET /driver/actions`
  - `GET /driver/runtime-metadata`
  - `GET /driver/runtime-graph`
  - `POST /driver/invoke`
  - `POST /driver/connect`
  - `POST /driver/stop`
  - `GET /driver/events`

  Then create `alice`, call `POST /clients/alice:attach` with
  `{"endpoint":"http://127.0.0.1:<port>/driver"}`, fetch
  `/clients/alice/openapi.json`, and call `POST /clients/alice:run` for an
  action advertised by the remote endpoint.

- [x] **Step 2: Run red HTTP test**

  ```sh
  mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.server attach proxies generated run calls to remote driver endpoint*'
  ```

- [x] **Step 3: Implement `HttpDriverSession`**

  Implement `DriverSession` using Ktor Client `CIO` and `runBlocking` for the
  synchronous driver interface. Endpoint paths:

  - `GET $endpoint/snapshot`
  - `POST $endpoint/connect`
  - `GET $endpoint/actions`
  - `GET $endpoint/runtime-metadata`
  - `GET $endpoint/runtime-graph`
  - `POST $endpoint/invoke`
  - `POST $endpoint/stop`
  - `GET $endpoint/events`

  Return `DriverOperationAdapters.empty()`; generated invocation falls back to
  remote `invoke` when an operation has no local adapter.

- [x] **Step 4: Add attach route**

  Add `DriverAttachRequest(endpoint: String)` and `POST /clients/{id}:attach`
  in `LocalSessionApiServer`. Validate nonblank endpoint, attach
  `HttpDriverSession(clientId, endpoint)`, emit `client.attached`, and return
  the updated client.

- [x] **Step 5: Run focused green**

  ```sh
  mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.server attach proxies generated run calls to remote driver endpoint*'
  ```

### Task 4: Register Phase 98 And Verify

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/evidence/2026-06-28-driver-attach-proxy.md`

- [x] **Step 1: Register Phase 98**

  Add Phase 98 to `AGENTS.md` and the checklist as attach/proxy plumbing.

- [x] **Step 2: Run local gates**

  ```sh
  git diff --check
  mise exec -- gradle :daemon:test
  mise exec -- gradle :daemon:ktlintCheck :daemon:detekt
  ```

- [x] **Step 3: Record evidence**

  Write red/green and local gate outcomes to
  `docs/superpowers/evidence/2026-06-28-driver-attach-proxy.md`.

### Task 5: Commit And Push

**Files:**
- All modified files from Tasks 1-4

- [x] **Step 1: Commit and push**

  ```sh
  git add AGENTS.md daemon/src/main/kotlin/com/minekube/craftless/daemon/ClientSessionService.kt daemon/src/main/kotlin/com/minekube/craftless/daemon/HttpDriverSession.kt daemon/src/main/kotlin/com/minekube/craftless/daemon/LocalSessionApiServer.kt daemon/src/test/kotlin/com/minekube/craftless/daemon/ClientSessionServiceTest.kt daemon/src/test/kotlin/com/minekube/craftless/daemon/LocalSessionApiServerTest.kt docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-98-driver-attach-proxy-design.md docs/superpowers/plans/2026-06-28-98-driver-attach-proxy-plan.md docs/superpowers/evidence/2026-06-28-driver-attach-proxy.md
  git commit -m "daemon: attach remote driver sessions"
  git push origin main
  ```

## Self-Review

- Spec coverage: session replacement, HTTP proxy, attach route, tests,
  evidence, gates, and push are covered.
- Placeholder scan: no TODO/TBD placeholders.
- Scope: no gameplay action catalog, static descriptor family, Fabric binding,
  scenario shortcut, version-specific public API, or completion claim.
