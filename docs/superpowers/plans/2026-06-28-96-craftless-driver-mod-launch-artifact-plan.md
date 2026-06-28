# Craftless Driver Mod Launch Artifact Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the prepared runtime launch path include a configured Craftless Fabric driver mod as a cached Fabric mod artifact.

**Architecture:** Add a daemon-owned `ClientRuntimeDriverModProvider` boundary that returns optional local mod paths by loader. `WorkspaceClientRuntimeDriverFactory.prepare` copies configured mod jars into the workspace cache as `FABRIC_MOD` artifacts and rebuilds the launch plan from the augmented artifact list. The daemon remains independent from `driver-fabric`; distribution/CLI packaging can supply the concrete jar path later.

**Tech Stack:** Kotlin/JVM, java.nio.file, existing cache DTOs, daemon tests through Gradle and mise.

---

### Task 1: Add Red Prepared Runtime Test

**Files:**
- Modify: `daemon/src/test/kotlin/com/minekube/craftless/daemon/LocalSessionApiServerTest.kt`

- [x] **Step 1: Add a focused test**

  Add a test near the existing prepared runtime server test:

  ```kotlin
  @Test
  fun `prepared runtime launch plan includes configured craftless fabric driver mod`() =
      withHttpClient { http ->
          val workspace = Files.createTempDirectory("craftless-driver-mod-launch")
          val driverMod = Files.createTempFile("craftless-driver-fabric", ".jar")
          Files.writeString(driverMod, "craftless-driver-mod")
          val launcher = RecordingClientRuntimeLauncher()

          LocalSessionApiServer
              .inMemory(
                  workspaceRoot = workspace,
                  cacheMetadataFetcher = preparedRuntimeMetadataFetcher(),
                  clientRuntimeLauncher = launcher,
                  clientRuntimeDriverModProvider =
                      StaticClientRuntimeDriverModProvider(fabric = driverMod),
              ).use { server ->
                  server.start()

                  val response =
                      http.post(server.url("/clients")) {
                          contentType(ContentType.Application.Json)
                          setBody(
                              """
                              {
                                "id": "alice",
                                "version": "1.21.6",
                                "loader": "FABRIC",
                                "profile": { "kind": "OFFLINE", "name": "Alice" }
                              }
                              """.trimIndent(),
                          )
                      }

                  assertEquals(HttpStatusCode.Created, response.status)
                  val launch = launcher.launches.single()
                  val driverHandle =
                      launch.prepared.artifacts.single {
                          it.kind == CachePreparedArtifactKind.FABRIC_MOD &&
                              it.source == driverMod.toUri().toString()
                      }.handle
                  assertTrue(launch.launch.mods.contains(driverHandle))
                  assertEquals("craftless-driver-mod", Files.readString(workspace.resolve(driverHandle)))
              }
      }
  ```

- [x] **Step 2: Run red test**

  ```sh
  mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.prepared runtime launch plan includes configured craftless fabric driver mod*'
  ```

  Expected: compile/test failure because the provider API does not exist yet.

### Task 2: Implement Generic Driver Mod Provider

**Files:**
- Modify: `daemon/src/main/kotlin/com/minekube/craftless/daemon/WorkspaceClientRuntimeDriverFactory.kt`
- Modify: `daemon/src/main/kotlin/com/minekube/craftless/daemon/LocalSessionApiServer.kt`

- [x] **Step 1: Add provider boundary**

  In `WorkspaceClientRuntimeDriverFactory.kt`, add:

  ```kotlin
  fun interface ClientRuntimeDriverModProvider {
      fun modFor(loader: Loader): Path?
  }

  object NoClientRuntimeDriverModProvider : ClientRuntimeDriverModProvider {
      override fun modFor(loader: Loader): Path? = null
  }
  ```

- [x] **Step 2: Inject provider into workspace factory and server**

  Add `driverModProvider: ClientRuntimeDriverModProvider =
  NoClientRuntimeDriverModProvider` to `WorkspaceClientRuntimeDriverFactory`
  and `clientRuntimeDriverModProvider: ClientRuntimeDriverModProvider =
  NoClientRuntimeDriverModProvider` to `LocalSessionApiServer.inMemory`, then
  pass it into the workspace runtime factory.

- [x] **Step 3: Cache configured mod artifacts**

  After cache preparation in `WorkspaceClientRuntimeDriverFactory.prepare`, call
  a helper that copies the provider path into `cache/mods/craftless/<sha>.jar`,
  creates a `CachePreparedArtifact(kind = FABRIC_MOD, source =
  path.toUri().toString(), status = CACHED)`, appends it to the artifact list,
  and rebuilds `CacheLaunchPlan.fromArtifacts(artifacts)`.

- [x] **Step 4: Run focused green**

  ```sh
  mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest.prepared runtime launch plan includes configured craftless fabric driver mod*'
  ```

### Task 3: Register Phase 96 And Verify

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Create: `docs/superpowers/evidence/2026-06-28-craftless-driver-mod-launch-artifact.md`

- [x] **Step 1: Register Phase 96**

  Add Phase 96 to `AGENTS.md` and the project checklist, stating that the
  configured Craftless driver mod is launch artifact wiring and not a static
  gameplay API.

- [x] **Step 2: Run local gates**

  ```sh
  git diff --check
  mise exec -- gradle :daemon:test
  mise exec -- gradle :daemon:ktlintCheck :daemon:detekt
  ```

- [x] **Step 3: Record evidence**

  Write the red/green and local gate output summary to
  `docs/superpowers/evidence/2026-06-28-craftless-driver-mod-launch-artifact.md`.

### Task 4: Commit And Push

**Files:**
- All modified files from Tasks 1-3

- [x] **Step 1: Commit and push**

  ```sh
  git add AGENTS.md docs/project-completion-checklist.md docs/superpowers/specs/2026-06-28-96-craftless-driver-mod-launch-artifact-design.md docs/superpowers/plans/2026-06-28-96-craftless-driver-mod-launch-artifact-plan.md docs/superpowers/evidence/2026-06-28-craftless-driver-mod-launch-artifact.md daemon/src/main/kotlin/com/minekube/craftless/daemon/WorkspaceClientRuntimeDriverFactory.kt daemon/src/main/kotlin/com/minekube/craftless/daemon/LocalSessionApiServer.kt daemon/src/test/kotlin/com/minekube/craftless/daemon/LocalSessionApiServerTest.kt
  git commit -m "daemon: include configured driver mod in launch plan"
  git push origin main
  ```

## Self-Review

- Spec coverage: provider boundary, cache artifact, launch-plan mods, tests,
  docs, gates, and push are covered.
- Placeholder scan: no TODO/TBD placeholders.
- Scope: no public gameplay actions, static descriptors, version-specific API,
  or new support claim.
