package com.minekube.craftless.driver.fabric.v1_21_6

internal object FabricCurrentLaneBootstrap {
    fun initialize() {
        FabricEventCallbacks.register()
        val gateway = MinecraftFabricClientGateway()
        val backend = FabricDriverBackend.real(gateway)
        FabricDriverBackend.install(backend)
        FabricClientSmokeController.fromEnvironment().start(backend, gateway)
    }
}
