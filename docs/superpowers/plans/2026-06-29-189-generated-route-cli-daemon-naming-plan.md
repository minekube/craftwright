# Generated Route CLI And Daemon Naming Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Craftless CLI supervisor commands generated from OpenAPI route metadata, keep existing per-client generated action syntax, and rename local API process startup to `daemon start` while preserving `server start`.

**Architecture:** Add `x-craftless-cli` metadata to `OpenApiOperation`, generated from `ApiRouteCatalog.sessionDefaults()` and runtime action routes. Add a CLI route interpreter that fetches the supervisor spec, matches command tokens to operation metadata, builds request bodies from schema/CLI bindings, and dispatches through Ktor. Keep only the static startup kernel and route interpreter in CLI source; use bundled supervisor OpenAPI only as offline help fallback.

**Tech Stack:** Kotlin/JVM, kotlinx.serialization, Ktor Client/Server tests, existing Clikt shell wrapper, Gradle through `mise`.

---

## File Structure

- Modify `protocol/src/main/kotlin/com/minekube/craftless/protocol/OpenApiDocument.kt`
  - Add serializable `OpenApiCliOperation`, `OpenApiCliBody`, and `OpenApiCliBinding`.
  - Add `cli: OpenApiCliOperation?` to `OpenApiOperation` serialized as `x-craftless-cli`.
  - Emit route CLI metadata from `ApiRoute.toOperation(...)`.
- Modify `protocol/src/main/kotlin/com/minekube/craftless/protocol/ApiRoute.kt`
  - Add route-owned CLI metadata, including command tokens, hidden flag, stream flag, and body bindings.
  - Populate metadata for every stable supervisor route.
- Create `cli/src/main/kotlin/com/minekube/craftless/cli/GeneratedRouteCli.kt`
  - Own route matching, help rendering, request body building, path expansion, and HTTP dispatch for generated OpenAPI operations.
- Modify `cli/src/main/kotlin/com/minekube/craftless/cli/Main.kt`
  - Keep `daemon start` and `server start` static.
  - Route non-start commands through `GeneratedRouteCli`.
  - Keep per-client action alias safeguards, either by moving existing alias logic into `GeneratedRouteCli` or delegating from it.
- Modify `protocol/src/test/kotlin/com/minekube/craftless/protocol/OpenApiGenerationTest.kt`
  - Add coverage for `x-craftless-cli`.
- Modify `cli/src/test/kotlin/com/minekube/craftless/cli/CraftlessCliTest.kt`
  - Update root/group help expectations.
  - Add generated supervisor route dispatch/help tests.
  - Keep existing action alias authority tests.
- Modify `README.md`, `.agents/skills/craftless-public-gameplay-agent/SKILL.md`, `docs/agent-operating-contract.md`, `docs/agent-module-contracts.md`, `cli/AGENTS.md`
  - Prefer `daemon start`.
  - Describe generated supervisor and per-client CLI commands.
  - Keep `server start` documented only as a compatibility alias.
- Modify `docs/project-completion-checklist.md` and `docs/superpowers/phase-index.md`
  - Record Phase 189 status and evidence pointer after verification.
- Create `docs/superpowers/evidence/2026-06-29-generated-route-cli-daemon-naming.md`
  - Record focused tests and full CI outcome.

## Task 1: Protocol CLI Metadata

**Files:**
- Modify: `protocol/src/test/kotlin/com/minekube/craftless/protocol/OpenApiGenerationTest.kt`
- Modify: `protocol/src/main/kotlin/com/minekube/craftless/protocol/OpenApiDocument.kt`
- Modify: `protocol/src/main/kotlin/com/minekube/craftless/protocol/ApiRoute.kt`

- [ ] **Step 1: Write failing OpenAPI metadata test**

Add a protocol test near the existing supervisor OpenAPI tests:

```kotlin
@Test
fun `supervisor openapi exposes cli metadata for stable routes`() {
    val document = OpenApiDocument.from(ApiRouteCatalog.sessionDefaults())

    val create = requireNotNull(document.paths["/clients"]?.post?.cli)
    assertEquals(listOf("clients", "create", "{id}"), create.command)
    assertEquals(false, create.hidden)
    assertEquals(false, create.stream)
    assertEquals("/id", create.body?.bindings?.single { it.flag == null && it.argument == "id" }?.pointer)
    assertEquals("/version", create.body?.bindings?.single { it.flag == "--version" }?.pointer)
    assertEquals("/loader", create.body?.bindings?.single { it.flag == "--loader" }?.pointer)
    assertEquals("/loaderVersion", create.body?.bindings?.single { it.flag == "--loader-version" }?.pointer)
    assertEquals("/profile/name", create.body?.bindings?.single { it.flag == "--offline-name" }?.pointer)
    assertEquals("/profile/kind", create.body?.bindings?.single { it.fixed == "OFFLINE" }?.pointer)
    assertEquals("/presentation/window", create.body?.bindings?.single { it.flag == "--visible" }?.pointer)
    assertEquals("VISIBLE", create.body?.bindings?.single { it.flag == "--visible" }?.fixed)
    assertEquals("/presentation/audio", create.body?.bindings?.single { it.flag == "--audio" }?.pointer)

    val connect = requireNotNull(document.paths["/clients/{id}:connect"]?.post?.cli)
    assertEquals(listOf("clients", "{id}", "connect"), connect.command)
    assertEquals("/host", connect.body?.bindings?.single { it.flag == "--host" }?.pointer)
    assertEquals("/port", connect.body?.bindings?.single { it.flag == "--port" }?.pointer)

    val stream = requireNotNull(document.paths["/clients/{id}/events:stream"]?.get?.cli)
    assertEquals(listOf("clients", "{id}", "events"), stream.command)
    assertEquals(true, stream.stream)

    val eventList = requireNotNull(document.paths["/clients/{id}/events"]?.get?.cli)
    assertEquals(listOf("clients", "{id}", "events", "list"), eventList.command)

    val runtimes = requireNotNull(document.paths["/runtimes/java"]?.get?.cli)
    assertEquals(listOf("runtimes", "java", "list"), runtimes.command)
}
```

- [ ] **Step 2: Run the red protocol test**

```sh
mise exec -- gradle :protocol:test --tests com.minekube.craftless.protocol.OpenApiGenerationTest
```

Expected: fails because `OpenApiOperation.cli` and CLI metadata DTOs do not exist.

- [ ] **Step 3: Add serializable CLI metadata DTOs**

In `OpenApiDocument.kt`, extend `OpenApiOperation` and add DTOs:

```kotlin
@Serializable
data class OpenApiOperation(
    val operationId: String,
    val tags: List<String>,
    val responses: Map<String, OpenApiResponse>,
    val requestBody: OpenApiRequestBody? = null,
    @SerialName("x-craftless-cli")
    val cli: OpenApiCliOperation? = null,
    @SerialName("x-craftless")
    val extensions: Map<String, String>,
)

@Serializable
data class OpenApiCliOperation(
    val command: List<String>,
    val aliases: List<List<String>> = emptyList(),
    val hidden: Boolean = false,
    val stream: Boolean = false,
    val body: OpenApiCliBody? = null,
)

@Serializable
data class OpenApiCliBody(
    val bindings: List<OpenApiCliBinding> = emptyList(),
)

@Serializable
data class OpenApiCliBinding(
    val pointer: String,
    val flag: String? = null,
    val argument: String? = null,
    val fixed: String? = null,
    val type: String = "string",
    val required: Boolean = false,
)
```

Keep `extensions` serialized as the existing `x-craftless` object. Do not rename existing fields.

- [ ] **Step 4: Add route metadata model**

In `ApiRoute.kt`, add route-local metadata:

```kotlin
@Serializable
data class ApiRouteCli(
    val command: List<String>,
    val aliases: List<List<String>> = emptyList(),
    val hidden: Boolean = false,
    val stream: Boolean = false,
    val body: List<ApiRouteCliBinding> = emptyList(),
)

@Serializable
data class ApiRouteCliBinding(
    val pointer: String,
    val flag: String? = null,
    val argument: String? = null,
    val fixed: String? = null,
    val type: String = "string",
    val required: Boolean = false,
)
```

Add `val cli: ApiRouteCli? = null` to `ApiRoute`.

- [ ] **Step 5: Convert route metadata to OpenAPI metadata**

In `OpenApiDocument.kt`, pass CLI metadata inside `ApiRoute.toOperation(...)`:

```kotlin
private fun ApiRoute.toOperation(actionsById: Map<String, OpenApiAction>): OpenApiOperation {
    val route = this
    return OpenApiOperation(
        operationId = operationId,
        tags = listOf(tag),
        responses = route.responses(actionsById),
        requestBody = route.requestBody(actionsById),
        cli = route.cli?.toOpenApiCliOperation(),
        extensions = buildMap {
            put("x-craftless-owner", route.owner)
            route.member?.let { put("x-craftless-member", it) }
            put("x-craftless-target", target)
            put("x-craftless-return", returnKind)
            put("x-craftless-source", source)
            actionId?.let { put("x-craftless-action", it) }
        },
    )
}

private fun ApiRouteCli.toOpenApiCliOperation(): OpenApiCliOperation =
    OpenApiCliOperation(
        command = command,
        aliases = aliases,
        hidden = hidden,
        stream = stream,
        body = body.takeIf { it.isNotEmpty() }?.let { bindings ->
            OpenApiCliBody(bindings.map { it.toOpenApiCliBinding() })
        },
    )

private fun ApiRouteCliBinding.toOpenApiCliBinding(): OpenApiCliBinding =
    OpenApiCliBinding(
        pointer = pointer,
        flag = flag,
        argument = argument,
        fixed = fixed,
        type = type,
        required = required,
    )
```

- [ ] **Step 6: Populate supervisor route CLI metadata**

