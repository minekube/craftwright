package com.minekube.craftless.driver.api

import com.minekube.craftless.protocol.ClientState
import com.minekube.craftless.protocol.isCraftlessActionArgumentType
import com.minekube.craftless.protocol.isCraftlessActionId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

interface DriverSession {
    val clientId: String

    fun snapshot(): DriverClientSnapshot

    fun connect(target: ConnectionTarget): DriverClientSnapshot

    fun actions(): List<DriverActionDescriptor>

    fun runtimeMetadata(): DriverRuntimeMetadata

    fun invoke(invocation: DriverActionInvocation): DriverActionResult

    fun stop(): DriverClientSnapshot

    fun events(): List<DriverEvent>
}

@Serializable
data class DriverClientSnapshot(
    val id: String,
    val state: ClientState,
)

@Serializable
data class ConnectionTarget(
    val host: String,
    val port: Int,
)

@Serializable
data class DriverActionDescriptor(
    val id: String,
    val schemaVersion: String,
    val arguments: Map<String, DriverActionArgument> = emptyMap(),
) {
    init {
        require(id.isNotBlank()) { "action id is required" }
        require(id.isCraftlessActionId()) { "invalid action id $id" }
        require(schemaVersion.isNotBlank()) { "action schema version is required" }
        require(arguments.keys.none { it.isBlank() }) { "action argument name is required" }
    }
}

@Serializable
data class DriverRuntimeMetadata(
    val loaderVersion: String = "none",
    val driver: String,
    val driverVersion: String = "0.1.0-SNAPSHOT",
    val mappings: String = "none",
    val installedModsFingerprint: String = "none",
    val registryFingerprint: String = "none",
    val serverFeatureFingerprint: String = "none",
    val permissionsFingerprint: String = "local-fake",
) {
    init {
        require(loaderVersion.isNotBlank()) { "loader version is required" }
        require(driver.isNotBlank()) { "driver is required" }
        require(driverVersion.isNotBlank()) { "driver version is required" }
        require(mappings.isNotBlank()) { "mappings fingerprint is required" }
        require(installedModsFingerprint.isNotBlank()) { "installed mods fingerprint is required" }
        require(registryFingerprint.isNotBlank()) { "registry fingerprint is required" }
        require(serverFeatureFingerprint.isNotBlank()) { "server feature fingerprint is required" }
        require(permissionsFingerprint.isNotBlank()) { "permissions fingerprint is required" }
    }

    companion object {
        fun fake(): DriverRuntimeMetadata =
            DriverRuntimeMetadata(driver = "craftless-fake")

        fun runtimeAdapter(): DriverRuntimeMetadata =
            DriverRuntimeMetadata(driver = "craftless-driver-runtime")
    }
}

@Serializable
data class DriverActionArgument(
    val type: String,
    val required: Boolean = false,
) {
    init {
        require(type.isCraftlessActionArgumentType()) { "unsupported action argument type $type" }
    }
}

@Serializable
data class DriverActionInvocation(
    val action: String,
    val arguments: Map<String, JsonElement> = emptyMap(),
)

fun Map<String, JsonElement>.booleanArgument(name: String): Boolean =
    this[name]?.jsonPrimitive?.let { primitive ->
        primitive.booleanOrNull ?: primitive.content.toBooleanStrictOrNull()
    } == true

fun Map<String, JsonElement>.intArgument(name: String): Int? =
    this[name]?.jsonPrimitive?.let { primitive ->
        primitive.intOrNull ?: primitive.content.toIntOrNull()
    }

fun Map<String, JsonElement>.stringArgument(name: String): String? =
    this[name]?.jsonPrimitive?.content

fun requireChatMessage(message: String): String {
    require(message.isNotBlank()) { "chat message is required" }
    require(!message.startsWith("/")) { "minecraft command strings are not valid chat action input" }
    return message
}

@Serializable
data class DriverActionResult(
    val action: String,
    val status: DriverActionStatus,
    val message: String? = null,
    val eventType: DriverEventType? = null,
)

@Serializable
enum class DriverActionStatus {
    ACCEPTED,
    UNSUPPORTED,
    FAILED,
}

@Serializable
data class DriverEvent(
    val type: DriverEventType,
    val client: String,
    val message: String? = null,
    val time: String = Instant.now().toString(),
)

