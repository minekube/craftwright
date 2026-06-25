package com.minekube.craftless.testkit

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object LocalMinecraftServerSmoke {
    fun run(config: LocalMinecraftServerSmokeConfig = LocalMinecraftServerSmokeConfig.fromEnvironment()): LocalMinecraftServerSmokeResult =
        runWithServer(config)

    fun runWithServer(
        config: LocalMinecraftServerSmokeConfig = LocalMinecraftServerSmokeConfig.fromEnvironment(),
        provisionServerJar: suspend (LocalServerLayout, HttpClient) -> Path = { layout, http ->
            layout.provisionMinecraftServerJar(
                version = config.minecraftVersion,
                provisioner = MinecraftServerJarProvisioner(http),
            )
        },
        action: (LocalServerLayout, LocalMinecraftServerHandle) -> Unit = { _, _ -> },
    ): LocalMinecraftServerSmokeResult {
        if (!config.enabled) {
            return LocalMinecraftServerSmokeResult(
                status = LocalMinecraftServerSmokeStatus.SKIPPED,
                message = "set CRAFTLESS_LOCAL_SERVER_SMOKE=1 to run the local server smoke",
                root = config.root,
            )
        }

        return runBlocking {
            HttpClient(CIO).use { http ->
                val layout = LocalServerFixture(root = config.root, port = config.port).prepare()
                val serverJar = provisionServerJar(layout, http)
                val server =
                    layout.startMinecraftServer(
                        serverJar = serverJar,
                        javaExecutable = config.javaExecutable,
                        minHeap = config.minHeap,
                        maxHeap = config.maxHeap,
                        readinessTimeoutMillis = config.readinessTimeoutMillis,
                        shutdownTimeoutMillis = config.shutdownTimeoutMillis,
                    )
                var commandResult: LocalMinecraftSmokeCommandResult? = null
                val processResult =
                    runCatching {
                        action(layout, server)
                        commandResult = runConfiguredActionCommand(config, layout)
                        server.stopAndCollect()
                    }.getOrElse { failure ->
                        if (server.isRunning()) {
                            server.stopAndCollect()
                        }
                        throw failure
                    }
                LocalMinecraftServerSmokeResult(
                    status = LocalMinecraftServerSmokeStatus.RAN,
                    message = "local Minecraft server smoke collected ${processResult.evidenceCount} evidence event(s)",
                    root = config.root,
                    serverLog = layout.serverLog,
                    evidenceLog = layout.evidenceLog,
                    exitCode = processResult.exitCode,
                    evidenceCount = processResult.evidenceCount,
                    actionLog = commandResult?.log,
                    actionExitCode = commandResult?.exitCode,
                    evidenceSummary = layout.assertExpectedEvidence(config),
                )
            }
        }
    }
}

private const val CRAFTLESS_SMOKE_SERVER_PORT_ENV = "CRAFTLESS_SMOKE_SERVER_PORT"
private const val CRAFTLESS_SMOKE_ARTIFACTS_DIR_ENV = "CRAFTLESS_SMOKE_ARTIFACTS_DIR"
private const val DEFAULT_MINECRAFT_SERVER_PORT = 25565

data class LocalMinecraftServerSmokeResult(
    val status: LocalMinecraftServerSmokeStatus,
    val message: String,
    val root: Path,
    val serverLog: Path? = null,
    val evidenceLog: Path? = null,
    val exitCode: Int? = null,
    val evidenceCount: Int = 0,
    val actionLog: Path? = null,
    val actionExitCode: Int? = null,
    val evidenceSummary: LocalMinecraftSmokeEvidenceSummary = LocalMinecraftSmokeEvidenceSummary(),
)

data class LocalMinecraftSmokeEvidenceSummary(
    val playerJoined: Boolean = false,
    val chatObserved: Boolean = false,
    val playerDisconnected: Boolean = false,
)

enum class LocalMinecraftServerSmokeStatus {
    SKIPPED,
    RAN,
}

fun main() {
    val result = LocalMinecraftServerSmoke.run()
    println(result.message)
    result.serverLog?.let { println("serverLog=$it") }
    result.evidenceLog?.let { println("evidenceLog=$it") }
    result.exitCode?.let { println("exitCode=$it") }
}

