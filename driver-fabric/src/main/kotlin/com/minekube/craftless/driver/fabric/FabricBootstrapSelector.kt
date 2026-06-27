package com.minekube.craftless.driver.fabric

import com.minekube.craftless.driver.fabric.runtime.FabricCompiledLaneMetadata
import com.minekube.craftless.driver.fabric.v1_21_6.FabricCurrentLaneBootstrap

internal interface FabricDriverBootstrap {
    val providerId: String
    val minecraftVersion: String

    fun initialize()
}

internal object FabricBootstrapSelector {
    private val bootstraps: List<FabricDriverBootstrap> =
        listOf(
            FabricCurrentLaneBootstrap,
        )

    fun selectCurrentCompiledLane(): FabricDriverBootstrap =
        bootstraps.single { bootstrap ->
            bootstrap.providerId == FabricCompiledLaneMetadata.PROVIDER_ID &&
                bootstrap.minecraftVersion == FabricCompiledLaneMetadata.MINECRAFT_VERSION
        }

    fun initializeCurrentCompiledLane() {
        selectCurrentCompiledLane().initialize()
    }
}
