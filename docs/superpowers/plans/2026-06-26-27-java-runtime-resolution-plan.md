# Java Runtime Resolution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Prism-inspired, Craftless-owned Java runtime resolution so Minecraft versions launch with a validated compatible Java runtime instead of accidentally using the repository build JVM.

**Architecture:** Add protocol DTOs for Java runtime requirements, descriptors, and selections; implement a daemon-side resolver with explicit, managed-cache, mise, and system providers; integrate the selected runtime into cache manifests, launch plans, testkit smoke, CLI, and supervisor metadata without changing gameplay APIs.

**Tech Stack:** Kotlin/JVM, kotlinx.serialization DTOs, Ktor Server/Client, Gradle through mise, Bun through mise for helper tests, Java process validation through bounded `ProcessBuilder`.

---

### Task 1: Protocol Runtime Selection Models

**Files:**
- Modify: `protocol/src/main/kotlin/com/minekube/craftless/protocol/CacheModels.kt`
- Test: `protocol/src/test/kotlin/com/minekube/craftless/protocol/CacheModelsTest.kt`

- [ ] **Step 1: Write failing tests for Java selection DTOs**

  Add tests that construct a `JavaRuntimeRequirement`, a
  `JavaRuntimeDescriptor`, rejected candidates, and a `JavaRuntimeSelection`
  for Minecraft `26.2`.

  Required assertions:

  ```kotlin
  assertEquals(25, requirement.majorVersion)
  assertEquals("minecraft-version-metadata", requirement.reason)
  assertEquals(JavaRuntimeProviderKind.MANAGED, selected.provider)
  assertEquals(JavaRuntimeSelectionStatus.SELECTED, selection.status)
  assertTrue(selection.rejected.any { it.reason == "java-major-too-low" })
  ```

- [ ] **Step 2: Run the focused failing test**

  Run:

  ```sh
  mise exec -- gradle :protocol:test --tests '*CacheModelsTest*'
  ```

  Expected: compilation fails because the Java runtime DTOs do not exist yet.

- [ ] **Step 3: Add serializable protocol models**

  Add `@Serializable` DTOs in `CacheModels.kt`:

  ```kotlin
  @Serializable
  data class JavaRuntimeRequirement(
      val majorVersion: Int,
      val component: String? = null,
      val platform: String? = null,
      val architecture: String? = null,
      val sourcePolicy: JavaRuntimeSourcePolicy = JavaRuntimeSourcePolicy.AUTO,
      val reason: String,
  ) {
      init {
          require(majorVersion > 0) { "Java major version must be positive" }
          component?.let { requireFileSafeSegment(it, "Java runtime component") }
      }
  }

  @Serializable
  enum class JavaRuntimeSourcePolicy {
      AUTO,
      CONFIGURED_ONLY,
      MANAGED_ALLOWED,
  }

  @Serializable
  enum class JavaRuntimeProviderKind {
      CONFIGURED,
      MANAGED,
      MISE,
      SYSTEM,
  }

  @Serializable
  data class JavaRuntimeDescriptor(
      val id: String,
      val provider: JavaRuntimeProviderKind,
      val javaHome: String? = null,
      val executable: String,
      val majorVersion: Int,
      val version: String,
      val vendor: String? = null,
      val architecture: String? = null,
      val managed: Boolean = false,
      val evidence: Map<String, String> = emptyMap(),
  )

  @Serializable
  data class RejectedJavaRuntimeCandidate(
      val executable: String,
      val provider: JavaRuntimeProviderKind,
      val reason: String,
      val detectedMajorVersion: Int? = null,
  )

  @Serializable
  data class JavaRuntimeSelection(
      val requirement: JavaRuntimeRequirement,
      val status: JavaRuntimeSelectionStatus,
      val selected: JavaRuntimeDescriptor? = null,
      val rejected: List<RejectedJavaRuntimeCandidate> = emptyList(),
      val reason: String,
  )

  @Serializable
  enum class JavaRuntimeSelectionStatus {
      SELECTED,
      UNSATISFIED,
  }
  ```

- [ ] **Step 4: Add Java selection fields to cache models**

  Extend `CachePrepareRequest`, `CachePrepareResult`, and `CacheLaunchPlan`:

  ```kotlin
  data class CachePrepareRequest(
      val minecraftVersion: String,
      val loader: Loader,
      val loaderVersion: String? = null,
      val java: JavaRuntimeRequirement? = null,
  )
  ```

  `CachePrepareResult` gets `val javaSelection: JavaRuntimeSelection? = null`.
  `CacheLaunchPlan` keeps `javaExecutable` but its value must point at the
  selected descriptor executable when preparation resolves Java.

- [ ] **Step 5: Verify protocol tests**

  Run:

  ```sh
  mise exec -- gradle :protocol:test --tests '*CacheModelsTest*'
  ```

### Task 2: Minecraft Java Requirement Derivation

**Files:**
- Create: `daemon/src/main/kotlin/com/minekube/craftless/daemon/JavaRuntimeRequirementResolver.kt`
- Test: `daemon/src/test/kotlin/com/minekube/craftless/daemon/JavaRuntimeRequirementResolverTest.kt`

