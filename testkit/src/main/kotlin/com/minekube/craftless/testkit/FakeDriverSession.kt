package com.minekube.craftless.testkit

import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionResult
import com.minekube.craftless.driver.api.DriverActionStatus
import com.minekube.craftless.driver.api.DriverClientSnapshot
import com.minekube.craftless.driver.api.DriverEvent
import com.minekube.craftless.driver.api.DriverEventType
import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.driver.api.DriverSession
import com.minekube.craftless.driver.api.intArgument
import com.minekube.craftless.driver.api.stringArgument
import com.minekube.craftless.protocol.ClientState
import com.minekube.craftless.protocol.RuntimeAvailability
import com.minekube.craftless.protocol.RuntimeCapabilityGraph
import com.minekube.craftless.protocol.RuntimeOperationNode
import com.minekube.craftless.protocol.RuntimeResourceNode
import com.minekube.craftless.protocol.RuntimeSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class FakeDriverSession(
    override val clientId: String,
) : DriverSession {
    private var state = ClientState.RUNNING
    private val events =
        mutableListOf(
            DriverEvent(
                type = DriverEventType.CLIENT_CREATED,
                client = clientId,
                message = "created client $clientId",
            ),
        )

    override fun snapshot(): DriverClientSnapshot = DriverClientSnapshot(id = clientId, state = state)

    override fun connect(target: ConnectionTarget): DriverClientSnapshot {
        require(target.host.isNotBlank()) { "connection host is required" }
        require(target.port in 1..65535) { "connection port must be between 1 and 65535" }
        state = ClientState.CONNECTED
        events +=
            DriverEvent(
                type = DriverEventType.CLIENT_CONNECTED,
                client = clientId,
                message = "connected $clientId to ${target.host}:${target.port}",
            )
        return snapshot()
    }

    override fun runtimeMetadata(): DriverRuntimeMetadata = fakeDriverRuntimeMetadata()

    override fun runtimeGraph(): RuntimeCapabilityGraph =
        RuntimeCapabilityGraph(
            clientId = clientId,
            resources =
                listOf(
                    RuntimeResourceNode("player", RuntimeAvailability.available()),
                    RuntimeResourceNode("media.screenshot", RuntimeAvailability.available()),
                ),
            operations =
                listOf(
                    RuntimeOperationNode(
                        id = "player.chat",
                        resource = "player",
                        adapter = "fake.chat",
                        arguments = mapOf("message" to RuntimeSchema("string", required = true)),
                        availability = RuntimeAvailability.available(),
                    ),
                    RuntimeOperationNode(
                        id = "player.move",
                        resource = "player",
                        adapter = "fake.move",
                        arguments =
                            mapOf(
                                "forward" to RuntimeSchema("boolean"),
                                "backward" to RuntimeSchema("boolean"),
                                "left" to RuntimeSchema("boolean"),
                                "right" to RuntimeSchema("boolean"),
                                "jump" to RuntimeSchema("boolean"),
                                "sneak" to RuntimeSchema("boolean"),
                                "sprint" to RuntimeSchema("boolean"),
                                "ticks" to RuntimeSchema("integer"),
                            ),
                        availability = RuntimeAvailability.available(),
                    ),
                    RuntimeOperationNode(
                        id = "media.screenshot.capture",
                        resource = "media.screenshot",
                        adapter = "media.screenshot",
                        result = screenshotResultSchema(),
                        availability = RuntimeAvailability.available(),
                    ),
                ),
        )

    override fun invoke(invocation: DriverActionInvocation): DriverActionResult {
        require(invocation.action.isNotBlank()) { "action is required" }
        val result =
            when (invocation.action) {
                "player.chat" -> {
                    val message =
                        invocation.arguments.stringArgument("message")
                            ?: return chatInputFailure(invocation.action, "missing-message")
                    if (message.isBlank()) {
                        return chatInputFailure(invocation.action, "blank-message")
                    }
                    if (message.startsWith("/")) {
                        return chatInputFailure(invocation.action, "minecraft-command-rejected")
                    }
                    DriverActionResult(
                        action = invocation.action,
                        status = DriverActionStatus.ACCEPTED,
                        message = message,
                    )
                }

                "player.move" -> {
                    val ticks = invocation.arguments.intArgument("ticks") ?: 1
                    if (ticks <= 0) {
                        return actionFailure(invocation.action, "invalid-ticks", "moved")
                    }
                    val message = "accepted ${invocation.action} for $clientId"
                    DriverActionResult(
                        action = invocation.action,
                        status = DriverActionStatus.ACCEPTED,
                        message = message,
                    )
                }

                "media.screenshot.capture" ->
                    DriverActionResult(
                        action = invocation.action,
                        status = DriverActionStatus.ACCEPTED,
                        message = "captured screenshot for $clientId",
                        data =
                            buildJsonObject {
                                put("artifact-id", "screenshot-0001.png")
                                put("media-type", "image/png")
                                put("byte-size", 68)
                                put("sha256", FAKE_SCREENSHOT_SHA256)
                                put("width", 1)
                                put("height", 1)
                                put("created-at", FAKE_SCREENSHOT_CREATED_AT)
                                put("download-url", "/clients/$clientId/artifacts/screenshot-0001.png")
                            },
                    )

                else ->
                    DriverActionResult(
                        action = invocation.action,
                        status = DriverActionStatus.UNSUPPORTED,
                        message = "unsupported fake action ${invocation.action}",
                    )
            }
        if (result.status != DriverActionStatus.ACCEPTED && result.message != null) {
            events +=
                DriverEvent(
                    type = DriverEventType.ERROR,
                    client = clientId,
                    message = result.message,
                )
        }
        return result
    }

    override fun stop(): DriverClientSnapshot {
        state = ClientState.STOPPED
        events +=
            DriverEvent(
                type = DriverEventType.CLIENT_STOPPED,
                client = clientId,
                message = "stopped client $clientId",
            )
        return snapshot()
    }

    override fun events(): List<DriverEvent> = events.toList()
}

private fun chatInputFailure(
    action: String,
    reason: String,
): DriverActionResult = actionFailure(action, reason, "sent")

private fun actionFailure(
    action: String,
    reason: String,
    flag: String,
): DriverActionResult =
    DriverActionResult(
        action = action,
        status = DriverActionStatus.FAILED,
        message = reason,
        data =
            buildJsonObject {
                put(flag, false)
                put("reason", reason)
            },
    )

private fun screenshotResultSchema(): RuntimeSchema =
    RuntimeSchema(
        type = "object",
        required = true,
        properties =
            mapOf(
                "artifact-id" to RuntimeSchema("string", required = true),
                "media-type" to RuntimeSchema("string", required = true),
                "byte-size" to RuntimeSchema("integer", required = true),
                "sha256" to RuntimeSchema("string", required = true),
                "width" to RuntimeSchema("integer", required = true),
                "height" to RuntimeSchema("integer", required = true),
                "created-at" to RuntimeSchema("string", required = true),
                "download-url" to RuntimeSchema("string", required = true),
            ),
    )

fun fakeDriverRuntimeMetadata(): DriverRuntimeMetadata =
    DriverRuntimeMetadata(
        driver = "craftless-fake",
        permissionsFingerprint = "local-fake",
    )

private const val FAKE_SCREENSHOT_CREATED_AT = "2026-06-29T00:00:00Z"
private const val FAKE_SCREENSHOT_SHA256 = "f446dc0d7abccbd4640a14c741a7502983481cb26ad6a3797db49073b3d0fad1"