In `ApiRouteCatalog.sessionDefaults()`, change each `route(...)` call to include command metadata. Use helper functions to keep the list readable:

```kotlin
private fun cli(
    vararg command: String,
    stream: Boolean = false,
    hidden: Boolean = false,
    aliases: List<List<String>> = emptyList(),
    body: List<ApiRouteCliBinding> = emptyList(),
): ApiRouteCli =
    ApiRouteCli(
        command = command.toList(),
        stream = stream,
        hidden = hidden,
        aliases = aliases,
        body = body,
    )

private fun bind(
    pointer: String,
    flag: String? = null,
    argument: String? = null,
    fixed: String? = null,
    type: String = "string",
    required: Boolean = false,
): ApiRouteCliBinding =
    ApiRouteCliBinding(
        pointer = pointer,
        flag = flag,
        argument = argument,
        fixed = fixed,
        type = type,
        required = required,
    )
```

Route mappings:

```kotlin
route("GET", "/openapi.json", "getOpenapiJson", "openapi", "supervisor", "openapi", "route", cli = cli("openapi"))
route("GET", "/version", "getVersion", "version", "supervisor", "version", "route", cli = cli("version"))
route("GET", "/events", "getEvents", "events", "supervisor", "events", "route", cli = cli("events", "list"))
route("GET", "/events:stream", "streamEvents", "events", "supervisor", "events", "route", cli = cli("events", stream = true))
route("POST", "/cache:prepare", "prepareCache", "cache", "cache", "prepare", "method", cli = cli("cache", "prepare", body = listOf(bind("/minecraftVersion", "--mc", type = "string", required = true), bind("/loader", "--loader", type = "string", required = true), bind("/loaderVersion", "--loader-version", type = "string"))))
route("POST", "/cache:export", "exportCache", "cache", "cache", "export", "method", cli = cli("cache", "export", body = listOf(bind("/manifest", "--manifest", required = true), bind("/archive", "--archive"))))
route("POST", "/cache:cleanup", "cleanupCache", "cache", "cache", "cleanup", "method", cli = cli("cache", "cleanup", body = listOf(bind("/manifest", "--manifest", required = true))))
route("GET", "/runtimes/java", "listJavaRuntimes", "runtimes", "runtimes", "java", "route", cli = cli("runtimes", "java", "list"))
route("POST", "/runtimes/java:resolve", "resolveJavaRuntime", "runtimes", "runtimes", "java", "method", cli = cli("runtimes", "java", "resolve", body = listOf(bind("/minecraftVersion", "--mc", required = true))))
route("GET", "/clients", "listClients", "clients", "clients", "list", "route", cli = cli("clients", "list"))
route("POST", "/clients", "createClient", "clients", "clients", "create", "route", cli = cli("clients", "create", "{id}", body = listOf(bind("/id", argument = "id", required = true), bind("/version", "--version", required = true), bind("/loader", "--loader", required = true), bind("/loaderVersion", "--loader-version"), bind("/profile/name", "--offline-name"), bind("/profile/kind", fixed = "OFFLINE"), bind("/presentation/window", "--visible", fixed = "VISIBLE"), bind("/presentation/audio", "--audio"))))
route("GET", "/clients/{id}", "getClient", "clients", "clients", "get", "route", cli = cli("clients", "{id}", "get"))
route("GET", "/clients/{id}/openapi.json", "getClientOpenapiJson", "clients", "clients", "openapi", "route", cli = cli("clients", "{id}", "openapi"))
route("POST", "/clients/{id}:attach", "attachClientDriver", "clients", "clients", "attach", "method", cli = cli("clients", "{id}", "attach", body = listOf(bind("/endpoint", "--endpoint", required = true))))
route("POST", "/clients/{id}:connect", "clientConnect", "clients", "clients", "connect", "method", cli = cli("clients", "{id}", "connect", body = listOf(bind("/host", "--host", required = true), bind("/port", "--port", type = "integer", required = true))))
route("GET", "/clients/{id}/actions", "listClientActions", "clients", "clients", "actions", "action", cli = cli("clients", "{id}", "actions"))
route("GET", "/clients/{id}/resources", "listClientResources", "clients", "clients", "resources", "resource", cli = cli("clients", "{id}", "resources"))
route("POST", "/clients/{id}:run", "runClientAction", "clients", "clients", "run", "action", cli = cli("clients", "{id}", "run", "{action}"))
route("POST", "/clients/{id}:stop", "stopClient", "clients", "clients", "stop", "method", cli = cli("clients", "{id}", "stop"))
route("GET", "/clients/{id}/events", "getClientEvents", "clients", "clients", "events", "route", cli = cli("clients", "{id}", "events", "list"))
route("GET", "/clients/{id}/events:stream", "streamClientEvents", "clients", "clients", "events", "route", cli = cli("clients", "{id}", "events", stream = true))
```

Use this exact helper shape by moving `cli` and `bind` before
`sessionDefaults()` inside the companion object, then call `route(...)` with a
new trailing `cli: ApiRouteCli? = null` parameter.

