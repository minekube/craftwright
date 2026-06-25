package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverActionDescriptor
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionResult
import com.minekube.craftless.driver.api.DriverActionStatus
import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.driver.runtime.DriverBackend
import com.minekube.craftless.driver.runtime.DriverBackendAction
import com.minekube.craftless.driver.runtime.DriverBackendResult

class FabricDriverBackend private constructor(
    private val mode: Mode,
    private val gateway: FabricClientGateway?,
    actionBindings: List<FabricActionBinding> = defaultFabricActionBindings(),
) : DriverBackend {
    private val events = mutableListOf<String>()
    private val actionBindingsById = actionBindings.associateBy { it.descriptor.id }

    override fun connect(clientId: String, target: ConnectionTarget): DriverBackendResult {
        require(target.host.isNotBlank()) { "connection host is required" }
        require(target.port in 1..65535) { "connection port must be between 1 and 65535" }
        record("connect $clientId ${target.host}:${target.port}")
        gateway?.execute {
            gateway.connect(target)
        }
        return DriverBackendResult(DriverBackendAction.CONNECT, "fabric ${mode.id} connect requested")
    }

    override fun actions(clientId: String): List<DriverActionDescriptor> =
        actionBindingsById.values.map { it.descriptor }

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
        val binding = actionBindingsById[invocation.action]
        if (binding == null) {
            return DriverActionResult(
                action = invocation.action,
                status = DriverActionStatus.UNSUPPORTED,
                message = "unsupported Fabric action ${invocation.action}",
            )
        }
        return binding.invoke(
            clientId = clientId,
            invocation = invocation,
            context = FabricActionContext(
                modeId = mode.id,
                gateway = gateway,
                record = ::record,
            ),
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
