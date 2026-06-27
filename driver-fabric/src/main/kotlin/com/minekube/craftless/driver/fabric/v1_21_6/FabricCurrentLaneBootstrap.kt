package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.driver.fabric.FabricDriverBootstrap
import com.minekube.craftless.driver.fabric.runtime.FabricCompiledLaneMetadata

internal object FabricCurrentLaneBootstrap : FabricDriverBootstrap {
    override val providerId: String = FabricCompiledLaneMetadata.PROVIDER_ID
    override val minecraftVersion: String = FabricCompiledLaneMetadata.MINECRAFT_VERSION

    override fun initialize() {
        FabricEventCallbacks.register()
        val gateway = MinecraftFabricClientGateway()
        val backend = FabricDriverBackend.real(gateway)
        FabricDriverBackend.install(backend)
        FabricClientSmokeController.fromEnvironment().start(backend, gateway)
    }
}