@Serializable
enum class DriverEventType {
    CLIENT_CREATED,
    CLIENT_CONNECTED,
    CHAT,
    MOVEMENT,
    CLIENT_STOPPED,
    ERROR,
}

class FakeDriverSession(
    override val clientId: String,
) : DriverSession {
    private var state = ClientState.RUNNING
    private val events = mutableListOf(
        DriverEvent(
            type = DriverEventType.CLIENT_CREATED,
            client = clientId,
            message = "created client $clientId",
        )
    )

    override fun snapshot(): DriverClientSnapshot =
        DriverClientSnapshot(id = clientId, state = state)

    override fun connect(target: ConnectionTarget): DriverClientSnapshot {
        require(target.host.isNotBlank()) { "connection host is required" }
        require(target.port in 1..65535) { "connection port must be between 1 and 65535" }
        state = ClientState.CONNECTED
        events += DriverEvent(
            type = DriverEventType.CLIENT_CONNECTED,
            client = clientId,
            message = "connected $clientId to ${target.host}:${target.port}",
        )
        return snapshot()
    }

    private fun recordChat(message: String): DriverEvent {
        requireChatMessage(message)
        val event = DriverEvent(
            type = DriverEventType.CHAT,
            client = clientId,
            message = message,
        )
        events += event
        return event
    }

    private fun recordMovement(message: String): DriverEvent {
        val event = DriverEvent(
            type = DriverEventType.MOVEMENT,
            client = clientId,
            message = message,
        )
        events += event
        return event
    }

    override fun actions(): List<DriverActionDescriptor> =
        listOf(
            fakePlayerMoveActionDescriptor(),
            fakePlayerChatActionDescriptor(),
        )

    override fun runtimeMetadata(): DriverRuntimeMetadata =
        DriverRuntimeMetadata.fake()

    override fun invoke(invocation: DriverActionInvocation): DriverActionResult {
        require(invocation.action.isNotBlank()) { "action is required" }
        val result = when (invocation.action) {
            "player.chat" -> {
                val message = requireNotNull(invocation.arguments.stringArgument("message")) { "message is required" }
                val event = recordChat(message)
                DriverActionResult(
                    action = invocation.action,
                    status = DriverActionStatus.ACCEPTED,
                    message = event.message,
                    eventType = event.type,
                )
            }

            "player.move" -> {
                val ticks = invocation.arguments.intArgument("ticks") ?: 1
                require(ticks > 0) { "movement ticks must be positive" }
                val event = recordMovement("accepted ${invocation.action} for $clientId")
                DriverActionResult(
                    action = invocation.action,
                    status = DriverActionStatus.ACCEPTED,
                    message = event.message,
                    eventType = event.type,
                )
            }

            else -> DriverActionResult(
                action = invocation.action,
                status = DriverActionStatus.UNSUPPORTED,
                message = "unsupported fake action ${invocation.action}",
            )
        }
        if (result.status != DriverActionStatus.ACCEPTED && result.message != null) {
            events += DriverEvent(
                type = DriverEventType.ERROR,
                client = clientId,
                message = result.message,
            )
        }
        return result
    }

    override fun stop(): DriverClientSnapshot {
        state = ClientState.STOPPED
        events += DriverEvent(
            type = DriverEventType.CLIENT_STOPPED,
            client = clientId,
            message = "stopped client $clientId",
        )
        return snapshot()
    }

    override fun events(): List<DriverEvent> =
        events.toList()
}

private fun fakePlayerMoveActionDescriptor(): DriverActionDescriptor =
    DriverActionDescriptor(
        id = "player.move",
        schemaVersion = "1",
        arguments = mapOf(
            "forward" to DriverActionArgument("boolean"),
            "backward" to DriverActionArgument("boolean"),
            "left" to DriverActionArgument("boolean"),
            "right" to DriverActionArgument("boolean"),
            "jump" to DriverActionArgument("boolean"),
            "sneak" to DriverActionArgument("boolean"),
            "sprint" to DriverActionArgument("boolean"),
            "ticks" to DriverActionArgument("integer"),
        ),
    )

private fun fakePlayerChatActionDescriptor(): DriverActionDescriptor =
    DriverActionDescriptor(
        id = "player.chat",
        schemaVersion = "1",
        arguments = mapOf(
            "message" to DriverActionArgument("string", required = true),
        ),
    )
