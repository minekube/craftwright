package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.driver.fabric.runtime.FabricClientStateAccess
import com.minekube.craftless.driver.fabric.runtime.FabricEntityAccess
import com.minekube.craftless.driver.fabric.runtime.FabricEventAccess
import com.minekube.craftless.driver.fabric.runtime.FabricInteractionAccess
import com.minekube.craftless.driver.fabric.runtime.FabricInventoryAccess
import com.minekube.craftless.driver.fabric.runtime.FabricRegistryAccess
import com.minekube.craftless.driver.fabric.runtime.FabricRuntimeAccess
import com.minekube.craftless.driver.fabric.runtime.FabricRuntimeIdentity
import com.minekube.craftless.driver.fabric.runtime.FabricRuntimeProvider
import com.minekube.craftless.driver.fabric.runtime.FabricRuntimeSupport
import com.minekube.craftless.driver.fabric.runtime.FabricScreenAccess
import com.minekube.craftless.driver.fabric.runtime.FabricTargetingAccess
import com.minekube.craftless.driver.fabric.runtime.FabricWorldAccess

internal class FabricCurrentLaneRuntimeProvider : FabricRuntimeProvider {
    override val id: String = "fabric-current-lane"

    override fun support(identity: FabricRuntimeIdentity): FabricRuntimeSupport =
        if (identity.gameVersion == MINECRAFT_VERSION) {
            FabricRuntimeSupport.supported()
        } else {
            FabricRuntimeSupport.unsupported("unsupported-version")
        }

    override fun createAccess(identity: FabricRuntimeIdentity): FabricRuntimeAccess = FabricCurrentLaneRuntimeAccess(identity)

    private companion object {
        const val MINECRAFT_VERSION = "1.21.6"
    }
}

private class FabricCurrentLaneRuntimeAccess(
    override val identity: FabricRuntimeIdentity,
) : FabricRuntimeAccess {
    override val clientState: FabricClientStateAccess = object : FabricClientStateAccess {}
    override val registries: FabricRegistryAccess = object : FabricRegistryAccess {}
    override val targeting: FabricTargetingAccess = object : FabricTargetingAccess {}
    override val interaction: FabricInteractionAccess = object : FabricInteractionAccess {}
    override val inventory: FabricInventoryAccess = object : FabricInventoryAccess {}
    override val screen: FabricScreenAccess = object : FabricScreenAccess {}
    override val entities: FabricEntityAccess = object : FabricEntityAccess {}
    override val world: FabricWorldAccess = object : FabricWorldAccess {}
    override val events: FabricEventAccess = object : FabricEventAccess {}
}
