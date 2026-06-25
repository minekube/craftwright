package com.minekube.craftless.testkit

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
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
            logsDir = logsDir,
            artifactsDir = artifactsDir,
            evidenceLog = artifactsDir.resolve("server-evidence.jsonl"),
        )
    }
}

data class LocalServerLayout(
    val root: Path,
    val serverProperties: Path,
    val logsDir: Path,
    val artifactsDir: Path,
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

    fun readEvidence(): List<LocalServerEvidence> {
        if (!Files.exists(evidenceLog)) {
            return emptyList()
        }
        return evidenceLog.readLines()
            .filter { it.isNotBlank() }
            .map { line -> localServerEvidenceJson.decodeFromString<LocalServerEvidence>(line) }
    }
}

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