- [ ] **Step 7: Run protocol test green**

```sh
mise exec -- gradle :protocol:test --tests com.minekube.craftless.protocol.OpenApiGenerationTest
```

Expected: `BUILD SUCCESSFUL`.

## Task 2: Generated Route CLI Engine

**Files:**
- Create: `cli/src/main/kotlin/com/minekube/craftless/cli/GeneratedRouteCli.kt`
- Modify: `cli/src/main/kotlin/com/minekube/craftless/cli/Main.kt`
- Modify: `cli/src/test/kotlin/com/minekube/craftless/cli/CraftlessCliTest.kt`

- [ ] **Step 1: Write failing tests for generated supervisor dispatch**

Add tests proving current lifecycle commands are no longer special-cased by behavior:

```kotlin
@Test
fun `generated supervisor route creates client from openapi cli metadata`() {
    RecordingCreateApiServer().use { server ->
        val output = StringBuilder()
        val errors = StringBuilder()

        val exit =
            CraftlessCli.run(
                listOf(
                    "clients",
                    "create",
                    "bot",
                    "--api",
                    server.url,
                    "--version",
                    "latest-release",
                    "--loader",
                    "fabric",
                ),
                stdout = { output.appendLine(it) },
                stderr = { errors.appendLine(it) },
            )

        assertEquals(0, exit, errors.toString())
        val request = Json.parseToJsonElement(server.createBodies.single()).jsonObject
        assertEquals("bot", request["id"]?.jsonPrimitive?.content)
        assertEquals("latest-release", request["version"]?.jsonPrimitive?.content)
        assertEquals("FABRIC", request["loader"]?.jsonPrimitive?.content)
    }
}

@Test
fun `generated supervisor route help is loaded from supervisor openapi`() {
    RecordingCreateApiServer().use { server ->
        val output = StringBuilder()
        val errors = StringBuilder()

        val exit =
            CraftlessCli.run(
                listOf("clients", "create", "--help", "--api", server.url),
                stdout = { output.appendLine(it) },
                stderr = { errors.appendLine(it) },
            )

        assertEquals(0, exit, errors.toString())
        val help = output.toString()
        assertTrue(help.contains("Route: POST /clients"))
        assertTrue(help.contains("Usage: craftless clients create <id>"))
        assertTrue(help.contains("--version string required"))
        assertTrue(help.contains("--loader string required"))
        assertTrue(help.contains("--audio string default=MUTED enum=MUTED|DEFAULT"))
    }
}
```

Make `RecordingCreateApiServer` serve `/openapi.json` by responding with
`Json.encodeToString(OpenApiDocument.from(ApiRouteCatalog.sessionDefaults()))`.
Import `ApiRouteCatalog` and `OpenApiDocument` in the test file.

- [ ] **Step 2: Write failing source guard test**

Add a guard that prevents reintroducing direct CLI route branches for supervisor commands:

```kotlin
@Test
fun `cli does not hardcode supervisor api route dispatch branches`() {
    val source = Files.readString(repositoryRoot().resolve("cli/src/main/kotlin/com/minekube/craftless/cli/Main.kt"))

    assertFalse(source.contains("args.take(2) == listOf(\"clients\", \"create\")"))
    assertFalse(source.contains("args.take(2) == listOf(\"clients\", \"list\")"))
    assertFalse(source.contains("args.take(3) == listOf(\"runtimes\", \"java\", \"list\")"))
    assertFalse(source.contains("args.take(2) == listOf(\"cache\", \"prepare\")"))
    assertFalse(source.contains("http.post(\"\\${api.trimEnd('/')}/clients\")"))
}
```

This guard should still allow `daemon start` and `server start` static startup
branches.

- [ ] **Step 3: Run CLI tests red**

```sh
mise exec -- gradle :cli:test --tests com.minekube.craftless.cli.CraftlessCliTest
```

Expected: fails because generated supervisor route dispatch does not exist and old hardcoded branches still exist.

- [ ] **Step 4: Create generated route data structures**

Create `GeneratedRouteCli.kt` with these data structures:

```kotlin
package com.minekube.craftless.cli

import com.minekube.craftless.protocol.OpenApiCliBinding
import com.minekube.craftless.protocol.OpenApiDocument
import com.minekube.craftless.protocol.OpenApiOperation
import com.minekube.craftless.protocol.OpenApiSchema
import kotlinx.serialization.json.JsonElement

internal data class GeneratedCliRoute(
    val method: String,
    val pathTemplate: String,
    val command: List<String>,
    val operation: OpenApiOperation,
    val hidden: Boolean,
    val stream: Boolean,
)

internal data class GeneratedRouteMatch(
    val route: GeneratedCliRoute,
    val pathValues: Map<String, String>,
    val consumed: Int,
)

internal class GeneratedRouteCli(
    private val json: kotlinx.serialization.json.Json,
) {
    fun routes(document: OpenApiDocument): List<GeneratedCliRoute> =
        document.paths.flatMap { (path, item) ->
            listOfNotNull(
                item.get?.toGeneratedRoute("GET", path),
                item.post?.toGeneratedRoute("POST", path),
            )
        }.sortedWith(compareByDescending<GeneratedCliRoute> { it.command.size }.thenBy { it.command.joinToString(" ") })

    private fun OpenApiOperation.toGeneratedRoute(method: String, path: String): GeneratedCliRoute? {
        val metadata = cli ?: return null
        return GeneratedCliRoute(
            method = method,
            pathTemplate = path,
            command = metadata.command,
            operation = this,
            hidden = metadata.hidden,
            stream = metadata.stream,
        )
    }
}
```

