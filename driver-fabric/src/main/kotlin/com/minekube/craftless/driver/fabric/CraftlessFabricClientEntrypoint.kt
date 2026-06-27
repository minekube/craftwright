package com.minekube.craftless.driver.fabric

import net.fabricmc.api.ClientModInitializer

class CraftlessFabricClientEntrypoint : ClientModInitializer {
    override fun onInitializeClient() {
        FabricBootstrapSelector.initializeCurrentCompiledLane()
    }
}
