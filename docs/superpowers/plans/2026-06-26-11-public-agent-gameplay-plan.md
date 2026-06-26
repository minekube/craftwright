# Public Agent Gameplay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove final gameplay through an external agent/adaptive CLI flow that composes generated Craftless OpenAPI actions and SSE events instead of relying on hard-coded survival scenario APIs.

**Architecture:** Add a public-agent gameplay runner outside `driver-fabric` product internals. The runner discovers the live client spec, invokes only generated actions through the daemon, listens to SSE, records state/action artifacts, and reports missing generic primitives as blockers. Any missing Minecraft capability must be added through the runtime graph/projection/adapter system, not as `task.survival.*` scenario logic.

**Tech Stack:** Kotlin/JVM Gradle through `mise`, Ktor Server/Client, kotlinx.serialization, Craftless daemon/client OpenAPI, SSE, optional Bun helper tests through `mise exec -- bun`.

---

### Task 1: Mark Internal Survival Task As Diagnostic

**Files:**
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricNavigationDiscovery.kt`
- Modify: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricNavigationDiscoveryTest.kt`
- Modify: `docs/project-completion-checklist.md`

- [x] **Step 1: Write the failing test**

Add a test that asserts public completion docs do not treat `task.survival.*`
as durable API and that final completion requires a public-agent artifact:

```kotlin
@Test
fun `final completion docs require public agent gameplay evidence`() {
    val checklist = Files.readString(repositoryRoot().resolve("docs/project-completion-checklist.md"))

    assertTrue(checklist.contains("public-agent gameplay runner"))
    assertTrue(checklist.contains("not by hard-coding the scenario"))
    assertFalse(checklist.contains("[x] Craftless obtains weapon materials through ordinary survival gameplay"))
}
```

- [x] **Step 2: Verify RED**

Run:

```sh
mise exec -- gradle :driver-fabric:test --tests '*final completion docs require public agent gameplay evidence*'
```

Expected: FAIL until the docs and tests agree on the new completion gate.

- [ ] **Step 3: Implement the minimal doc/test alignment**

Keep `task.run` diagnostic, but ensure docs and tests state that final proof
comes from `public-agent-gameplay-results.jsonl` and
`public-agent-state.jsonl`.

- [ ] **Step 4: Verify GREEN**

Run the focused test again. Expected: PASS.

### Task 2: Public-Agent Runner Contract

**Files:**
- Create: `testkit/src/main/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunner.kt`
- Create: `testkit/src/test/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunnerTest.kt`

- [ ] **Step 1: Write the failing contract test**

```kotlin
@Test
fun `runner fetches live client spec before invoking gameplay actions`() = runTest {
    val server = RecordingCraftlessHttpServer()
    val runner = PublicAgentGameplayRunner(server.url, clientId = "fabric-smoke")

    runner.runOnce()

    assertEquals(
        listOf(
            "GET /openapi.json",
            "GET /clients/fabric-smoke/openapi.json",
            "GET /clients/fabric-smoke/actions",
            "GET /clients/fabric-smoke/events:stream",
            "POST /clients/fabric-smoke:run",
        ),
        server.requests.take(5),
    )
}
```

- [ ] **Step 2: Verify RED**

Run:

```sh
mise exec -- gradle :testkit:test --tests '*runner fetches live client spec*'
```

Expected: FAIL because `PublicAgentGameplayRunner` does not exist.

- [x] **Step 3: Implement minimal runner**

Implement:

```kotlin
class PublicAgentGameplayRunner(
    private val baseUrl: String,
    private val clientId: String,
    private val http: HttpClient = HttpClient(CIO),
) {
    suspend fun runOnce(): PublicAgentGameplayResult {
        val supervisorSpec = http.get("$baseUrl/openapi.json").bodyAsText()
        val clientSpec = http.get("$baseUrl/clients/$clientId/openapi.json").bodyAsText()
        val actions = http.get("$baseUrl/clients/$clientId/actions").bodyAsText()
        val events = http.get("$baseUrl/clients/$clientId/events:stream").bodyAsText()
        return PublicAgentGameplayResult(supervisorSpec, clientSpec, actions, events)
    }
}
```

- [x] **Step 4: Verify GREEN**

Run the focused test again. Expected: PASS.

### Task 3: Generic State Machine And Missing Primitive Reporting

**Files:**
- Modify: `testkit/src/main/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunner.kt`
- Modify: `testkit/src/test/kotlin/com/minekube/craftless/testkit/PublicAgentGameplayRunnerTest.kt`

- [x] **Step 1: Write failing tests**

Add tests:

```kotlin
@Test
fun `runner reports missing generic primitive instead of using scenario shortcut`() = runTest {
    val server = RecordingCraftlessHttpServer(actions = listOf("entity.query", "inventory.query"))
    val runner = PublicAgentGameplayRunner(server.url, clientId = "fabric-smoke")

    val result = runner.runOnce()

    assertEquals(PublicAgentGameplayState.BLOCKED, result.state)
    assertEquals("missing-generic-primitive:navigation.plan", result.blocker)
    assertFalse(server.requests.any { it.contains("task.survival") })
}
```

