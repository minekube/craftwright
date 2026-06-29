# Tiny-Agent Lifecycle Defaults Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add lifecycle-only client intent defaults so tiny agents can create API-first Craftless clients without profile/window/audio guesswork.

**Architecture:** Extend protocol DTOs with optional profile and presentation metadata. Resolve defaults in the daemon before creating the persisted `Client`. Keep gameplay commands generated from live per-client OpenAPI; add only static lifecycle CLI flags.

**Tech Stack:** Kotlin/JVM, kotlinx.serialization, Ktor Server/Client tests, Clikt-compatible CLI command handling, Gradle through `mise`.

---

## File Structure

- Modify `protocol/src/main/kotlin/com/minekube/craftless/protocol/ClientModels.kt` for lifecycle DTOs and profile derivation.
- Modify `protocol/src/main/kotlin/com/minekube/craftless/protocol/OpenApiDocument.kt` for supervisor schema metadata.
- Modify `daemon/src/main/kotlin/com/minekube/craftless/daemon/ClientSessionService.kt` to resolve defaults once.
- Modify `cli/src/main/kotlin/com/minekube/craftless/cli/Main.kt` for CLI flags and help.
- Modify README and `.agents/skills/craftless-public-gameplay-agent/SKILL.md` for tiny-agent co-play guidance.
- Update `docs/superpowers/phase-index.md` and `docs/project-completion-checklist.md` after verification.

## Test Strategy

- Protocol unit tests prove serialization/default model behavior.
- Daemon service tests prove effective profile and presentation in returned clients and OpenAPI schemas.
- CLI slice tests prove stdout/stderr/exit code and request payload shape.
- Documentation is verified with `git diff --check`.

### Task 1: Protocol Model Defaults

**Files:**
- Modify: `protocol/src/test/kotlin/com/minekube/craftless/protocol/ClientModelsTest.kt`
- Modify: `protocol/src/main/kotlin/com/minekube/craftless/protocol/ClientModels.kt`

- [x] **Step 1: Write failing protocol tests**

Add tests asserting:

```kotlin
@Test
fun `create client request defaults to automation muted non visible presentation`() {
    val request =
        CreateClientRequest(
            id = "api-bot-01",
            version = "latest-release",
            loader = Loader.FABRIC,
        )

    assertEquals(null, request.profile)
    assertEquals(ClientPresentation(), request.presentation)
    assertEquals(Profile.offline("Apibot01"), request.resolvedProfile())
}
```

and an explicit human presentation case.

- [x] **Step 2: Verify red**

Run:

```sh
mise exec -- gradle :protocol:test --tests com.minekube.craftless.protocol.ClientModelsTest
```

Expected: fail because `ClientPresentation`, optional profile, and
`resolvedProfile()` do not exist.

- [x] **Step 3: Implement protocol DTOs**

Add:

```kotlin
@Serializable
enum class ClientWindowMode { NONE, VISIBLE }

@Serializable
enum class ClientAudioMode { MUTED, DEFAULT }

@Serializable
data class ClientPresentation(
    val window: ClientWindowMode = ClientWindowMode.NONE,
    val audio: ClientAudioMode = ClientAudioMode.MUTED,
)
```

Change `CreateClientRequest.profile` to `Profile? = null`, add the
presentation field, add `resolvedProfile()`, and add `presentation` to
`Client`.

- [x] **Step 4: Verify green**

Run the same protocol test command. Expected: pass.

### Task 2: Daemon Defaults And Schemas

**Files:**
- Modify: `daemon/src/test/kotlin/com/minekube/craftless/daemon/ClientSessionServiceTest.kt`
- Modify: `daemon/src/main/kotlin/com/minekube/craftless/daemon/ClientSessionService.kt`
- Modify: `protocol/src/main/kotlin/com/minekube/craftless/protocol/OpenApiDocument.kt`

- [x] **Step 1: Write failing daemon/schema tests**

Add assertions that creating a client without `profile` returns:

```kotlin
assertEquals("Bot", client.profile.name)
assertEquals(ClientPresentation(), client.presentation)
```

