package com.minekube.craftwright.driver.fabric.v1_21_6

import net.fabricmc.api.ClientModInitializer

class CraftwrightFabricClientEntrypoint : ClientModInitializer {
    override fun onInitializeClient() {
        FabricDriverBackend.install(FabricDriverBackend.placeholder())
    }
}
