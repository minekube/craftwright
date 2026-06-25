package com.minekube.craftless.testkit

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.readLines
import kotlin.io.path.writeText

data class LocalServerFixture(
    val root: Path,
    val port: Int,
) {
    fun prepare(): LocalServerLayout {
        Files.createDirectories(root)
        val logsDir = root.resolve("logs")
        val artifactsDir = root.resolve("artifacts")
        Files.createDirectories(logsDir)
        Files.createDirectories(artifactsDir)

        val serverProperties = root.resolve("server.properties")
        serverProperties.writeText(
            """
            online-mode=false
            server-port=$port
            enable-command-block=true
            spawn-protection=0
            """.trimIndent() + "\n"
        )

        return LocalServerLayout(
            root = root,
            serverProperties = serverProperties,
            eulaFile = root.resolve("eula.txt"),
            logsDir = logsDir,
            artifactsDir = artifactsDir,
            serverLog = logsDir.resolve("server.log"),
            evidenceLog = artifactsDir.resolve("server-evidence.jsonl"),
        )
    }
}

data class LocalServerLayout(
    val root: Path,
    val serverProperties: Path,
    val eulaFile: Path,
    val logsDir: Path,
    val artifactsDir: Path,
    val serverLog: Path,
    val evidenceLog: Path,
) {
    fun recordEvidence(evidence: LocalServerEvidence) {
        Files.createDirectories(evidenceLog.parent)
        Files.writeString(
            evidenceLog,
            localServerEvidenceJson.encodeToString(evidence) + "\n",
            CREATE,
            APPEND,
        )
    }

    fun recordEvidenceFromLogLine(line: String): Boolean {
        val evidence = line.toLocalServerEvidence() ?: return false
        recordEvidence(evidence)
        return true
    }

    fun collectEvidenceFromProcess(
        command: List<String>,
        timeoutMillis: Long = 10_000,
    ): LocalServerProcessResult {
        require(command.isNotEmpty()) { "server process command is required" }
        require(timeoutMillis > 0) { "server process timeout must be positive" }
        Files.createDirectories(serverLog.parent)
        val process = ProcessBuilder(command)
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start()
        val output = mutableListOf<String>()
        val outputReader = thread(name = "craftless-local-server-output") {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line -> output += line }
            }
        }

        val exited = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
        if (!exited) {
            process.destroyForcibly()
            outputReader.join(1_000)
            error("server process timed out after ${timeoutMillis}ms")
        }
        outputReader.join()

        val imported = persistProcessOutput(output)
        return LocalServerProcessResult(
            exitCode = process.exitValue(),
            evidenceCount = imported,
        )
    }

    fun collectMinecraftServerEvidence(
        serverJar: Path,
        javaExecutable: Path = Path.of(System.getProperty("java.home")).resolve("bin").resolve("java"),
        minHeap: String = "512M",
        maxHeap: String = "1G",
        timeoutMillis: Long = 60_000,
    ): LocalServerProcessResult {
        require(Files.exists(serverJar)) { "minecraft server jar does not exist: $serverJar" }
        require(Files.exists(javaExecutable)) { "java executable does not exist: $javaExecutable" }
        require(minHeap.isNotBlank()) { "minecraft server minimum heap is required" }
        require(maxHeap.isNotBlank()) { "minecraft server maximum heap is required" }
        val javaPath = javaExecutable.toAbsolutePath().normalize()
        val serverJarPath = serverJar.toAbsolutePath().normalize()
        Files.writeString(eulaFile, "eula=true\n", CREATE)
        return collectEvidenceFromProcess(
            command = listOf(
                javaPath.toString(),
                "-Xms$minHeap",
                "-Xmx$maxHeap",
                "-jar",
                serverJarPath.toString(),
                "nogui",
            ),
            timeoutMillis = timeoutMillis,
        )
    }

    fun startMinecraftServer(
        serverJar: Path,
        javaExecutable: Path = Path.of(System.getProperty("java.home")).resolve("bin").resolve("java"),
        minHeap: String = "512M",
        maxHeap: String = "1G",
        readinessTimeoutMillis: Long = 120_000,
        shutdownTimeoutMillis: Long = 10_000,
    ): LocalMinecraftServerHandle {
        require(Files.exists(serverJar)) { "minecraft server jar does not exist: $serverJar" }
        require(Files.exists(javaExecutable)) { "java executable does not exist: $javaExecutable" }
        require(minHeap.isNotBlank()) { "minecraft server minimum heap is required" }
        require(maxHeap.isNotBlank()) { "minecraft server maximum heap is required" }
        require(readinessTimeoutMillis > 0) { "minecraft server readiness timeout must be positive" }
        require(shutdownTimeoutMillis > 0) { "minecraft server shutdown timeout must be positive" }
        val javaPath = javaExecutable.toAbsolutePath().normalize()
        val serverJarPath = serverJar.toAbsolutePath().normalize()
        Files.writeString(eulaFile, "eula=true\n", CREATE)
        Files.createDirectories(serverLog.parent)

        val process = ProcessBuilder(
            javaPath.toString(),
            "-Xms$minHeap",
            "-Xmx$maxHeap",
            "-jar",
            serverJarPath.toString(),
            "nogui",
        )
            .directory(root.toFile())
            .redirectErrorStream(true)
            .start()
        val output = Collections.synchronizedList(mutableListOf<String>())
        val ready = CountDownLatch(1)
        val outputReader = thread(name = "craftless-minecraft-server-output") {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    output += line
                    if (line.isMinecraftServerReadyLine()) {
                        ready.countDown()
                    }
                }
            }
        }

        fun stopAndCollect(message: String): Nothing {
            process.destroyForcibly()
            outputReader.join(1_000)
            persistProcessOutput(output.snapshot())
            error(message)
        }

        if (!ready.await(readinessTimeoutMillis, TimeUnit.MILLISECONDS)) {
            stopAndCollect("minecraft server did not become ready after ${readinessTimeoutMillis}ms")
        }

        return LocalMinecraftServerHandle(
            layout = this,
            process = process,
            outputReader = outputReader,
            output = output,
            shutdownTimeoutMillis = shutdownTimeoutMillis,
        )
    }

    fun collectMinecraftServerStartupEvidence(
        serverJar: Path,
        javaExecutable: Path = Path.of(System.getProperty("java.home")).resolve("bin").resolve("java"),
        minHeap: String = "512M",
        maxHeap: String = "1G",
        readinessTimeoutMillis: Long = 120_000,
        shutdownTimeoutMillis: Long = 10_000,
    ): LocalServerProcessResult {
        val handle = startMinecraftServer(
            serverJar = serverJar,
            javaExecutable = javaExecutable,
            minHeap = minHeap,
            maxHeap = maxHeap,
            readinessTimeoutMillis = readinessTimeoutMillis,
            shutdownTimeoutMillis = shutdownTimeoutMillis,
        )
        return handle.stopAndCollect()
    }

    fun readEvidence(): List<LocalServerEvidence> {
        if (!Files.exists(evidenceLog)) {
            return emptyList()
        }
        return evidenceLog.readLines()
            .filter { it.isNotBlank() }
            .map { line -> localServerEvidenceJson.decodeFromString<LocalServerEvidence>(line) }
    }

    internal fun persistProcessOutput(output: List<String>): Int {
        Files.writeString(serverLog, output.joinToString("\n", postfix = "\n"), CREATE, APPEND)
        var imported = 0
        output.forEach { line ->
            if (recordEvidenceFromLogLine(line)) {
                imported += 1
            }
        }
        return imported
    }
}

