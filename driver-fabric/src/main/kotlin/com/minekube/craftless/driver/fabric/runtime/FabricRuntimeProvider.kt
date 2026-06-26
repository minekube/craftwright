package com.minekube.craftless.driver.fabric.runtime

internal interface FabricRuntimeProvider {
    val id: String

    fun support(identity: FabricRuntimeIdentity): FabricRuntimeSupport

    fun createAccess(identity: FabricRuntimeIdentity): FabricRuntimeAccess
}

internal data class FabricRuntimeSupport(
    val state: FabricRuntimeSupportState,
    val reason: String,
) {
    init {
        require(reason.matches(machineCode)) { "runtime support reason must be a machine-readable Craftless code" }
    }

    companion object {
        fun supported(reason: String = "supported"): FabricRuntimeSupport =
            FabricRuntimeSupport(FabricRuntimeSupportState.SUPPORTED, reason)

        fun unsupported(reason: String): FabricRuntimeSupport = FabricRuntimeSupport(FabricRuntimeSupportState.UNSUPPORTED, reason)
    }
}

internal enum class FabricRuntimeSupportState {
    SUPPORTED,
    UNSUPPORTED,
}

internal data class FabricRuntimeProviderSelection(
    val provider: FabricRuntimeProvider,
    val support: FabricRuntimeSupport,
    val access: FabricRuntimeAccess,
)

internal fun selectFabricRuntimeProvider(
    identity: FabricRuntimeIdentity,
    providers: List<FabricRuntimeProvider>,
): FabricRuntimeProviderSelection {
    val evaluated = providers.map { provider -> provider to provider.support(identity) }
    val selected =
        evaluated.firstOrNull { (_, support) -> support.state == FabricRuntimeSupportState.SUPPORTED }
            ?: throw IllegalArgumentException(
                "no Fabric runtime provider supports Minecraft ${identity.gameVersion}: " +
                    evaluated.joinToString(",") { (provider, support) -> "${provider.id}:${support.reason}" },
            )
    val provider = selected.first
    val support = selected.second
    return FabricRuntimeProviderSelection(
        provider = provider,
        support = support,
        access = provider.createAccess(identity),
    )
}

private val machineCode = Regex("[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)*")
