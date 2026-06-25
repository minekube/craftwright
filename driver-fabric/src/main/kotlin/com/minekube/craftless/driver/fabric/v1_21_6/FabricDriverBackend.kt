package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverActionArgument
import com.minekube.craftless.driver.api.DriverActionDescriptor
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionResult
import com.minekube.craftless.driver.api.DriverActionStatus
import com.minekube.craftless.driver.api.DriverEventType
import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.driver.api.booleanArgument
import com.minekube.craftless.driver.api.intArgument
import com.minekube.craftless.driver.api.requireChatMessage
import com.minekube.craftless.driver.api.stringArgument
import com.minekube.craftless.driver.runtime.DriverBackend
import com.minekube.craftless.driver.runtime.DriverBackendAction
import com.minekube.craftless.driver.runtime.DriverBackendResult

class FabricDriverBackend private constructor(
    private val mode: Mode,
    private val gateway: FabricClientGateway?,
) : DriverBackend {
    private val events = mutableListOf<String>()

    override fun connect(clientId: String, target: ConnectionTarget): DriverBackendResult {
        require(target.host.isNotBlank()) { "connection host is required" }
        require(target.port in 1..65535) { "connection port must be between 1 and 65535" }
        record("connect $clientId ${target.host}:${target.port}")
        gateway?.execute {
            gateway.connect(target)
        }
        return DriverBackendResult(DriverBackendAction.CONNECT, "fabric ${mode.id} connect requested")
    }

    private fun invokePlayerChatAction(clientId: String, message: String): String {
        requireChatMessage(message)
        record("chat $clientId $message")
        gateway?.execute {
            gateway.dispatchChatMessage(message)
        }
        return message
    }

    override fun actions(clientId: String): List<DriverActionDescriptor> =
        listOf(
            fabricPlayerMoveActionDescriptor(),
            fabricPlayerChatActionDescriptor(),
        )

    override fun runtimeMetadata(clientId: String): DriverRuntimeMetadata =
        DriverRuntimeMetadata(
            loaderVersion = "unknown",
            driver = "craftless-driver-fabric",
            driverVersion = "0.1.0-SNAPSHOT",
            mappings = "craftless-fabric-bindings",
            installedModsFingerprint = "fabric-driver",
            registryFingerprint = "unknown",
            serverFeatureFingerprint = "unknown",
            permissionsFingerprint = "local-client",
        )

    override fun invoke(clientId: String, invocation: DriverActionInvocation): DriverActionResult {
        require(invocation.action.isNotBlank()) { "action is required" }
        if (invocation.action == "player.chat") {
            val message = requireNotNull(invocation.arguments.stringArgument("message")) { "message is required" }
            return DriverActionResult(
                action = invocation.action,
                status = DriverActionStatus.ACCEPTED,
                message = invokePlayerChatAction(clientId, message),
                eventType = DriverEventType.CHAT,
            )
        }
        if (invocation.action != "player.move") {
            return DriverActionResult(
                action = invocation.action,
                status = DriverActionStatus.UNSUPPORTED,
                message = "unsupported Fabric action ${invocation.action}",
            )
        }
        val intent = FabricMovementIntent(
            forward = invocation.arguments.booleanArgument("forward"),
            backward = invocation.arguments.booleanArgument("backward"),
            left = invocation.arguments.booleanArgument("left"),
            right = invocation.arguments.booleanArgument("right"),
            jump = invocation.arguments.booleanArgument("jump"),
            sneak = invocation.arguments.booleanArgument("sneak"),
            sprint = invocation.arguments.booleanArgument("sprint"),
            ticks = invocation.arguments.intArgument("ticks") ?: 1,
        )
        require(intent.ticks > 0) { "movement ticks must be positive" }
        gateway?.execute {
            gateway.move(intent)
        }
        return DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.ACCEPTED,
            message = "fabric ${mode.id} action ${invocation.action} accepted",
            eventType = DriverEventType.MOVEMENT,
        )
    }

    override fun stop(clientId: String): DriverBackendResult {
        record("stop $clientId")
        gateway?.execute {
            gateway.stop()
        }
        return DriverBackendResult(DriverBackendAction.STOP, "fabric ${mode.id} stop requested")
    }

    fun events(): List<String> = events.toList()

    private fun record(event: String) {
        events += event
    }

    private enum class Mode(val id: String) {
        METADATA_ONLY("metadata-only"),
        REAL_CLIENT("real-client"),
    }

    companion object {
        @Volatile
        private var installed: FabricDriverBackend? = null

        fun metadataOnly(): FabricDriverBackend = FabricDriverBackend(Mode.METADATA_ONLY, gateway = null)

        fun real(gateway: FabricClientGateway = MinecraftFabricClientGateway()): FabricDriverBackend =
            FabricDriverBackend(Mode.REAL_CLIENT, gateway)

        fun install(backend: FabricDriverBackend) {
            installed = backend
        }

        fun current(): FabricDriverBackend =
            installed ?: metadataOnly().also(::install)
    }
}

private fun fabricPlayerMoveActionDescriptor(): DriverActionDescriptor =
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

private fun fabricPlayerChatActionDescriptor(): DriverActionDescriptor =
    DriverActionDescriptor(
        id = "player.chat",
        schemaVersion = "1",
        arguments = mapOf(
            "message" to DriverActionArgument("string", required = true),
        ),
    )