class LocalMinecraftServerHandle internal constructor(
    private val layout: LocalServerLayout,
    private val process: Process,
    private val outputReader: Thread,
    private val output: MutableList<String>,
    private val shutdownTimeoutMillis: Long,
) {
    private var collected = false

    fun isRunning(): Boolean = process.isAlive

    fun stopAndCollect(): LocalServerProcessResult {
        check(!collected) { "minecraft server output has already been collected" }

        if (process.isAlive) {
            process.outputStream.bufferedWriter().use { writer ->
                writer.write("stop\n")
                writer.flush()
            }
            val exited = process.waitFor(shutdownTimeoutMillis, TimeUnit.MILLISECONDS)
            if (!exited) {
                process.destroyForcibly()
                outputReader.join(1_000)
                layout.persistProcessOutput(output.snapshot())
                collected = true
                error("minecraft server did not stop after ${shutdownTimeoutMillis}ms")
            }
        }

        outputReader.join()
        val imported = layout.persistProcessOutput(output.snapshot())
        collected = true
        return LocalServerProcessResult(
            exitCode = process.exitValue(),
            evidenceCount = imported,
        )
    }
}

data class LocalServerProcessResult(
    val exitCode: Int,
    val evidenceCount: Int,
)

@Serializable
data class LocalServerEvidence(
    val type: LocalServerEvidenceType,
    val player: String,
    val message: String? = null,
    val from: LocalServerPosition? = null,
    val to: LocalServerPosition? = null,
) {
    init {
        require(player.isNotBlank()) { "evidence player is required" }
    }

    companion object {
        fun playerJoined(player: String): LocalServerEvidence =
            LocalServerEvidence(type = LocalServerEvidenceType.PLAYER_JOINED, player = player)

        fun chat(player: String, message: String): LocalServerEvidence {
            require(message.isNotBlank()) { "chat evidence message is required" }
            return LocalServerEvidence(
                type = LocalServerEvidenceType.CHAT,
                player = player,
                message = message,
            )
        }

        fun movement(
            player: String,
            from: LocalServerPosition,
            to: LocalServerPosition,
        ): LocalServerEvidence =
            LocalServerEvidence(
                type = LocalServerEvidenceType.MOVEMENT,
                player = player,
                from = from,
                to = to,
            )

        fun playerDisconnected(player: String): LocalServerEvidence =
            LocalServerEvidence(type = LocalServerEvidenceType.PLAYER_DISCONNECTED, player = player)
    }
}