Update the OpenAPI client schema required fields to include `presentation`,
and the create request required fields to exclude `profile`.

- [x] **Step 2: Verify red**

Run:

```sh
mise exec -- gradle :daemon:test --tests com.minekube.craftless.daemon.ClientSessionServiceTest
```

Expected: fail because daemon still dereferences `request.profile`.

- [x] **Step 3: Resolve profile in daemon**

Use `val profile = request.resolvedProfile()` in `createClient`, validate that
profile, and store `presentation = request.presentation` in `Client`.
For daemon-managed launches, materialize muted Minecraft sound options when
`request.presentation.audio` is `MUTED`; keep window mode as lifecycle intent.

- [x] **Step 4: Update schemas**

Add `presentation` object schemas to client responses and create
requests in `OpenApiDocument.kt`. Keep `id`, `version`, and `loader` as the
only required create fields.

- [x] **Step 5: Verify green**

Run:

```sh
mise exec -- gradle :daemon:test --tests com.minekube.craftless.daemon.ClientSessionServiceTest --tests com.minekube.craftless.daemon.LocalSessionApiServerTest
mise exec -- gradle :protocol:test --tests com.minekube.craftless.protocol.OpenApiGenerationTest
```

Expected: pass.

### Task 3: CLI Defaults And Flags

**Files:**
- Modify: `cli/src/test/kotlin/com/minekube/craftless/cli/CraftlessCliTest.kt`
- Modify: `cli/src/main/kotlin/com/minekube/craftless/cli/Main.kt`

- [x] **Step 1: Write failing CLI tests**

Add tests proving:

```kotlin
craftless clients create bot --api <url> --version latest-release --loader fabric
```

posts a request without `profile`, with `presentation.window` `NONE`, and
`presentation.audio` `MUTED`.

Add a second test proving `--visible --audio default --offline-name Robin`
posts `VISIBLE`, `DEFAULT`, and the explicit profile.

- [x] **Step 2: Verify red**

Run:

```sh
mise exec -- gradle :cli:test --tests com.minekube.craftless.cli.CraftlessCliTest
```

Expected: fail because `--offline-name` is still required and flags are
unknown.

- [x] **Step 3: Implement CLI parsing**

Parse `--visible` and `--audio`. Make `--offline-name` optional.
Build `CreateClientRequest` with `profile = profileName?.let(Profile::offline)`.

- [x] **Step 4: Update CLI help**

Add lifecycle flags to usage and clarify `server start` starts the Craftless
supervisor.

- [x] **Step 5: Verify green**

Run the same CLI test command. Expected: pass.

### Task 4: Docs And Agent Skill

**Files:**
- Modify: `README.md`
- Modify: `.agents/skills/craftless-public-gameplay-agent/SKILL.md`
- Modify: `docs/superpowers/phase-index.md`
- Modify: `docs/project-completion-checklist.md`

- [x] **Step 1: Update README**

Add tiny-agent client creation examples and a two-client co-play bootstrap
that still fetches generated per-client OpenAPI before gameplay.

- [x] **Step 2: Update repo-local skill**

Add the same bootstrap as agent instructions, plus the rule that lifecycle
presentation flags are not gameplay authority.

- [x] **Step 3: Update phase/checklist**

Record Phase 188 as a post-completion usability improvement with focused
verification evidence.

- [x] **Step 4: Verify docs**

Run:

```sh
git diff --check
```

Expected: pass.

### Task 5: Focused Verification

**Files:** no code edits unless verification reveals a bug.

- [x] **Step 1: Run focused tests**

```sh
mise exec -- gradle :protocol:test --tests com.minekube.craftless.protocol.ClientModelsTest --tests com.minekube.craftless.protocol.OpenApiGenerationTest
mise exec -- gradle :daemon:test --tests com.minekube.craftless.daemon.ClientSessionServiceTest --tests com.minekube.craftless.daemon.LocalSessionApiServerTest
mise exec -- gradle :cli:test --tests com.minekube.craftless.cli.CraftlessCliTest
git diff --check
```

- [x] **Step 2: Inspect status**

```sh
git status --short --branch
```

Expected: only intentional files are modified.