The initial `routes` function only uses explicit `x-craftless-cli`. The first
implementation does not add path-derived fallback; every stable supervisor
route must have explicit metadata from Task 1.

- [ ] **Step 5: Add matching**

Add matching to `GeneratedRouteCli`:

```kotlin
fun match(
    args: List<String>,
    routes: List<GeneratedCliRoute>,
): GeneratedRouteMatch? =
    routes.firstNotNullOfOrNull { route ->
        matchRoute(args, route)
    }

private fun matchRoute(
    args: List<String>,
    route: GeneratedCliRoute,
): GeneratedRouteMatch? {
    val command = route.command
    val commandArgs = args.takeWhile { !it.startsWith("--") }
    if (commandArgs.size < command.size) return null

    val values = linkedMapOf<String, String>()
    command.forEachIndexed { index, token ->
        val actual = commandArgs[index]
        if (token.startsWith("{") && token.endsWith("}")) {
            values[token.removePrefix("{").removeSuffix("}")] = actual
        } else if (token != actual) {
            return null
        }
    }
    return GeneratedRouteMatch(route = route, pathValues = values, consumed = command.size)
}
```

- [ ] **Step 6: Add path expansion and request body building**

Add request helpers:

```kotlin
fun expandPath(match: GeneratedRouteMatch): String =
    match.pathValues.entries.fold(match.route.pathTemplate) { path, (name, value) ->
        path.replace("{$name}", value)
    }

fun bodyFor(
    args: List<String>,
    match: GeneratedRouteMatch,
): JsonElement? {
    val requestSchema =
        match.route.operation.requestBody
            ?.content
            ?.get("application/json")
            ?.schema
            ?: return null
    val bindings = match.route.operation.cli?.body?.bindings.orEmpty()
    return if (bindings.isNotEmpty()) {
        buildBoundBody(args, match, bindings)
    } else {
        buildSchemaBody(args, requestSchema)
    }
}
```

Implement `buildBoundBody` with JSON pointer assignment:

```kotlin
private fun buildBoundBody(
    args: List<String>,
    match: GeneratedRouteMatch,
    bindings: List<OpenApiCliBinding>,
): JsonElement {
    val root = linkedMapOf<String, Any?>()
    bindings.forEach { binding ->
        val value =
            when {
                binding.argument != null -> match.pathValues[binding.argument]
                binding.fixed != null && binding.flag == null -> binding.fixed
                binding.fixed != null && args.contains(binding.flag) -> binding.fixed
                binding.flag != null -> args.optionValue(binding.flag)
                else -> null
            }
        if (binding.required && value == null) {
            error("${binding.flag ?: binding.argument ?: binding.pointer} is required")
        }
        if (value != null) {
            root.putJsonPointer(binding.pointer, value.toTypedValue(binding.type))
        }
    }
    return root.toJsonElement()
}
```

Provide local helpers in `GeneratedRouteCli.kt`:

```kotlin
private fun List<String>.optionValue(name: String?): String? {
    if (name == null) return null
    val index = indexOf(name)
    return if (index >= 0 && index + 1 < size) this[index + 1] else null
}

private fun String.toTypedValue(type: String): Any =
    when (type) {
        "integer" -> requireNotNull(toIntOrNull()) { "integer argument is required" }
        "number" -> requireNotNull(toDoubleOrNull()) { "number argument is required" }
        "boolean" -> when {
            equals("true", ignoreCase = true) -> true
            equals("false", ignoreCase = true) -> false
            else -> error("boolean argument must be true or false")
        }
        else -> this
    }
```

Use `kotlinx.serialization.json.buildJsonObject` recursively for
`toJsonElement()` so nested pointers such as `/profile/name` produce objects.

- [ ] **Step 7: Add HTTP dispatch**

In `GeneratedRouteCli.kt`, add:

```kotlin
suspend fun dispatch(
    http: io.ktor.client.HttpClient,
    api: String,
    args: List<String>,
    document: OpenApiDocument,
    stdout: (String) -> Unit,
    stderr: (String) -> Unit,
): Int {
    val routes = routes(document)
    val match = match(args, routes)
    if (match == null) {
        stderr("error: unknown command ${args.joinToString(" ")}")
        return 2
    }
    if (args.contains("--help")) {
        stdout(help(match))
        return 0
    }
    val path = expandPath(match)
    val response =
        when (match.route.method) {
            "GET" -> http.get("${api.trimEnd('/')}$path")
            "POST" -> http.post("${api.trimEnd('/')}$path") {
                val body = bodyFor(args, match)
                if (body != null) {
                    contentType(io.ktor.http.ContentType.Application.Json)
                    setBody(json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), body))
                }
            }
            else -> error("unsupported method ${match.route.method}")
        }
    val body = response.bodyAsText()
    return if (response.status.isSuccess()) {
        stdout(body)
        0
    } else {
        stderr(body)
        1
    }
}
```

Import Ktor request helpers at the top of the new file.

- [ ] **Step 8: Add generated route help**

Add help rendering:

```kotlin
fun help(match: GeneratedRouteMatch): String =
    buildString {
        appendLine("Route: ${match.route.method} ${match.route.pathTemplate}")
        appendLine("Usage: craftless ${match.route.command.joinToString(" ") { token -> if (token.startsWith("{")) "<${token.removePrefix("{").removeSuffix("}")}>" else token }} [--api <url>]")
        val bindings = match.route.operation.cli?.body?.bindings.orEmpty()
        if (bindings.isNotEmpty()) {
            appendLine("Arguments:")
            bindings
                .filter { it.flag != null }
                .forEach { binding ->
                    val required = if (binding.required) " required" else ""
                    appendLine("  ${binding.flag} ${binding.type}$required")
                }
        }
    }.trimEnd()
```

Enhance this after green tests to include schema enum/default metadata for
known pointers. The help test for `--audio` should drive that final behavior.

- [ ] **Step 9: Wire generated route engine into `Main.kt`**

In `CraftlessCli.run(...)`, keep only help, daemon/server start, and generated dispatch:

```kotlin
if (args.take(2) == listOf("daemon", "start") || args.take(2) == listOf("server", "start")) {
    return runServerStart(args.drop(2), stdout, stderr, afterStart, env, cacheMetadataFetcher, distributionRoot)
}
if (args.isNotEmpty()) {
    return runGeneratedRoute(args, stdout, stderr, env)
}
```

Add:

```kotlin
private fun runGeneratedRoute(
    args: List<String>,
    stdout: (String) -> Unit,
    stderr: (String) -> Unit,
    env: Map<String, String>,
): Int {
    val api = args.apiBaseUrl(env)
    return runCatching {
        kotlinx.coroutines.runBlocking {
            apiHttpClient(env).use { http ->
                val supervisor = json.decodeFromString<OpenApiDocument>(http.get("${api.trimEnd('/')}/openapi.json").bodyAsText())
                GeneratedRouteCli(json).dispatch(http, api, args, supervisor, stdout, stderr)
            }
        }
    }.getOrElse { error ->
        stderr("error: ${error.message ?: "failed to run generated route"}")
        2
    }
}
```

After the initial green path, reintroduce per-client action alias logic by
making `GeneratedRouteCli.dispatch` fall back to existing generated action
alias behavior when supervisor route matching fails for `clients <id> ...`.
Move existing helper methods from `Main.kt` into `GeneratedRouteCli.kt` rather
than duplicating them.

- [ ] **Step 10: Run CLI tests and iterate**

```sh
mise exec -- gradle :cli:test --tests com.minekube.craftless.cli.CraftlessCliTest
```

Expected: current lifecycle, action alias, and generated help tests pass.

## Task 3: Daemon Naming And Help

**Files:**
- Modify: `cli/src/test/kotlin/com/minekube/craftless/cli/CraftlessCliTest.kt`
- Modify: `cli/src/main/kotlin/com/minekube/craftless/cli/Main.kt`

- [ ] **Step 1: Write failing daemon naming tests**

Update current help tests:

```kotlin
@Test
fun `root help prefers daemon start and hides server start`() {
    val output = StringBuilder()
    val errors = StringBuilder()

    val exit =
        CraftlessCli.run(
            listOf("--help"),
            stdout = { output.appendLine(it) },
            stderr = { errors.appendLine(it) },
        )

    assertEquals(0, exit)
    assertEquals("", errors.toString())
    val help = output.toString()
    assertTrue(help.contains("daemon start"))
    assertFalse(help.contains("server start"))
}

@Test
fun `server start remains a compatibility alias`() {
    val output = StringBuilder()
    var versionStatus = 0

    val exit =
        CraftlessCli.run(
            listOf("server", "start", "--once"),
            stdout = { output.appendLine(it) },
            afterStart = { metadata ->
                kotlinx.coroutines.runBlocking {
                    HttpClient(CIO).use { http ->
                        versionStatus = http.get("${metadata.url}/version").status.value
                    }
                }
            },
        )

    assertEquals(0, exit)
    assertEquals(200, versionStatus)
}
```

Add a parallel `daemon start once prints daemon metadata...` test by copying
the existing `server start once prints server metadata...` test and replacing
the command tokens with `daemon start`.