@Serializable
enum class LocalServerEvidenceType {
    PLAYER_JOINED,
    CHAT,
    MOVEMENT,
    PLAYER_DISCONNECTED,
}

@Serializable
data class LocalServerPosition(
    val x: Double,
    val y: Double,
    val z: Double,
)

private val localServerEvidenceJson = Json {
    encodeDefaults = false
    ignoreUnknownKeys = true
}

private fun String.toLocalServerEvidence(): LocalServerEvidence? {
    val message = substringAfter("]: ", missingDelimiterValue = this)
    joinedGameRegex.matchEntire(message)?.let { match ->
        return LocalServerEvidence.playerJoined(match.groupValues[1])
    }
    leftGameRegex.matchEntire(message)?.let { match ->
        return LocalServerEvidence.playerDisconnected(match.groupValues[1])
    }
    chatRegex.matchEntire(message)?.let { match ->
        return LocalServerEvidence.chat(match.groupValues[1], match.groupValues[2])
    }
    movementRegex.matchEntire(message)?.let { match ->
        return LocalServerEvidence.movement(
            player = match.groupValues[1],
            from = LocalServerPosition(
                x = match.groupValues[2].toDouble(),
                y = match.groupValues[3].toDouble(),
                z = match.groupValues[4].toDouble(),
            ),
            to = LocalServerPosition(
                x = match.groupValues[5].toDouble(),
                y = match.groupValues[6].toDouble(),
                z = match.groupValues[7].toDouble(),
            ),
        )
    }
    return null
}

private val playerNamePattern = "([A-Za-z0-9_]{1,16})"
private val coordinatePattern = "(-?\\d+(?:\\.\\d+)?)"
private val joinedGameRegex = Regex("$playerNamePattern joined the game")
private val leftGameRegex = Regex("$playerNamePattern left the game")
private val chatRegex = Regex("<$playerNamePattern> (.+)")
private val movementRegex = Regex(
    "\\[Craftless] $playerNamePattern moved from " +
        "$coordinatePattern $coordinatePattern $coordinatePattern to " +
        "$coordinatePattern $coordinatePattern $coordinatePattern"
)

private fun String.isMinecraftServerReadyLine(): Boolean =
    contains("Done (") && contains("For help, type")

private fun <T> MutableList<T>.snapshot(): List<T> =
    synchronized(this) { toList() }