data class LocalMinecraftServerSmokeConfig(
    val enabled: Boolean,
    val root: Path,
    val minecraftVersion: String = "1.21.6",
    val port: Int = DEFAULT_MINECRAFT_SERVER_PORT,
    val javaExecutable: Path = Path.of(System.getProperty("java.home")).resolve("bin").resolve("java"),
    val actionCommand: List<String> = emptyList(),
    val actionTimeoutMillis: Long = 300_000,
    val expectedPlayer: String? = null,
    val expectedChatMessage: String? = null,
    val expectDisconnect: Boolean = false,
    val readinessTimeoutMillis: Long = 120_000,
    val shutdownTimeoutMillis: Long = 10_000,
    val minHeap: String = "512M",
    val maxHeap: String = "1G",
) {
    init {
        require(minecraftVersion.isNotBlank()) { "minecraft smoke version is required" }
        require(port in 1..65535) { "minecraft smoke server port must be between 1 and 65535" }
        require(actionCommand.none { it.isBlank() }) { "minecraft smoke action command entries must not be blank" }
        require(actionTimeoutMillis > 0) { "minecraft smoke action timeout must be positive" }
        expectedPlayer?.let { require(it.isNotBlank()) { "minecraft smoke expected player must not be blank" } }
        expectedChatMessage?.let { require(it.isNotBlank()) { "minecraft smoke expected chat must not be blank" } }
        require(readinessTimeoutMillis > 0) { "minecraft smoke readiness timeout must be positive" }
        require(shutdownTimeoutMillis > 0) { "minecraft smoke shutdown timeout must be positive" }
        require(minHeap.isNotBlank()) { "minecraft smoke minimum heap is required" }
        require(maxHeap.isNotBlank()) { "minecraft smoke maximum heap is required" }
    }

    companion object {
        private const val ENABLED = "CRAFTLESS_LOCAL_SERVER_SMOKE"
        private const val FABRIC_ENABLED = "CRAFTLESS_FABRIC_CLIENT_SMOKE"
        private const val ROOT = "CRAFTLESS_LOCAL_SERVER_SMOKE_ROOT"
        private const val VERSION = "CRAFTLESS_SMOKE_MINECRAFT_VERSION"
        private const val JAVA_EXECUTABLE = "CRAFTLESS_SMOKE_JAVA_EXECUTABLE"
        private const val ACTION_COMMAND_JSON = "CRAFTLESS_SMOKE_ACTION_COMMAND_JSON"
        private const val ACTION_TIMEOUT = "CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS"
        private const val EXPECT_PLAYER = "CRAFTLESS_SMOKE_EXPECT_PLAYER"
        private const val EXPECT_CHAT_MESSAGE = "CRAFTLESS_SMOKE_EXPECT_CHAT_MESSAGE"
        private const val EXPECT_DISCONNECT = "CRAFTLESS_SMOKE_EXPECT_DISCONNECT"
        private const val READINESS_TIMEOUT = "CRAFTLESS_SMOKE_READINESS_TIMEOUT_MS"
        private const val SHUTDOWN_TIMEOUT = "CRAFTLESS_SMOKE_SHUTDOWN_TIMEOUT_MS"
        private const val MIN_HEAP = "CRAFTLESS_SMOKE_MIN_HEAP"
        private const val MAX_HEAP = "CRAFTLESS_SMOKE_MAX_HEAP"

        fun fromEnvironment(env: Map<String, String> = System.getenv()): LocalMinecraftServerSmokeConfig {
            val enabled = env.isEnabled(ENABLED) || env.isEnabled(FABRIC_ENABLED)
            return LocalMinecraftServerSmokeConfig(
                enabled = enabled,
                root =
                    env[ROOT]
                        ?.takeIf { it.isNotBlank() }
                        ?.let(Path::of)
                        ?: Path.of("build", "craftless-local-server-smoke"),
                minecraftVersion = env[VERSION]?.takeIf { it.isNotBlank() } ?: "1.21.6",
                port =
                    env[CRAFTLESS_SMOKE_SERVER_PORT_ENV]?.toIntStrict(CRAFTLESS_SMOKE_SERVER_PORT_ENV)
                        ?: if (enabled) findAvailableSmokePort() else DEFAULT_MINECRAFT_SERVER_PORT,
                javaExecutable =
                    env[JAVA_EXECUTABLE]
                        ?.takeIf { it.isNotBlank() }
                        ?.let(Path::of)
                        ?: Path.of(System.getProperty("java.home")).resolve("bin").resolve("java"),
                actionCommand =
                    env[ACTION_COMMAND_JSON]
                        ?.takeIf { it.isNotBlank() }
                        ?.let { smokeJson.decodeFromString<List<String>>(it) }
                        ?: emptyList(),
                actionTimeoutMillis = env[ACTION_TIMEOUT]?.toLongStrict(ACTION_TIMEOUT) ?: 300_000,
                expectedPlayer = env[EXPECT_PLAYER]?.takeIf { it.isNotBlank() },
                expectedChatMessage = env[EXPECT_CHAT_MESSAGE]?.takeIf { it.isNotBlank() },
                expectDisconnect = env.isEnabled(EXPECT_DISCONNECT),
                readinessTimeoutMillis = env[READINESS_TIMEOUT]?.toLongStrict(READINESS_TIMEOUT) ?: 120_000,
                shutdownTimeoutMillis = env[SHUTDOWN_TIMEOUT]?.toLongStrict(SHUTDOWN_TIMEOUT) ?: 10_000,
                minHeap = env[MIN_HEAP]?.takeIf { it.isNotBlank() } ?: "512M",
                maxHeap = env[MAX_HEAP]?.takeIf { it.isNotBlank() } ?: "1G",
            )
        }

        private fun String.toIntStrict(name: String): Int = toIntOrNull() ?: error("$name must be an integer")

        private fun String.toLongStrict(name: String): Long = toLongOrNull() ?: error("$name must be a long integer")

        private fun Map<String, String>.isEnabled(name: String): Boolean = this[name] == "1" || this[name].equals("true", ignoreCase = true)

        private fun findAvailableSmokePort(): Int {
            repeat(16) {
                ServerSocket(0).use { socket ->
                    val port = socket.localPort
                    if (port != DEFAULT_MINECRAFT_SERVER_PORT) {
                        return port
                    }
                }
            }
            error("could not allocate a non-default Minecraft smoke server port")
        }
    }
}

