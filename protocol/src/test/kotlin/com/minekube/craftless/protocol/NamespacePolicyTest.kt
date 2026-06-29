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
    fun `kotlin quality gates include explicit unused and dead code checks`() {
        val root = repositoryRoot()
        val detekt = Files.readString(root.resolve("config/detekt/detekt.yml"))
        val mise = Files.readString(root.resolve(".mise.toml"))

        listOf(
            "UnusedImport",
            "UnusedParameter",
            "UnusedPrivateClass",
            "UnusedPrivateFunction",
            "UnusedPrivateProperty",
            "UnusedVariable",
            "UnreachableCatchBlock",
            "UnreachableCode",
            "UnusedUnaryOperator",
        ).forEach { rule ->
            assertTrue(detekt.contains("$rule:"), "detekt config must explicitly include $rule")
        }
        assertTrue(mise.contains("[tasks.unused-check]"), "mise must expose an explicit unused-check task")
        assertTrue(mise.contains("mise exec -- gradle detekt"), "unused-check must run Detekt")
        assertTrue(mise.contains("mise run unused-check"), "ci must include the explicit unused-check task")
    }

    @Test
    fun `ci executes craftless daemon smoke through packaged cli`() {
        val root = repositoryRoot()
        val mise = Files.readString(root.resolve(".mise.toml"))
        val workflow = Files.readString(root.resolve(".github/workflows/ci.yml"))
        val smokeScript = root.resolve("scripts/ci-craftless-smoke.sh")
        val smokeScriptText = if (Files.isRegularFile(smokeScript)) Files.readString(smokeScript) else ""

        assertTrue(
            mise.contains("[tasks.ci-craftless-smoke]"),
            "mise must expose a CI Craftless daemon smoke task",
        )
        assertTrue(
            mise.contains("mise run package-cli"),
            "CI Craftless smoke must build the release-style packaged CLI distribution",
        )
        assertTrue(
            mise.contains("scripts/ci-craftless-smoke.sh"),
            "CI Craftless smoke must run the daemon smoke script",
        )
        assertTrue(
            mise.contains("mise run ci-craftless-smoke"),
            "mise run ci must include the Craftless daemon smoke task",
        )
        assertTrue(
            workflow.contains("mise run ci"),
            "GitHub Actions CI must execute the mise ci task that includes Craftless smoke",
        )
        assertTrue(
            Files.isRegularFile(smokeScript),
            "CI Craftless daemon smoke script must exist",
        )
        listOf(
            "daemon start",
            "/openapi.json",
            "/version",
            "build/docker/craftless/bin/craftless",
        ).forEach { required ->
            assertTrue(
                smokeScriptText.contains(required),
                "CI Craftless daemon smoke script must contain $required",
            )
        }
    }

    @Test
    fun `public gameplay agent skill keeps generated workflow guidance`() {
        val skill =
            Files.readString(
                repositoryRoot().resolve(".agents/skills/craftless-public-gameplay-agent/SKILL.md"),
            )
        val required =
            listOf(
                "GET /openapi.json",
                "GET /clients/{id}/openapi.json",
                "craftless clients <id> actions",
                "craftless clients <id> run <action>",
                "POST /clients/{id}:run",
                "POST JSON-RPC-style",
                "GET /clients/{id}/events:stream",
                "missing-generic-primitive",
                "Fresh State Gate",
                "GET /clients/{id}/events",
                "lsof",
                "POST /clients/{id}:stop",
                "public-agent-state.jsonl",
                "without server-provisioned inventory",
            )

        val missing = required.filterNot(skill::contains)

        assertTrue(
            missing.isEmpty(),
            "Public gameplay agent skill is missing generated workflow guidance:\n${missing.joinToString("\n")}",
        )
    }

    @Test
    fun `agent governance requires fresh live state before status claims`() {
        val root = repositoryRoot()
        val rootAgents = Files.readString(root.resolve("AGENTS.md"))
        val operatingContract = Files.readString(root.resolve("docs/agent-operating-contract.md"))
        val runbook = Files.readString(root.resolve("docs/final-gameplay-runbook.md"))

        assertTrue(
            rootAgents.contains("Live status claims must be fresh"),
            "Root AGENTS.md must keep the fresh live-status non-negotiable visible.",
        )
        listOf(
            "Live State Freshness",
            "Treat every previous agent message",
            "GET /clients/{id}/events:stream",
            "lsof",
            "POST /clients/{id}:stop",
        ).forEach { required ->
            assertTrue(
                operatingContract.contains(required),
                "Agent operating contract must require fresh state evidence: $required",
            )
        }
        listOf(
            "Live State Hygiene",
            "lsof -nP -iTCP:8080",
            "curl -fsS \"\$CRAFTLESS/clients\"",
            "old state must be called stale",
        ).forEach { required ->
            assertTrue(
                runbook.contains(required),
                "Final gameplay runbook must include live-state hygiene: $required",
            )
        }
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
    fun `package cli stages craftless fabric driver mod for docker runtime`() {
        val root = repositoryRoot()
        val mise = Files.readString(root.resolve(".mise.toml"))
        val cliBuild = Files.readString(root.resolve("cli/build.gradle.kts"))
        val dockerfile = Files.readString(root.resolve("Dockerfile"))

        assertTrue(
            cliBuild.contains("project(\":driver-fabric\")"),
            "CLI distribution must depend on the Fabric driver module as a packaged runtime artifact",
        )
        assertTrue(
            cliBuild.contains("tasks.named(\"remapJar\")"),
            "CLI distribution must package the remapped Fabric driver jar, not the dev jar",
        )
        assertTrue(
            cliBuild.contains("from(stageFabricDriverLaneArtifacts)") && cliBuild.contains("from(driverModManifest)"),
            "CLI distribution must include manifest-driven Fabric driver lane artifacts",
        )
        assertTrue(
            mise.contains("tar -tf cli/build/distributions/craftless-*.tar"),
            "package-cli must verify the tar distribution contains the Fabric driver mod",
        )
        assertTrue(
            mise.contains("grep -q '/mods/craftless-driver-fabric.jar$'"),
            "package-cli must verify the default Fabric driver mod path in packaged archives",
        )
        assertTrue(
            mise.contains("jar tf cli/build/distributions/craftless-*.zip"),
            "package-cli must verify the zip distribution contains the Fabric driver mod",
        )
        assertTrue(
            mise.contains(":driver-fabric:remapJar"),
            "package-cli must build the remapped Fabric driver mod jar",
        )
        assertTrue(
            mise.contains("build/docker/craftless/mods"),
            "package-cli must create a runtime mods directory in the Docker context",
        )
        assertTrue(
            mise.contains("craftless-driver-fabric.jar"),
            "package-cli must stage the Fabric driver mod with a deterministic runtime filename",
        )
        assertTrue(
            mise.contains("! -name 'driver-fabric-*_*'"),
            "package-cli must not stage stale lane-named placeholder jars",
        )
        assertTrue(
            mise.contains("grep -q '^fabric.mod.json$'"),
            "package-cli must verify the staged Fabric driver mod contains Fabric metadata",
        )
        assertTrue(
            dockerfile.contains(
                "ENV CRAFTLESS_FABRIC_DRIVER_MOD=/opt/craftless/mods/craftless-driver-fabric.jar",
            ),
            "Docker runtime must point the daemon at the staged Fabric driver mod",
        )
    }

    @Test
    fun `fabric driver mod declares nested runtime dependencies`() {
        val root = repositoryRoot()
        val build = Files.readString(root.resolve("driver-fabric/build.gradle.kts"))
        val mise = Files.readString(root.resolve(".mise.toml"))
        val requiredIncludes =
            listOf(
                "include(project(\":protocol\"))",
                "include(project(\":driver-api\"))",
                "include(project(\":driver-runtime\"))",
                "include(project(\":daemon\"))",
                "include(project(\":bridge-hmc\"))",
                "include(\"io.ktor:ktor-client-core-jvm:3.5.0\")",
                "include(\"io.ktor:ktor-client-cio-jvm:3.5.0\")",
                "include(\"io.ktor:ktor-server-core-jvm:3.5.0\")",
                "include(\"io.ktor:ktor-server-cio-jvm:3.5.0\")",
                "include(\"org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0\")",
                "include(\"org.jetbrains.kotlin:kotlin-stdlib:2.4.0\")",
                "include(\"org.jetbrains.kotlin:kotlin-reflect:2.4.0\")",
                "include(\"org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0\")",
                "include(\"org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.11.0\")",
                "include(\"org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.11.0\")",
                "include(\"io.ktor:ktor-http-jvm:3.5.0\")",
                "include(\"io.ktor:ktor-http-cio-jvm:3.5.0\")",
                "include(\"io.ktor:ktor-utils-jvm:3.5.0\")",
                "include(\"io.ktor:ktor-io-jvm:3.5.0\")",
                "include(\"io.ktor:ktor-network-jvm:3.5.0\")",
                "include(\"io.ktor:ktor-network-tls-jvm:3.5.0\")",
                "include(\"com.typesafe:config:1.4.8\")",
            )

        val missing = requiredIncludes.filterNot(build::contains)

        assertTrue(
            missing.isEmpty(),
            "Fabric driver mod must nest runtime dependencies for real client classloading:\n${missing.joinToString("\n")}",
        )
        assertTrue(
            mise.contains("grep -q '^META-INF/jars/.\\\\+\\\\.jar$'"),
            "package-cli must verify the staged Fabric driver mod contains nested runtime jars",
        )
        assertTrue(
            mise.contains("grep -q '^META-INF/jars/kotlin-stdlib-"),
            "package-cli must verify the staged Fabric driver mod contains Kotlin stdlib",
        )
        assertTrue(
            mise.contains("grep -q '^META-INF/jars/kotlinx-coroutines-core-jvm-"),
            "package-cli must verify the staged Fabric driver mod contains coroutines",
        )
        assertTrue(
            mise.contains("grep -q '^META-INF/jars/ktor-http-jvm-"),
            "package-cli must verify the staged Fabric driver mod contains Ktor transitive HTTP runtime",
        )
    }

    @Test
    fun `private fabric execution adapters are limited to discovered operation id references`() {
        val root = repositoryRoot()
        val allowlistPath = root.resolve("docs/architecture/transitional-fabric-action-allowlist.txt")
        val executionAdapters =
            Files.readString(
                root.resolve(
                    "driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricExecutionAdapters.kt",
                ),
            )
        val bootstrapDefinitions =
            Files.readString(
                root.resolve(
                    "driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricBootstrapOperationDefinitions.kt",
                ),
            )
        val capabilityProbe =
            Files.readString(
                root.resolve(
                    "driver-fabric/src/main/kotlin/com/minekube/craftless/driver/fabric/v1_21_6/FabricCapabilityProbe.kt",
                ),
            )
        val executionAdapterOperationConstants =
            Regex("""operationId(?:\s*:\s*String)?\s*=\s*FabricBootstrapOperationIds\.([A-Z0-9_]+)""")
                .findAll(executionAdapters)
                .map { match -> match.groupValues[1] }
                .distinct()
                .sorted()
                .toList()
        val bootstrapDefinitionConstants =
            Regex("""id\s*=\s*FabricBootstrapOperationIds\.([A-Z0-9_]+)""")
                .findAll(bootstrapDefinitions)
                .map { match -> match.groupValues[1] }
                .distinct()
                .sorted()
                .toSet()
        val discoveredOperationConstants =
            Regex("""id\s*=\s*FabricBootstrapOperationIds\.([A-Z0-9_]+)""")
                .findAll(capabilityProbe)
                .map { match -> match.groupValues[1] }
                .distinct()
                .sorted()
                .toSet()
        val graphOperationConstants = bootstrapDefinitionConstants + discoveredOperationConstants
        val executionAdapterOperationIdLiterals =
            Regex("""operationId(?:\s*:\s*String)?\s*=\s*"([a-z][a-z0-9]*(?:\.[a-z][a-z0-9]*)*)"""")
                .findAll(executionAdapters)
                .map { match -> match.groupValues[1] }
                .distinct()
                .sorted()
                .toList()

        assertTrue(
            executionAdapterOperationIdLiterals.isEmpty(),
            "Private Fabric execution adapters must reference discovered operation id constants instead of owning literals:\n" +
                executionAdapterOperationIdLiterals.joinToString("\n"),
        )
        assertTrue(
            !Files.exists(allowlistPath),
            "The deleted transitional Fabric action allowlist must not be recreated.",
        )
        assertTrue(
            executionAdapterOperationConstants.isNotEmpty(),
            "Private Fabric execution adapters must reference FabricBootstrapOperationIds constants.",
        )
        assertTrue(
            graphOperationConstants.containsAll(executionAdapterOperationConstants),
            "Private Fabric execution adapter operation constants must be represented by runtime graph operation sources.\n" +
                "adapterConstants=$executionAdapterOperationConstants\n" +
                "bootstrapDefinitionConstants=${bootstrapDefinitionConstants.sorted()}\n" +
                "discoveredOperationConstants=${discoveredOperationConstants.sorted()}",
        )
        assertTrue(
            executionAdapters.contains("FabricBootstrapOperationIds."),
            "Private Fabric execution adapters must reference FabricBootstrapOperationIds constants.",
        )
        assertTrue(
            "DriverActionDescriptor" !in executionAdapters && "DriverActionArgument" !in executionAdapters,
            "Private Fabric execution adapters must not own public descriptors or schemas.",
        )
    }

    @Test
    fun `daemon live event normalization does not synthesize gameplay action ids`() {
        val root = repositoryRoot()
        val daemon =
            Files.readString(
                root.resolve("daemon/src/main/kotlin/com/minekube/craftless/daemon/LocalSessionApiServer.kt"),
            )
        val forbidden =
            listOf(
                """operationId ?: "player.chat"""",
                """operationId ?: "player.move"""",
                """type == "chat" ->""",
                """type == "movement" ->""",
            ).filter(daemon::contains)

        assertTrue(
            forbidden.isEmpty(),
            "Daemon live event normalization must not synthesize gameplay action ids:\n" +
                forbidden.joinToString("\n"),
        )
    }

    @Test
    fun `adaptive cli and daemon production sources do not own static gameplay catalogs`() {
        val root = repositoryRoot()
        val forbiddenGameplayLiterals =
            listOf(
                "player.chat",
                "player.move",
                "world.block.break",
                "world.block.interact",
                "inventory.query",
                "inventory.equip",
                "entity.attack",
                "recipe.craft",
                "navigation.plan",
                "navigation.follow",
                "/player:chat",
                "/player:move",
                "/world:block",
                "find.tree",
                "craft.sword",
                "kill.cow",
                "task.survival",
            )
        val violations =
            repositoryContentViolations(
                include = { path ->
                    val relative = root.relativize(path).pathString
                    (relative.startsWith("cli/src/main/kotlin/") || relative.startsWith("daemon/src/main/kotlin/")) &&
                        relative.endsWith(".kt")
                },
            ) { contents ->
                forbiddenGameplayLiterals.any(contents::contains)
            }

        assertTrue(
            violations.isEmpty(),
            "Production CLI and daemon sources must not own static gameplay command catalogs or alias route families:\n" +
                violations.joinToString("\n"),
        )
    }

    @Test
    fun `active code and governance avoid stale invoke wording`() {
        val staleInvokePattern =
            Regex(
                pattern = "(legacy|fabric legacy)[\\s`_./:-]*invoke",
                option = RegexOption.IGNORE_CASE,
            )
        val root = repositoryRoot()
        val violations =
            repositoryContentViolations(
                include = { path ->
                    val relative = root.relativize(path).pathString
                    when {
                        relative == "AGENTS.md" -> true
                        relative == "docs/project-completion-checklist.md" -> true
                        relative.startsWith("docs/superpowers/specs/") && relative.endsWith(".md") -> true
                        relative.startsWith("docs/superpowers/plans/") && relative.endsWith(".md") -> true
                        relative.endsWith(".kt") || relative.endsWith(".kts") -> true
                        else -> false
                    }
                },
            ) { contents ->
                staleInvokePattern.containsMatchIn(contents)
            }

        assertTrue(
            violations.isEmpty(),
            "Active code and governance must use generic invoke compatibility wording:\n" +
                violations.joinToString("\n"),
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
        val ignoredDirectories = setOf("build", ".craftless", ".gradle", ".git", ".kotlin", ".vscode")
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
