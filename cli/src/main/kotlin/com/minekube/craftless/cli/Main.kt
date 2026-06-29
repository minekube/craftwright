package com.minekube.craftless.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.minekube.craftless.daemon.CacheMetadataFetcher
import com.minekube.craftless.daemon.ConfiguredClientRuntimeDriverModProvider
import com.minekube.craftless.daemon.KtorCacheMetadataFetcher
import com.minekube.craftless.daemon.LocalSessionApiServer
import com.minekube.craftless.daemon.ProcessClientRuntimeLauncher
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch

fun main(args: Array<String>) {
    val exit = CraftlessCli.run(args.toList(), stdout = ::println, stderr = System.err::println)
    if (exit != 0) {
        kotlin.system.exitProcess(exit)
    }
}

object CraftlessCli {
    private const val CRAFTLESS_HTTP_REQUEST_TIMEOUT_MS = "CRAFTLESS_HTTP_REQUEST_TIMEOUT_MS"
    private const val DEFAULT_HTTP_REQUEST_TIMEOUT_MS = 900_000L

    private val json =
        Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = true
        }

    fun root(): CoreCliktCommand =
        RootCommand().subcommands(
            LeafCommand("api"),
            GroupCommand("daemon").subcommands(
                LeafCommand("start"),
            ),
            GroupCommand("server").subcommands(
                LeafCommand("start"),
            ),
        )

    fun registeredCommandPaths(): Set<String> =
        setOf(
            "api <endpoint>",
            "daemon start",
        )

    fun run(
        args: List<String>,
        stdout: (String) -> Unit,
        stderr: (String) -> Unit = {},
        afterStart: (ApiServerMetadata) -> Unit = {},
        env: Map<String, String> = System.getenv(),
        cacheMetadataFetcher: CacheMetadataFetcher = KtorCacheMetadataFetcher(),
        distributionRoot: Path? = installedDistributionRoot(),
    ): Int {
        if (args.isRootHelp()) {
            stdout(rootHelp())
            return 0
        }
        groupHelp(args)?.let { help ->
            stdout(help)
            return 0
        }
        if (args.startsWithCommand("api")) {
            return ApiCli(json) { environment -> apiHttpClient(environment) }
                .run(args.drop(1), stdout, stderr, env)
        }
        if (args.startsWithCommand("daemon", "start") || args.startsWithCommand("server", "start")) {
            return runDaemonStart(args.drop(2), stdout, stderr, afterStart, env, cacheMetadataFetcher, distributionRoot)
        }
        if (args.isNotEmpty()) {
            stderr("error: unknown command ${args.joinToString(" ")}")
            return 2
        }
        root().main(args)
        return 0
    }

    private fun List<String>.isRootHelp(): Boolean =
        this == listOf("--help") ||
            this == listOf("-h") ||
            this == listOf("help")

    private fun groupHelp(args: List<String>): String? =
        when {
            args.isGroupHelp("api") -> ApiCli(json) { environment -> apiHttpClient(environment) }.help()
            args.isGroupHelp("daemon") -> daemonHelp()
            args.isGroupHelp("server") -> serverHelp()
            else -> null
        }

    private fun List<String>.isGroupHelp(group: String): Boolean =
        this == listOf(group, "--help") ||
            this == listOf(group, "-h") ||
            this == listOf("help", group)

    private fun List<String>.startsWithCommand(vararg tokens: String): Boolean =
        size >= tokens.size && tokens.indices.all { index -> this[index] == tokens[index] }

    private fun rootHelp(): String =
        buildString {
            appendLine("Usage: craftless <command> [args]")
            appendLine()
            appendLine("Automate real Minecraft Java clients for tests, agents, and CI.")
            appendLine()
            appendLine("Commands:")
            registeredCommandPaths().forEach { command ->
                appendLine("  $command")
            }
            appendLine()
            append("Use `craftless api /openapi.json` and `craftless api /clients/{id}/openapi.json` for discovery.")
        }

    private fun daemonHelp(): String =
        buildString {
            appendLine("Usage: craftless daemon <command> [args]")
            appendLine()
            appendLine("Commands:")
            append("  daemon start")
        }

    private fun serverHelp(): String =
        buildString {
            appendLine("Usage: craftless server <command> [args]")
            appendLine()
            appendLine("Commands:")
            append("  server start")
        }

    private fun runDaemonStart(
        args: List<String>,
        stdout: (String) -> Unit,
        stderr: (String) -> Unit,
        afterStart: (ApiServerMetadata) -> Unit,
        env: Map<String, String>,
        cacheMetadataFetcher: CacheMetadataFetcher,
        distributionRoot: Path?,
    ): Int {
        val once = args.contains("--once")
        val host = args.optionValue("--host") ?: "127.0.0.1"
        val port = args.optionValue("--port")?.toIntOrNull() ?: 0
        if (args.optionValue("--port") != null && port < 0) {
            stderr("error: --port must be a non-negative integer")
            return 2
        }
        val workspaceRoot = args.optionValue("--workspace")?.let(Path::of)
        val serverEnvironment = env.withPackagedDriverModConfiguration(distributionRoot)

        LocalSessionApiServer
            .inMemory(
                host = host,
                port = port,
                workspaceRoot = workspaceRoot,
                cacheMetadataFetcher = cacheMetadataFetcher,
                clientRuntimeLauncher = ProcessClientRuntimeLauncher(environment = serverEnvironment),
                clientRuntimeDriverModProvider = ConfiguredClientRuntimeDriverModProvider(environment = serverEnvironment),
            ).use { server ->
                server.start()
                val metadata =
                    ApiServerMetadata(
                        ok = true,
                        url = server.url(""),
                        openapi = "/openapi.json",
                        events = "/events",
                        workspace = workspaceRoot?.toString(),
                    )
                stdout(json.encodeToString(metadata))
                afterStart(metadata)
                if (!once) {
                    CountDownLatch(1).await()
                }
            }
        return 0
    }

    private fun Map<String, String>.withPackagedDriverModConfiguration(distributionRoot: Path?): Map<String, String> {
        val manifestKey = ConfiguredClientRuntimeDriverModProvider.CRAFTLESS_DRIVER_MOD_MANIFEST
        val fabricModKey = ConfiguredClientRuntimeDriverModProvider.CRAFTLESS_FABRIC_DRIVER_MOD
        if (!get(manifestKey).isNullOrBlank() || !get(fabricModKey).isNullOrBlank()) {
            return this
        }
        val packagedManifest =
            distributionRoot
                ?.resolve("driver-mods.json")
                ?.takeIf(Files::isRegularFile)
        if (packagedManifest != null) {
            return this + (manifestKey to packagedManifest.toString())
        }
        val packagedDriverMod =
            distributionRoot
                ?.resolve("mods")
                ?.resolve("craftless-driver-fabric.jar")
                ?.takeIf(Files::isRegularFile)
                ?: return this
        return this + (fabricModKey to packagedDriverMod.toString())
    }

    private fun installedDistributionRoot(): Path? =
        runCatching {
            val codeSource = CraftlessCli::class.java.protectionDomain.codeSource
            val codeSourceUri = codeSource.location.toURI()
            val location = Path.of(codeSourceUri).toAbsolutePath().normalize()
            val parent = location.parent
            if (Files.isRegularFile(location) && parent?.fileName?.toString() == "lib") {
                parent.parent
            } else {
                null
            }
        }.getOrNull()

    private fun List<String>.optionValue(name: String): String? {
        val index = indexOf(name)
        return if (index >= 0 && index + 1 < size) this[index + 1] else null
    }

    private fun apiHttpClient(env: Map<String, String>): HttpClient =
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = env.apiRequestTimeoutMillis()
            }
        }

    private fun Map<String, String>.apiRequestTimeoutMillis(): Long =
        get(CRAFTLESS_HTTP_REQUEST_TIMEOUT_MS)
            ?.takeIf { it.isNotBlank() }
            ?.toLongOrNull()
            ?.takeIf { it > 0 }
            ?: DEFAULT_HTTP_REQUEST_TIMEOUT_MS
}

@Serializable
data class ApiServerMetadata(
    val ok: Boolean,
    val url: String,
    val openapi: String,
    val events: String,
    val workspace: String? = null,
)

private class RootCommand :
    CoreCliktCommand(
        name = "craftless",
    ) {
    override fun help(context: Context): String = "Automate real Minecraft Java clients for tests, agents, and CI."

    override fun run() = Unit
}

private class GroupCommand(
    name: String,
) : CoreCliktCommand(name = name) {
    override fun run() = Unit
}

private class LeafCommand(
    name: String,
) : CoreCliktCommand(name = name) {
    override fun run() = Unit
}