data class LocalMinecraftSmokeCommandResult(
    val log: Path,
    val exitCode: Int,
)

private fun runConfiguredActionCommand(
    config: LocalMinecraftServerSmokeConfig,
    layout: LocalServerLayout,
): LocalMinecraftSmokeCommandResult? {
    if (config.actionCommand.isEmpty()) {
        return null
    }
    Files.createDirectories(layout.artifactsDir)
    val output = Collections.synchronizedList(mutableListOf<String>())
    val process =
        ProcessBuilder(config.actionCommand)
            .directory(config.root.toFile())
            .also { builder ->
                builder.environment()[CRAFTLESS_SMOKE_SERVER_PORT_ENV] = config.port.toString()
                builder.environment()[CRAFTLESS_SMOKE_ARTIFACTS_DIR_ENV] =
                    layout.artifactsDir
                        .toAbsolutePath()
                        .normalize()
                        .toString()
            }.redirectErrorStream(true)
            .start()
    val outputReader =
        thread(name = "craftless-smoke-action-output") {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line -> output += line }
            }
        }

    val exited = process.waitFor(config.actionTimeoutMillis, TimeUnit.MILLISECONDS)
    if (!exited) {
        process.destroyForcibly()
        outputReader.join(1_000)
        val log = layout.writeSmokeActionOutput(output.snapshot())
        error("minecraft smoke action command timed out after ${config.actionTimeoutMillis}ms; log=$log")
    }

    outputReader.join()
    val log = layout.writeSmokeActionOutput(output.snapshot())
    val exitCode = process.exitValue()
    check(exitCode == 0) { "minecraft smoke action command exited with $exitCode; log=$log" }
    return LocalMinecraftSmokeCommandResult(log = log, exitCode = exitCode)
}

private fun LocalServerLayout.assertExpectedEvidence(config: LocalMinecraftServerSmokeConfig): LocalMinecraftSmokeEvidenceSummary {
    val evidence = readEvidence()
    val playerJoined =
        config.expectedPlayer?.let { player ->
            evidence.any { it.type == LocalServerEvidenceType.PLAYER_JOINED && it.player == player }
        } ?: false
    val chatObserved =
        config.expectedChatMessage?.let { message ->
            evidence.any {
                it.type == LocalServerEvidenceType.CHAT &&
                    (config.expectedPlayer == null || it.player == config.expectedPlayer) &&
                    it.message == message
            }
        } ?: false
    val playerDisconnected =
        if (config.expectDisconnect) {
            evidence.any {
                it.type == LocalServerEvidenceType.PLAYER_DISCONNECTED &&
                    (config.expectedPlayer == null || it.player == config.expectedPlayer)
            }
        } else {
            false
        }

    if (config.expectedPlayer != null) {
        check(playerJoined) {
            "expected player join evidence for ${config.expectedPlayer}; evidenceLog=$evidenceLog"
        }
    }
    if (config.expectedChatMessage != null) {
        check(chatObserved) {
            "expected chat evidence ${config.expectedChatMessage}; evidenceLog=$evidenceLog"
        }
    }
    if (config.expectDisconnect) {
        check(playerDisconnected) {
            "expected player disconnect evidence for ${config.expectedPlayer ?: "any player"}; evidenceLog=$evidenceLog"
        }
    }

    return LocalMinecraftSmokeEvidenceSummary(
        playerJoined = playerJoined,
        chatObserved = chatObserved,
        playerDisconnected = playerDisconnected,
    )
}

private fun LocalServerLayout.writeSmokeActionOutput(output: List<String>): Path {
    val log = artifactsDir.resolve("smoke-action.log")
    Files.writeString(log, output.joinToString("\n", postfix = "\n"), CREATE, TRUNCATE_EXISTING)
    return log
}

private fun <T> MutableList<T>.snapshot(): List<T> = synchronized(this) { toList() }

private val smokeJson =
    Json {
        ignoreUnknownKeys = true
    }