- [ ] **Step 1: Write failing metadata tests**

  Cover:

  ```kotlin
  @Test
  fun `derives Java 25 from Minecraft 26 metadata`() {
      val manifest = """{"javaVersion":{"component":"java-runtime-gamma","majorVersion":25}}"""
      val requirement = MinecraftJavaRuntimeRequirementResolver().derive(manifest, "26.2")
      assertEquals(25, requirement.majorVersion)
      assertEquals("java-runtime-gamma", requirement.component)
      assertEquals("minecraft-version-metadata", requirement.reason)
  }
  ```

  Also test that missing metadata falls back to Java 8 with
  `reason = "minecraft-version-metadata-missing"` only for legacy manifests.

- [ ] **Step 2: Implement metadata parsing**

  Add a daemon-internal resolver that reads `javaVersion.majorVersion` and
  `javaVersion.component` from Mojang version metadata and returns
  `JavaRuntimeRequirement`.

- [ ] **Step 3: Verify focused tests**

  Run:

  ```sh
  mise exec -- gradle :daemon:test --tests '*JavaRuntimeRequirementResolverTest*'
  ```

### Task 3: Java Candidate Validation

**Files:**
- Create: `daemon/src/main/kotlin/com/minekube/craftless/daemon/JavaRuntimeValidator.kt`
- Test: `daemon/src/test/kotlin/com/minekube/craftless/daemon/JavaRuntimeValidatorTest.kt`

- [ ] **Step 1: Write fake Java validation tests**

  Create temporary executable scripts that print deterministic `java -version`
  style output for Java 21 and Java 25. Assert the validator extracts major
  version, vendor, and architecture evidence with a bounded timeout.

- [ ] **Step 2: Implement bounded process validation**

  Use `ProcessBuilder(executable.toString(), "-version")`, redirect error
  stream into output, wait with a timeout, and parse versions such as:

  ```text
  openjdk version "25.0.3" 2026-04-21 LTS
  Eclipse Temurin Runtime Environment
  ```

  Do not use Java `HttpClient`, OkHttp, or shell-specific command strings.

- [ ] **Step 3: Verify focused tests**

  Run:

  ```sh
  mise exec -- gradle :daemon:test --tests '*JavaRuntimeValidatorTest*'
  ```

### Task 4: Java Runtime Providers And Resolver

**Files:**
- Create: `daemon/src/main/kotlin/com/minekube/craftless/daemon/JavaRuntimeProvider.kt`
- Create: `daemon/src/main/kotlin/com/minekube/craftless/daemon/JavaRuntimeResolver.kt`
- Test: `daemon/src/test/kotlin/com/minekube/craftless/daemon/JavaRuntimeResolverTest.kt`

- [ ] **Step 1: Write resolver tests first**

  Tests must cover:

  - explicit Java 25 path selected over lower system Java;
  - managed cache Java 25 selected when present;
  - mise Java 25 discovered from a fake `MISE_DATA_DIR`;
  - Java 21 rejected for a Java 25 requirement with reason
    `java-major-too-low`;
  - unsatisfied result is machine-readable when no candidate matches.

- [ ] **Step 2: Implement provider interfaces**

  Add:

  ```kotlin
  interface JavaRuntimeProvider {
      val kind: JavaRuntimeProviderKind
      fun candidates(context: JavaRuntimeDiscoveryContext): List<JavaRuntimeCandidate>
  }
  ```

  Keep provider internals daemon-private. Do not add gameplay API types.

- [ ] **Step 3: Implement mise discovery as optional provider**

  Discover installed runtimes under:

  - `${MISE_DATA_DIR}/installs/java/*/bin/java`;
  - `${HOME}/.local/share/mise/installs/java/*/bin/java`.

  Do not require `mise` to be on `PATH` for product runtime launches.

- [ ] **Step 4: Implement deterministic resolver ordering**

  Resolve in this order: configured, managed cache, mise, system. Select the
  first validated candidate whose major version is at least the requirement
  major. Record all rejected candidates with reasons.

- [ ] **Step 5: Verify focused tests**

  Run:

  ```sh
  mise exec -- gradle :daemon:test --tests '*JavaRuntimeResolverTest*'
  ```

### Task 5: Integrate Resolver Into Cache Preparation

**Files:**
- Modify: `daemon/src/main/kotlin/com/minekube/craftless/daemon/CachePreparationService.kt`
- Test: `daemon/src/test/kotlin/com/minekube/craftless/daemon/CachePreparationServiceTest.kt`
- Test: `cli/src/test/kotlin/com/minekube/craftless/cli/CraftlessCliTest.kt`

- [ ] **Step 1: Write failing cache manifest tests**

  Assert `cache prepare` for a `26.2` manifest writes:

  - `javaSelection.requirement.majorVersion == 25`;
  - `javaSelection.status == SELECTED`;
  - `launch.javaExecutable` equals the selected executable handle/path;
  - Java 21 candidates appear in rejected evidence when present.