- [x] **Step 2: Verify RED**

Run:

```sh
mise exec -- gradle :testkit:test --tests '*missing generic primitive*'
```

Expected: FAIL until the runner validates required generated actions.

- [x] **Step 3: Implement primitive validation**

Require at least:

```kotlin
private val requiredActions = setOf(
    "entity.query",
    "inventory.query",
    "navigation.plan",
    "navigation.follow",
    "player.look",
    "player.raycast",
    "world.block.break",
)
```

Return `BLOCKED` with `missing-generic-primitive:<id>` for the first missing
action. Do not invoke `task.run` for final completion.

- [x] **Step 4: Verify GREEN**

Run the focused test again. Expected: PASS.

### Task 4: Agent Skill For Craftless Gameplay

**Files:**
- Create: `.agents/skills/craftless-public-gameplay-agent/SKILL.md`
- Test: `docs/project-completion-checklist.md`

- [x] **Step 1: Write the skill**

The skill must instruct agents to:

- fetch `/openapi.json` and `/clients/{id}/openapi.json`;
- cache by runtime fingerprint only;
- use `/clients/{id}/actions` for action discovery;
- subscribe to `/clients/{id}/events:stream`;
- invoke through `POST /clients/{id}:run`;
- validate state changes through queries/events;
- reject `task.survival.*`, `kill.cow`, `find.tree`, and `craft.sword`;
- report missing primitives instead of inventing shortcuts.

- [x] **Step 2: Verify text guardrails**

Run:

```sh
rg -n "task\\.survival|kill\\.cow|find\\.tree|craft\\.sword|runtime fingerprint|events:stream" .agents/skills/craftless-public-gameplay-agent/SKILL.md
```

Expected: finds the forbidden shortcut warnings and required discovery/stream
guidance.

### Task 5: Final Gameplay Harness Emits Public-Agent Evidence

**Files:**
- Modify: `driver-fabric/build.gradle.kts`
- Modify: `driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricClientSmokeController.kt`
- Test: `driver-fabric/src/test/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricDriverModuleTest.kt`

- [x] **Step 1: Write failing artifact test**

Assert final gameplay plan/artifacts include:

```kotlin
assertTrue(plan.artifacts.contains("public-agent-gameplay-results.jsonl"))
assertTrue(plan.artifacts.contains("public-agent-state.jsonl"))
```

- [x] **Step 2: Verify RED**

Run:

```sh
mise exec -- gradle :driver-fabric:test --tests '*fabric final gameplay plan*'
```

Expected: FAIL until final plan artifacts include public-agent evidence.

- [x] **Step 3: Emit public-agent evidence in final mode**

When `CRAFTLESS_FINAL_GAMEPLAY=1`, after the client is connected and the live
spec is captured, write public-agent evidence from the same generated
OpenAPI/action/SSE boundary:

- `public-agent-gameplay-results.jsonl`;
- `public-agent-state.jsonl`.

Keep `survival-task-results.jsonl` only as diagnostic harness evidence.
The next implementation step is to make the runner process-external by exposing
or reusing a daemon URL that is reachable outside the Fabric smoke controller.

- [x] **Step 4: Verify GREEN**

Run the focused module test. Expected: PASS.

### Task 6: Live Gate And Push

**Files:**
- Modify: `docs/project-completion-checklist.md`

- [ ] **Step 1: Run focused verification**

```sh
mise exec -- gradle :testkit:test :driver-fabric:test
```

Expected: PASS.

- [ ] **Step 2: Run full verification**

```sh
mise run lint
mise run architecture-check
mise run ci
```

Expected: PASS.

- [ ] **Step 3: Run no-hold final gameplay**

```sh
CRAFTLESS_FINAL_GAMEPLAY=1 CRAFTLESS_FABRIC_SMOKE_HOLD_AFTER_ACTIONS_MS=0 mise exec -- gradle :driver-fabric:fabricFinalGameplay
```

Expected: public-agent artifacts either show successful survival progress or
machine-readable missing generic primitives. Do not mark completion from
internal `task.survival.*` alone.

- [ ] **Step 4: Run held final gameplay for Robin**

```sh
CRAFTLESS_FINAL_GAMEPLAY=1 mise exec -- gradle :driver-fabric:fabricFinalGameplay
```

Use:

```sh
say "Robin, join the Craftless test server now and confirm in Minecraft chat when the goal may be completed."
```

Expected: Robin writes in Minecraft chat that the goal may be completed.

- [ ] **Step 5: Commit and push**

```sh
git status --short
git add AGENTS.md docs .agents testkit driver-fabric
git commit -m "feat: prove gameplay through public agent flow"
git push origin main
```

Expected: changes are on `main`.
