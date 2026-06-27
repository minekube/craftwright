package com.minekube.craftless.driver.fabric

import com.minekube.craftless.driver.fabric.runtime.FabricCompiledLaneMetadata
import kotlin.test.Test
import kotlin.test.assertEquals

class FabricBootstrapSelectorTest {
    @Test
    fun `selector exposes current compiled lane bootstrap metadata without initialization`() {
        val bootstrap = FabricBootstrapSelector.selectCurrentCompiledLane()

        assertEquals(FabricCompiledLaneMetadata.PROVIDER_ID, bootstrap.providerId)
        assertEquals(FabricCompiledLaneMetadata.MINECRAFT_VERSION, bootstrap.minecraftVersion)
    }
}