- [ ] **Step 2: Update registered command paths and help**

In `registeredCommandPaths()`, replace `"server start"` with `"daemon start"`.
Do not include the compatibility alias in primary command paths.

In `root()`, replace:

```kotlin
GroupCommand("server").subcommands(LeafCommand("start"))
```

with:

```kotlin
GroupCommand("daemon").subcommands(LeafCommand("start"))
```

Keep `server start` accepted directly in `run(...)`.

Update group help:

```kotlin
args.isGroupHelp("daemon") -> daemonHelp()
args.isGroupHelp("server") -> serverCompatibilityHelp()
```

`daemonHelp()` should say:

```text
Usage: craftless daemon <command> [args]

Commands:
  daemon start
```

`serverCompatibilityHelp()` should say:

```text
Usage: craftless server <command> [args]

Compatibility alias:
  server start

Prefer `craftless daemon start`.
```

- [ ] **Step 3: Rename internal method without changing behavior**

Rename `runServerStart` to `runDaemonStart` and update both `daemon start`
and `server start` branches to call it. Keep `ApiServerMetadata` for now to
avoid unrelated protocol churn.

- [ ] **Step 4: Run focused CLI tests**

```sh
mise exec -- gradle :cli:test --tests com.minekube.craftless.cli.CraftlessCliTest
```

Expected: `BUILD SUCCESSFUL`.

## Task 4: Per-Client Generated Action Preservation

**Files:**
- Modify: `cli/src/main/kotlin/com/minekube/craftless/cli/GeneratedRouteCli.kt`
- Modify: `cli/src/main/kotlin/com/minekube/craftless/cli/Main.kt`
- Modify: `cli/src/test/kotlin/com/minekube/craftless/cli/CraftlessCliTest.kt`

- [ ] **Step 1: Keep existing action alias tests unchanged**

Do not weaken these tests:

- `generated client action alias dispatches from runtime action metadata`;
- `generated client action alias maps single positional arg to required action argument`;
- `generated nested client action alias dispatches from runtime action metadata`;
- `generated client action alias help is loaded from runtime action metadata`;
- `generated client action alias rejects actions missing from live openapi action metadata`;
- `clients actions uses live openapi as action authority`.

- [ ] **Step 2: Move action alias fallback behind generated route engine**

When supervisor route matching fails and the args start with `clients <id>`,
`GeneratedRouteCli` should fetch `GET /clients/{id}/openapi.json` and reuse
the existing alias algorithm:

```kotlin
if (match == null && args.size >= 4 && args[0] == "clients") {
    return dispatchPerClientGeneratedCommand(http, api, args, stdout, stderr)
}
```

Move these helpers from `Main.kt` into `GeneratedRouteCli.kt`:

- `getClientOpenApiBody`;
- `generatedActionAlias`;
- `generatedResource`;
- `generatedCommandTokens`;
- `actionAliasArguments`;
- `genericActionArguments`;
- `requireRequiredActionArguments`;
- `generatedAliasHelp`;
- `generatedResourceHelp`;
- `generatedActionsHelp`;
- `toolRoute`;
- `toJsonArgument`.

Do not change their behavior except for package-private visibility and the new
class receiver.

- [ ] **Step 3: Keep generic run route safe**

`clients <id> run <action>` should match supervisor route metadata for
`POST /clients/{id}:run`, but it must still validate the action against the
live per-client OpenAPI before invoking.

Implement this as a special generated-route handler when matched route has:

```kotlin
operation.extensions["x-craftless-source"] == "action" &&
operation.extensions["x-craftless-member"] == "run" &&
pathTemplate.endsWith(":run")
```

The handler should use the existing `runClientAction` logic rather than
sending arbitrary body JSON from supervisor schema alone.

- [ ] **Step 4: Run focused CLI tests**

```sh
mise exec -- gradle :cli:test --tests com.minekube.craftless.cli.CraftlessCliTest
```

Expected: `BUILD SUCCESSFUL`.

## Task 5: Documentation And Guardrails

**Files:**
- Modify: `README.md`
- Modify: `.agents/skills/craftless-public-gameplay-agent/SKILL.md`
- Modify: `docs/agent-operating-contract.md`
- Modify: `docs/agent-module-contracts.md`
- Modify: `cli/AGENTS.md`
- Modify: `docs/project-completion-checklist.md`
- Modify: `docs/superpowers/phase-index.md`
- Create: `docs/superpowers/evidence/2026-06-29-generated-route-cli-daemon-naming.md`

- [ ] **Step 1: Update README command examples**

Replace primary startup examples:

```sh
craftless server start --port 8080 --workspace .craftless
```

with:

```sh
craftless daemon start --port 8080 --workspace .craftless
```

Add a compatibility sentence:

```markdown
`craftless server start` remains a compatibility alias for older scripts, but
new docs use `craftless daemon start` to avoid confusion with Minecraft game
servers.
```

