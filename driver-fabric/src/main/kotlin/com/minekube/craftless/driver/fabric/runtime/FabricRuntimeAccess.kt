package com.minekube.craftless.driver.fabric.runtime

internal interface FabricRuntimeAccess {
    val identity: FabricRuntimeIdentity
    val clientState: FabricClientStateAccess
    val registries: FabricRegistryAccess
    val targeting: FabricTargetingAccess
    val interaction: FabricInteractionAccess
    val inventory: FabricInventoryAccess
    val screen: FabricScreenAccess
    val entities: FabricEntityAccess
    val world: FabricWorldAccess
    val events: FabricEventAccess
}

internal interface FabricClientStateAccess

internal interface FabricRegistryAccess

internal interface FabricTargetingAccess

internal interface FabricInteractionAccess

internal interface FabricInventoryAccess

internal interface FabricScreenAccess

internal interface FabricEntityAccess

internal interface FabricWorldAccess

internal interface FabricEventAccess
