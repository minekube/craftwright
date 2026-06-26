package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.protocol.RuntimeAvailability
import com.minekube.craftless.protocol.RuntimeEventNode
import com.minekube.craftless.protocol.RuntimeOperationNode
import com.minekube.craftless.protocol.RuntimeResourceNode
import com.minekube.craftless.protocol.RuntimeSchema
import com.minekube.craftless.protocol.RuntimeSourceEvidence
import java.security.MessageDigest

internal class FabricNavigationDiscovery(
    private val classExists: (String) -> Boolean = ::classExists,
) : FabricCapabilityProbe {
    override fun discover(context: FabricCapabilityProbeContext): FabricCapabilityGraphFragment {
        val detectedPathfinders = PATHFINDER_CLASS_CANDIDATES.filter(classExists)
        val navigationAvailability =
            if (detectedPathfinders.isEmpty()) {
                RuntimeAvailability.unavailable("pathfinder-unavailable")
            } else {
                RuntimeAvailability.available()
            }
        val operationAvailability =
            if (detectedPathfinders.isEmpty()) {
                RuntimeAvailability.unavailable("pathfinder-unavailable")
            } else {
                RuntimeAvailability.unavailable("adapter-unavailable")
            }
        val sourceEvidence =
            detectedPathfinders
                .takeIf { it.isNotEmpty() }
                ?.let {
                    listOf(
                        RuntimeSourceEvidence(
                            kind = "pathfinder",
                            fingerprint = it.privateFingerprint(),
                        ),
                    )
                }.orEmpty()

        return FabricCapabilityGraphFragment(
            resources =
                listOf(
                    RuntimeResourceNode(
                        id = "navigation",
                        availability = navigationAvailability,
                        sourceEvidence = sourceEvidence,
                    ),
                    RuntimeResourceNode(
                        id = "task",
                        availability = navigationAvailability,
                        sourceEvidence = sourceEvidence,
                    ),
                ),
            operations =
                listOf(
                    navigationOperation(
                        id = "navigation.plan",
                        arguments = mapOf("goal" to RuntimeSchema("object", required = true)),
                        availability = operationAvailability,
                    ),
                    navigationOperation(
                        id = "navigation.follow",
                        arguments = mapOf("plan" to RuntimeSchema("object", required = true)),
                        availability = operationAvailability,
                    ),
                    navigationOperation(
                        id = "navigation.stop",
                        availability = operationAvailability,
                    ),
                    taskOperation(
                        id = "task.run",
                        arguments = mapOf("request" to RuntimeSchema("object", required = true)),
                        availability = operationAvailability,
                    ),
                    taskOperation(
                        id = "task.status",
                        arguments = mapOf("task" to RuntimeSchema("string", required = true)),
                        availability = operationAvailability,
                    ),
                ),
            events =
                listOf(
                    RuntimeEventNode(
                        id = "task.progress",
                        resource = "task",
                        payload = RuntimeSchema.objectSchema(),
                        availability = operationAvailability,
                    ),
                ),
        )
    }
}

private fun navigationOperation(
    id: String,
    arguments: Map<String, RuntimeSchema> = emptyMap(),
    availability: RuntimeAvailability,
): RuntimeOperationNode =
    RuntimeOperationNode(
        id = id,
        resource = "navigation",
        adapter = "navigation.default",
        arguments = arguments,
        result = RuntimeSchema.objectSchema(),
        availability = availability,
    )

private fun taskOperation(
    id: String,
    arguments: Map<String, RuntimeSchema> = emptyMap(),
    availability: RuntimeAvailability,
): RuntimeOperationNode =
    RuntimeOperationNode(
        id = id,
        resource = "task",
        adapter = "task.executor",
        arguments = arguments,
        result = RuntimeSchema.objectSchema(),
        availability = availability,
    )

private fun classExists(className: String): Boolean =
    runCatching {
        Class.forName(className, false, FabricNavigationDiscovery::class.java.classLoader)
    }.isSuccess

private fun List<String>.privateFingerprint(): String {
    val canonical = sorted().joinToString("\n")
    val digest =
        MessageDigest
            .getInstance("SHA-256")
            .digest(canonical.encodeToByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
            .take(16)
    return "classes:$digest"
}

private val PATHFINDER_CLASS_CANDIDATES =
    listOf(
        "baritone.api.BaritoneAPI",
        "net.swarmbot.SwarmBot",
    )