- [ ] **Step 2: Update CLI architecture wording**

In README and docs, describe:

```markdown
The CLI has a tiny static kernel for daemon startup and OpenAPI fetching.
Supervisor commands are generated from `GET /openapi.json`; gameplay and
client-runtime commands are generated from `GET /clients/{id}/openapi.json`.
```

- [ ] **Step 3: Update agent skill bootstrap**

In `.agents/skills/craftless-public-gameplay-agent/SKILL.md`, replace startup
examples with:

```sh
craftless daemon start --port 8080 --workspace .craftless
```

Add:

```markdown
Do not assume `server start` starts a Minecraft server. In current docs the
Craftless local API process is `daemon start`; `server start` is only a
compatibility alias.
```

- [ ] **Step 4: Update module contracts**

In `docs/agent-operating-contract.md` CLI section, replace the static command
allowance with:

```markdown
Keep the CLI adaptive: a tiny static kernel may start the Craftless API daemon,
resolve API URLs, fetch OpenAPI, cache specs, and interpret generated
commands. Supervisor API commands should come from `GET /openapi.json`.
Per-client gameplay commands and help must come from
`GET /clients/{id}/openapi.json` and generated action metadata.
```

In `docs/agent-module-contracts.md#cli` and `cli/AGENTS.md`, make the same
boundary explicit.

- [ ] **Step 5: Update phase/checklist**

Add to `docs/superpowers/phase-index.md`:

```markdown
- Phase 189: generated route CLI and daemon naming.
```

Append current direction text:

```markdown
Phase 189 moves the CLI toward an OpenAPI route interpreter: `daemon start`
is the static startup kernel, `server start` remains a hidden compatibility
alias, supervisor commands are generated from `GET /openapi.json`, and
per-client gameplay aliases remain generated from live per-client OpenAPI.
```

Update `docs/project-completion-checklist.md` current state after verification
to mention Phase 189 and point to the new evidence file.

- [ ] **Step 6: Write evidence file**

Create `docs/superpowers/evidence/2026-06-29-generated-route-cli-daemon-naming.md` with this initial content before running verification:

```markdown
# Generated Route CLI And Daemon Naming Evidence

Phase 189 makes the CLI route surface generated from OpenAPI metadata.

Planned verification:

1. `mise exec -- gradle :protocol:test --tests com.minekube.craftless.protocol.OpenApiGenerationTest`
2. `mise exec -- gradle :cli:test --tests com.minekube.craftless.cli.CraftlessCliTest`
3. `git diff --check`
```

After focused verification and full CI, replace `Planned verification` with
the actual commands and pass/fail summaries.

- [ ] **Step 7: Run docs guard**

```sh
git diff --check
```

Expected: no output.

## Task 6: Final Verification And Publish

**Files:** no code edits unless verification reveals a bug.

- [ ] **Step 1: Run focused protocol and CLI tests**

```sh
mise exec -- gradle :protocol:test --tests com.minekube.craftless.protocol.OpenApiGenerationTest
mise exec -- gradle :cli:test --tests com.minekube.craftless.cli.CraftlessCliTest
git diff --check
```

Expected: all pass.

- [ ] **Step 2: Run full CI**

```sh
mise run ci
```

Expected: `BUILD SUCCESSFUL` for Gradle phases, `Craftless CI smoke passed`,
and Bun/Playwright tests pass.

- [ ] **Step 3: Inspect status**

```sh
git status --short --branch
```

Expected before commit: only intended Phase 189 files modified.

- [ ] **Step 4: Commit**

```sh
git add \
  .agents/skills/craftless-public-gameplay-agent/SKILL.md \
  README.md \
  cli/AGENTS.md \
  cli/src/main/kotlin/com/minekube/craftless/cli/Main.kt \
  cli/src/main/kotlin/com/minekube/craftless/cli/GeneratedRouteCli.kt \
  cli/src/test/kotlin/com/minekube/craftless/cli/CraftlessCliTest.kt \
  docs/agent-operating-contract.md \
  docs/agent-module-contracts.md \
  docs/project-completion-checklist.md \
  docs/superpowers/evidence/2026-06-29-generated-route-cli-daemon-naming.md \
  docs/superpowers/phase-index.md \
  docs/superpowers/plans/2026-06-29-189-generated-route-cli-daemon-naming-plan.md \
  docs/superpowers/specs/2026-06-29-189-generated-route-cli-daemon-naming-design.md \
  protocol/src/main/kotlin/com/minekube/craftless/protocol/ApiRoute.kt \
  protocol/src/main/kotlin/com/minekube/craftless/protocol/OpenApiDocument.kt \
  protocol/src/test/kotlin/com/minekube/craftless/protocol/OpenApiGenerationTest.kt
git commit -m "feat: generate cli routes from openapi"
```

- [ ] **Step 5: Push to main if requested**

If the active instruction remains to push directly to `main`, run:

```sh
git push origin main
git status --short --branch
```

Expected after push:

```text
## main...origin/main
```