- [ ] **Step 2: Replace side-effect Java runtime download with selection**

  Keep Mojang Java runtime download support, but make it produce a managed
  provider candidate and selection. Cache artifacts remain idempotent handles.

- [ ] **Step 3: Keep existing cache exports compatible**

  Ensure `cleanupHandles()` and `exportHandles()` include selected Java runtime
  artifacts and the manifest, without adding absolute host paths to archives.

- [ ] **Step 4: Verify daemon and CLI tests**

  Run:

  ```sh
  mise exec -- gradle :daemon:test --tests '*CachePreparationServiceTest*'
  mise exec -- gradle :cli:test --tests '*CraftlessCliTest*'
  ```

### Task 6: Supervisor API And CLI Runtime Commands

**Files:**
- Modify: `protocol/src/main/kotlin/com/minekube/craftless/protocol/ApiRoute.kt`
- Modify: `daemon/src/main/kotlin/com/minekube/craftless/daemon/LocalSessionApiServer.kt`
- Modify: `cli/src/main/kotlin/com/minekube/craftless/cli/Main.kt`
- Test: `daemon/src/test/kotlin/com/minekube/craftless/daemon/LocalSessionApiServerTest.kt`
- Test: `cli/src/test/kotlin/com/minekube/craftless/cli/CraftlessCliTest.kt`

- [ ] **Step 1: Add stable supervisor routes**

  Add Craftless-owned lifecycle/runtime routes:

  - `GET /runtimes/java`;
  - `POST /runtimes/java:resolve`.

  These routes list/resolve Java runtime candidates. They must not describe
  Minecraft gameplay actions.

- [ ] **Step 2: Add adaptive CLI commands**

  Add:

  ```sh
  craftless runtimes java list
  craftless runtimes java resolve --mc 26.2
  ```

  Keep output JSON-friendly and include selected/rejected evidence.

- [ ] **Step 3: Verify route and CLI tests**

  Run:

  ```sh
  mise exec -- gradle :daemon:test --tests '*LocalSessionApiServerTest*'
  mise exec -- gradle :cli:test --tests '*CraftlessCliTest*'
  ```

### Task 7: Testkit And Smoke Integration

**Files:**
- Modify: `testkit/src/main/kotlin/com/minekube/craftless/testkit/LocalMinecraftServerSmoke.kt`
- Test: `testkit/src/test/kotlin/com/minekube/craftless/testkit/LocalMinecraftServerSmokeTest.kt`
- Modify: `docs/superpowers/evidence/2026-06-26-version-26-compatibility-probe.md`

- [ ] **Step 1: Write smoke selection tests**

  Assert a smoke request for Minecraft `26.2` uses resolver output for Java 25
  and records selected Java evidence. Keep `CRAFTLESS_SMOKE_JAVA_EXECUTABLE`
  as an explicit override provider, not the only path.

- [ ] **Step 2: Integrate resolver output**

  Make `LocalMinecraftServerSmokeConfig` accept a resolver-selected Java
  executable and write the selected Java descriptor to smoke artifacts.

- [ ] **Step 3: Re-run Java 25 server smoke**

  Run:

  ```sh
  CRAFTLESS_LOCAL_SERVER_SMOKE=1 CRAFTLESS_SMOKE_MINECRAFT_VERSION=26.2 mise exec java@temurin-25.0.3+9.0.LTS gradle@9.6.0 -- gradle :testkit:localMinecraftServerSmoke
  ```

  Expected: server smoke runs with Java 25 and records Java selection evidence.

### Task 8: Documentation, Governance, And Verification

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Modify: `README.md`
- Modify: `docs/client-file-management.md`

- [ ] **Step 1: Update docs after implementation evidence exists**

  Document implemented Java runtime resolution as supervisor/runtime behavior.
  Do not claim broad Fabric client `26.2` support until the driver lane is
  compatible.

- [ ] **Step 2: Verify focused checks**

  Run:

  ```sh
  git diff --check
  mise exec -- gradle :protocol:test :daemon:test :cli:test :testkit:test
  ```

- [ ] **Step 3: Verify repository checks when practical**

  Run:

  ```sh
  mise run architecture-check
  mise run ci
  ```

- [ ] **Step 4: Commit and push**

  Commit with:

  ```sh
  git add protocol daemon cli testkit docs AGENTS.md README.md
  git commit -m "feat: add Java runtime resolution"
  git push origin main
  ```

### Guardrails For Every Task

- [ ] Do not add static gameplay action descriptors.
- [ ] Do not add static gameplay routes or CLI commands.
- [ ] Do not expose Fabric/Yarn/intermediary/raw Minecraft names in public
  generated OpenAPI, CLI gameplay help, README contracts, or SSE event names.
- [ ] Do not require mise for product runtime launches.
- [ ] Do not use npm, npx, yarn, pnpm, or globally installed Node tooling.
- [ ] Use Ktor for HTTP and `ProcessBuilder` for local Java validation.
- [ ] Keep Java selection in the supervisor/client-management layer.
- [ ] Do not mark Craftless complete.
