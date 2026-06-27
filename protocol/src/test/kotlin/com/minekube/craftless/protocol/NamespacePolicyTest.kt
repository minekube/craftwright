package com.minekube.craftless.protocol

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NamespacePolicyTest {
    @Test
    fun `repository policy scanner sees sibling modules`() {
        val violations =
            repositoryContentViolations(
                include = { path -> path.name == "HmcBridgeDriverBackend.kt" },
            ) { contents ->
                contents.contains("class HmcBridgeDriverBackend")
            }

        assertTrue(
            violations.contains("driver-runtime/src/main/kotlin/com/minekube/craftless/driver/runtime/HmcBridgeDriverBackend.kt"),
            "Repository policy scanner must inspect sibling modules from Gradle module test tasks",
        )
    }

    @Test
    fun `repository uses minekube com namespace`() {
        val previousPackage = "dev" + ".minekube"
        val previousDomain = "minekube" + ".dev"
        val violations =
            repositoryContentViolations { contents ->
                contents.contains(previousPackage) || contents.contains(previousDomain)
            }

        assertTrue(
            violations.isEmpty(),
            "Previous minekube .dev namespace remains:\n${violations.joinToString("\n")}",
        )
    }

    @Test
    fun `repository uses craftless product naming`() {
        val previousBrand = "Craft" + "wright"
        val previousBrandLower = "craft" + "wright"
        val violations =
            repositoryContentViolations { contents ->
                contents.contains(previousBrand) || contents.contains(previousBrandLower)
            }

        assertTrue(
            violations.isEmpty(),
            "Previous " + previousBrand + " naming remains:\n${violations.joinToString("\n")}",
        )
    }

    @Test
    fun `kotlin sources avoid forbidden http implementations`() {
        val forbiddenImports =
            listOf(
                "java.net" + ".http",
                "com.sun.net" + ".httpserver",
                "ok" + "http3",
                "Ok" + "Http",
            )
        val forbiddenHttpEnums =
            listOf(
                "enum class Http" + "Method",
                "enum class HTTP" + "Method",
                "sealed class Http" + "Method",
                "object Http" + "Method",
            )
        val violations =
            repositoryContentViolations(
                include = { path -> path.name.endsWith(".kt") || path.name.endsWith(".kts") },
            ) { contents ->
                forbiddenImports.any(contents::contains) || forbiddenHttpEnums.any(contents::contains)
            }

        assertTrue(
            violations.isEmpty(),
            "Forbidden HTTP implementation or custom method enum remains:\n${violations.joinToString("\n")}",
        )
    }

    @Test
    fun `javascript helper sources stay scoped to playwright`() {
        val root = repositoryRoot()
        val violations =
            Files.walk(root).use { paths ->
                paths
                    .filter { path -> path.isJavaScriptHelperFile() }
                    .filter { path -> !root.relativize(path).pathString.startsWith("playwright/") }
                    .map { path -> root.relativize(path).pathString }
                    .sorted()
                    .toList()
            }

        assertTrue(
            violations.isEmpty(),
            "JavaScript helper files must stay in playwright/ and not recreate a TypeScript SDK:\n" +
                violations.joinToString("\n"),
        )
    }

    @Test
    fun `repository does not include non bun package manager artifacts`() {
        val forbiddenNames =
            setOf(
                "package-lock.json",
                "npm-shrinkwrap.json",
                "yarn.lock",
                "pnpm-lock.yaml",
            )
        val root = repositoryRoot()
        val violations =
            Files.walk(root).use { paths ->
                paths
                    .filter { path -> path.isScannable() && path.name in forbiddenNames }
                    .map { path -> root.relativize(path).pathString }
                    .sorted()
                    .toList()
            }

        assertTrue(
            violations.isEmpty(),
            "Non-Bun package manager artifacts are not allowed:\n${violations.joinToString("\n")}",
        )
    }

    @Test
    fun `mise exposes an architecture check for live openapi action contracts`() {
        val mise = Files.readString(repositoryRoot().resolve(".mise.toml"))

        assertTrue(mise.contains("[tasks.architecture-check]"), "mise must expose architecture-check")
        listOf(
            ":protocol:test",
            ":daemon:test",
            ":cli:test",
            ":driver-fabric:test",
            "bun test playwright",
        ).forEach { requiredCommand ->
            assertTrue(
                mise.contains(requiredCommand),
                "architecture-check must include $requiredCommand",
            )
        }
    }

    @Test
    fun `hand written fabric gameplay descriptors are limited to transitional bootstrap allowlist`() {
        val root = repositoryRoot()
        val allowlistPath = root.resolve("docs/architecture/transitional-fabric-action-allowlist.txt")
        val allowlist =
            Files
                .readAllLines(allowlistPath)
                .map { line -> line.substringBefore("#").trim() }
                .filter { line -> line.isNotBlank() }
                .sorted()
        val actionBindings =
            Files.readString(
                root.resolve(
                    "driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricActionBindings.kt",
                ),
            )
        val handWrittenActionIds =
            Regex("""id = "([a-z][a-z0-9]*(?:\.[a-z][a-z0-9]*)*)"""")
                .findAll(actionBindings)
                .map { match -> match.groupValues[1] }
                .distinct()
                .sorted()
                .toList()

        assertTrue(
            handWrittenActionIds == allowlist,
            "Hand-written Fabric gameplay descriptors are transitional only.\n" +
                "If a new public gameplay action is needed, add it through the runtime capability graph first.\n" +
                "handWritten=$handWrittenActionIds\nallowlist=$allowlist",
        )
    }

    @Test
    fun `driver runtime public errors do not expose bridge internals`() {
        val forbiddenMessage = "bridge" + " returned"
        val root = repositoryRoot()
        val violations =
            repositoryContentViolations(
                include = { path ->
                    val relative = root.relativize(path).pathString
                    relative.startsWith("driver-runtime/src/main/") && path.name.endsWith(".kt")
                },
            ) { contents ->
                contents.contains(forbiddenMessage)
            }

        assertTrue(
            violations.isEmpty(),
            "Driver runtime public errors must not expose bridge internals:\n${violations.joinToString("\n")}",
        )
    }

    @Test
    fun `production kotlin sources do not define or instantiate fake drivers`() {
        val fakeDriverSymbol = "Fake" + "DriverSession"
        val root = repositoryRoot()
        val violations =
            repositoryContentViolations(
                include = { path ->
                    val relative = root.relativize(path).pathString
                    path.name.endsWith(".kt") &&
                        relative.contains("/src/main/") &&
                        !relative.startsWith("testkit/")
                },
            ) { contents ->
                contents.contains(fakeDriverSymbol)
            }

        assertTrue(
            violations.isEmpty(),
            "Fake driver implementations must stay out of product sources:\n${violations.joinToString("\n")}",
        )
    }

    @Test
    fun `public kotlin sources do not expose launcher internals`() {
        val forbiddenNames =
            listOf(
                "Pr" + "ism",
                "Pr" + "ismLauncher",
                "Multi" + "MC",
                "M" + "MC",
                "instance" + ".cfg",
                "mmc" + "-pack",
                "patches" + "/",
                "Managed" + "Pack",
            )
        val root = repositoryRoot()
        val violations =
            repositoryContentViolations(
                include = { path ->
                    val relative = root.relativize(path).pathString
                    path.name.endsWith(".kt") &&
                        !relative.startsWith("docs/") &&
                        !relative.contains("/src/test/")
                },
            ) { contents ->
                forbiddenNames.any(contents::contains)
            }

        assertTrue(
            violations.isEmpty(),
            "Public Kotlin sources must not expose launcher internals:\n${violations.joinToString("\n")}",
        )
    }

    @Test
    fun `public action descriptors reject implementation namespace leaks`() {
        listOf(
            "fabric.screen",
            "yarn.client-player",
            "intermediary.class-1234",
            "hmc.command",
            "headlessmc.launch",
            "prism.instance",
        ).forEach { actionId ->
            assertFailsWith<IllegalArgumentException> {
                OpenApiAction(
                    id = actionId,
                    schemaVersion = "1",
                )
            }
        }

        listOf(
            "fabric-name",
            "yarn-name",
            "intermediary-name",
            "hmc-command",
            "headlessmc-command",
            "prism-instance",
        ).forEach { publicName ->
            assertFailsWith<IllegalArgumentException> {
                OpenApiAction(
                    id = "player.inspect",
                    schemaVersion = "1",
                    arguments = mapOf(publicName to OpenApiActionArgument("string")),
                )
            }

            assertFailsWith<IllegalArgumentException> {
                OpenApiAction(
                    id = "player.inspect",
                    schemaVersion = "1",
                    result =
                        OpenApiActionResult(
                            properties = mapOf("action" to OpenApiActionSchema("string"), publicName to OpenApiActionSchema("string")),
                            required = listOf("action"),
                        ),
                )
            }

            assertFailsWith<IllegalArgumentException> {
                OpenApiAction(
                    id = "player.inspect",
                    schemaVersion = "1",
                    source = OpenApiActionSource.RUNTIME_PROBE,
                    availability = OpenApiActionAvailability.UNAVAILABLE,
                    availabilityReason = publicName,
                )
            }

            assertFailsWith<IllegalArgumentException> {
                OpenApiResourceActionDescriptor(
                    id = "player.inspect",
                    schemaVersion = "1",
                    arguments = mapOf(publicName to OpenApiActionArgument("string")),
                )
            }

            assertFailsWith<IllegalArgumentException> {
                OpenApiResourceActionDescriptor(
                    id = "player.inspect",
                    schemaVersion = "1",
                    source = OpenApiActionSource.RUNTIME_PROBE,
                    availability = OpenApiActionAvailability.UNAVAILABLE,
                    availabilityReason = publicName,
                )
            }
        }
    }

    @Test
    fun `public action descriptors reject scenario shortcut action ids`() {
        listOf(
            "find.tree",
            "find.cow",
            "mine.log",
            "collect.wood",
            "craft.sword",
            "craft.planks",
            "craft.table",
            "make.weapon",
            "kill.cow",
            "hunt.animal",
            "pickup.log",
            "equip.log",
            "build.house",
            "place.log",
        ).forEach { actionId ->
            assertFailsWith<IllegalArgumentException> {
                OpenApiAction(
                    id = actionId,
                    schemaVersion = "1",
                )
            }

            assertFailsWith<IllegalArgumentException> {
                RuntimeOperationNode(
                    id = actionId,
                    resource = actionId.substringBeforeLast("."),
                    adapter = "runtime-probe",
                    availability = RuntimeAvailability.available(),
                )
            }
        }
    }

    @Test
    fun `public route metadata rejects implementation namespace leaks`() {
        listOf(
            {
                ApiRoute(
                    method = "GET",
                    path = "/clients/{id}/fabric:inspect",
                    operationId = "inspectClient",
                    tag = "clients",
                    owner = "clients",
                    member = "inspect",
                    target = "client",
                    source = "route",
                )
            },
            {
                ApiRoute(
                    method = "GET",
                    path = "/clients/{id}/inspect",
                    operationId = "getYarnClientPlayer",
                    tag = "clients",
                    owner = "clients",
                    member = "inspect",
                    target = "client",
                    source = "route",
                )
            },
            {
                ApiRoute(
                    method = "GET",
                    path = "/clients/{id}/inspect",
                    operationId = "inspectClient",
                    tag = "intermediary",
                    owner = "clients",
                    member = "inspect",
                    target = "client",
                    source = "route",
                )
            },
            {
                ApiRoute(
                    method = "GET",
                    path = "/clients/{id}/inspect",
                    operationId = "inspectClient",
                    tag = "clients",
                    owner = "headlessmc",
                    member = "inspect",
                    target = "client",
                    source = "route",
                )
            },
            {
                ApiRoute(
                    method = "GET",
                    path = "/clients/{id}/inspect",
                    operationId = "inspectClient",
                    tag = "clients",
                    owner = "clients",
                    member = "prism-instance",
                    target = "client",
                    source = "route",
                )
            },
        ).forEach { routeFactory ->
            assertFailsWith<IllegalArgumentException> {
                ApiRouteCatalog(listOf(routeFactory()))
            }
        }
    }

    @Test
    fun `public route path policy ignores concrete client id values`() {
        ApiRouteCatalog(
            listOf(
                ApiRoute(
                    method = "GET",
                    path = "/clients/fabric-smoke/openapi.json",
                    operationId = "getClientOpenapiJson",
                    tag = "clients",
                    owner = "clients",
                    member = "openapi",
                    target = "client",
                    source = "route",
                ),
            ),
        )
    }

    private fun repositoryContentViolations(
        include: (Path) -> Boolean = { true },
        predicate: (String) -> Boolean,
    ): List<String> {
        val root = repositoryRoot()
        return Files.walk(root).use { paths ->
            paths
                .filter { path -> path.isScannable() && include(path) }
                .filter { path -> predicate(Files.readString(path)) }
                .map { path -> root.relativize(path).pathString }
                .sorted()
                .toList()
        }
    }

    private fun repositoryRoot(): Path {
        var current = Path.of("").toAbsolutePath().normalize()
        while (!Files.exists(current.resolve("settings.gradle.kts"))) {
            current = requireNotNull(current.parent) { "repository root not found" }
        }
        return current
    }

    private fun Path.isScannable(): Boolean {
        if (isDirectory()) return false
        val ignoredDirectories = setOf("build", ".gradle", ".git", ".kotlin", ".vscode")
        if (iterator().asSequence().any { it.name in ignoredDirectories }) return false
        if (pathString.contains("driver-fabric/run/")) return false
        if (pathString.contains("driver-fabric/logs/")) return false
        if (name.endsWith(".class") || name.endsWith(".jar") || name.endsWith(".png") || name.endsWith(".gz")) return false
        return true
    }

    private fun Path.isJavaScriptHelperFile(): Boolean {
        if (!isScannable()) return false
        return name == "package.json" ||
            name == "tsconfig.json" ||
            name.endsWith(".ts") ||
            name.endsWith(".tsx") ||
            name.endsWith(".js") ||
            name.endsWith(".mjs") ||
            name.endsWith(".cjs")
    }
}
