package com.minekube.craftless.driver.fabric

import com.minekube.craftless.driver.fabric.v1_21_6.FabricCurrentLaneBootstrap
import net.fabricmc.api.ClientModInitializer

class CraftlessFabricClientEntrypoint : ClientModInitializer {
    override fun onInitializeClient() {
        FabricCurrentLaneBootstrap.initialize()
    }
}
