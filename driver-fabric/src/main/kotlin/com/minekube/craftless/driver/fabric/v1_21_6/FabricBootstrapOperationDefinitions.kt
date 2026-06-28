package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.protocol.RuntimeAvailability
import com.minekube.craftless.protocol.RuntimeEventNode
import com.minekube.craftless.protocol.RuntimeOperationNode
import com.minekube.craftless.protocol.RuntimeSchema

internal enum class FabricBootstrapOperationAvailabilityKind {
    PLAYER,
    CAMERA,
    INVENTORY,
    RECIPE_QUERY,
    RECIPE_CRAFT,
    ENTITY,
    ENTITY_ATTACK,
    BLOCK_QUERY,
    WORLD,
    BLOCK_BREAK,
    BLOCK_INTERACT,
    SCREEN,
    SCREEN_CLOSE,
}

internal data class FabricBootstrapOperationDefinition(
    val id: String,
    val availability: FabricBootstrapOperationAvailabilityKind,
    val arguments: Map<String, RuntimeSchema> = emptyMap(),
    val result: RuntimeSchema = RuntimeSchema.objectSchema(),
)

internal object FabricBootstrapOperationIds {
    const val PLAYER_QUERY = "player.query"
    const val PLAYER_CHAT = "player.chat"
    const val PLAYER_LOOK = "player.look"
    const val PLAYER_MOVE = "player.move"
    const val PLAYER_RAYCAST = "player.raycast"
    const val INVENTORY_QUERY = "inventory.query"
    const val INVENTORY_EQUIP = "inventory.equip"
    const val RECIPE_QUERY = "recipe.query"
    const val RECIPE_CRAFT = "recipe.craft"
    const val ENTITY_QUERY = "entity.query"
    const val ENTITY_ATTACK = "entity.attack"
    const val WORLD_BLOCK_QUERY = "world.block.query"
    const val WORLD_TIME_QUERY = "world.time.query"
    const val WORLD_BLOCK_BREAK = "world.block.break"
    const val WORLD_BLOCK_INTERACT = "world.block.interact"
    const val SCREEN_QUERY = "screen.query"
    const val SCREEN_CLOSE = "screen.close"
}

internal object FabricBootstrapOperationAdapters {
    const val PLAYER_QUERY = "fabric.player-query"
    const val PLAYER_CHAT = "fabric.player-chat"
    const val PLAYER_LOOK = "fabric.player-look"
    const val PLAYER_MOVE = "fabric.player-move"
    const val PLAYER_RAYCAST = "fabric.player-raycast"
    const val INVENTORY_QUERY = "fabric.inventory-query"
    const val INVENTORY_EQUIP = "fabric.inventory-equip"
    const val RECIPE_QUERY = "fabric.recipe-query"
    const val RECIPE_CRAFT = "fabric.recipe-craft"
    const val ENTITY_QUERY = "fabric.entity-query"
    const val ENTITY_ATTACK = "fabric.entity-attack"
    const val WORLD_BLOCK_QUERY = "fabric.world-block-query"
    const val WORLD_TIME_QUERY = "fabric.world-time-query"
    const val WORLD_BLOCK_BREAK = "fabric.world-block-break"
    const val WORLD_BLOCK_INTERACT = "fabric.world-block-interact"
    const val SCREEN_QUERY = "fabric.screen-query"
    const val SCREEN_CLOSE = "fabric.screen-close"
}

private val bootstrapOperationAdapterKeysById =
    mapOf(
        FabricBootstrapOperationIds.PLAYER_QUERY to FabricBootstrapOperationAdapters.PLAYER_QUERY,
        FabricBootstrapOperationIds.PLAYER_CHAT to FabricBootstrapOperationAdapters.PLAYER_CHAT,
        FabricBootstrapOperationIds.PLAYER_LOOK to FabricBootstrapOperationAdapters.PLAYER_LOOK,
        FabricBootstrapOperationIds.PLAYER_MOVE to FabricBootstrapOperationAdapters.PLAYER_MOVE,
        FabricBootstrapOperationIds.PLAYER_RAYCAST to FabricBootstrapOperationAdapters.PLAYER_RAYCAST,
        FabricBootstrapOperationIds.INVENTORY_QUERY to FabricBootstrapOperationAdapters.INVENTORY_QUERY,
        FabricBootstrapOperationIds.INVENTORY_EQUIP to FabricBootstrapOperationAdapters.INVENTORY_EQUIP,
        FabricBootstrapOperationIds.RECIPE_QUERY to FabricBootstrapOperationAdapters.RECIPE_QUERY,
        FabricBootstrapOperationIds.RECIPE_CRAFT to FabricBootstrapOperationAdapters.RECIPE_CRAFT,
        FabricBootstrapOperationIds.ENTITY_QUERY to FabricBootstrapOperationAdapters.ENTITY_QUERY,
        FabricBootstrapOperationIds.ENTITY_ATTACK to FabricBootstrapOperationAdapters.ENTITY_ATTACK,
        FabricBootstrapOperationIds.WORLD_BLOCK_QUERY to FabricBootstrapOperationAdapters.WORLD_BLOCK_QUERY,
        FabricBootstrapOperationIds.WORLD_TIME_QUERY to FabricBootstrapOperationAdapters.WORLD_TIME_QUERY,
        FabricBootstrapOperationIds.WORLD_BLOCK_BREAK to FabricBootstrapOperationAdapters.WORLD_BLOCK_BREAK,
        FabricBootstrapOperationIds.WORLD_BLOCK_INTERACT to FabricBootstrapOperationAdapters.WORLD_BLOCK_INTERACT,
        FabricBootstrapOperationIds.SCREEN_QUERY to FabricBootstrapOperationAdapters.SCREEN_QUERY,
        FabricBootstrapOperationIds.SCREEN_CLOSE to FabricBootstrapOperationAdapters.SCREEN_CLOSE,
    )

internal fun fabricBootstrapOperationAdapterKeysById(): Map<String, String> = bootstrapOperationAdapterKeysById

internal fun fabricBootstrapOperationAdapterKey(operationId: String): String? = bootstrapOperationAdapterKeysById[operationId]

internal fun fabricBootstrapOperationDefinitions(): List<FabricBootstrapOperationDefinition> =
    listOf(
        FabricBootstrapOperationDefinition(
            id = FabricBootstrapOperationIds.PLAYER_QUERY,
            availability = FabricBootstrapOperationAvailabilityKind.PLAYER,
            result = actionEnvelopeResultSchema(data = RuntimeSchema.objectSchema()),
        ),
        FabricBootstrapOperationDefinition(
            id = FabricBootstrapOperationIds.PLAYER_CHAT,
            availability = FabricBootstrapOperationAvailabilityKind.PLAYER,
            arguments = mapOf("message" to RuntimeSchema("string", required = true)),
        ),
        FabricBootstrapOperationDefinition(
            id = FabricBootstrapOperationIds.PLAYER_LOOK,
            availability = FabricBootstrapOperationAvailabilityKind.PLAYER,
            arguments =
                mapOf(
                    "yaw" to RuntimeSchema("number", required = true),
                    "pitch" to RuntimeSchema("number", required = true),
                ),
        ),
        FabricBootstrapOperationDefinition(
            id = FabricBootstrapOperationIds.PLAYER_MOVE,
            availability = FabricBootstrapOperationAvailabilityKind.PLAYER,
            arguments =
                mapOf(
                    "forward" to RuntimeSchema("boolean"),
                    "backward" to RuntimeSchema("boolean"),
                    "left" to RuntimeSchema("boolean"),
                    "right" to RuntimeSchema("boolean"),
                    "jump" to RuntimeSchema("boolean"),
                    "sneak" to RuntimeSchema("boolean"),
                    "sprint" to RuntimeSchema("boolean"),
                    "ticks" to RuntimeSchema("integer"),
                ),
            result = actionEnvelopeResultSchema(data = RuntimeSchema.objectSchema()),
        ),
        FabricBootstrapOperationDefinition(
            id = FabricBootstrapOperationIds.PLAYER_RAYCAST,
            availability = FabricBootstrapOperationAvailabilityKind.CAMERA,
            arguments = raycastArgumentsSchema(),
            result = actionEnvelopeResultSchema(data = RuntimeSchema.objectSchema()),
        ),
        FabricBootstrapOperationDefinition(
            id = FabricBootstrapOperationIds.INVENTORY_QUERY,
            availability = FabricBootstrapOperationAvailabilityKind.INVENTORY,
            result = actionEnvelopeResultSchema(data = RuntimeSchema.objectSchema()),
        ),
        FabricBootstrapOperationDefinition(
            id = FabricBootstrapOperationIds.INVENTORY_EQUIP,
            availability = FabricBootstrapOperationAvailabilityKind.INVENTORY,
            arguments = mapOf("slot" to RuntimeSchema("integer", required = true)),
        ),
        FabricBootstrapOperationDefinition(
            id = FabricBootstrapOperationIds.RECIPE_QUERY,
            availability = FabricBootstrapOperationAvailabilityKind.RECIPE_QUERY,
            arguments =
                mapOf(
                    "category" to RuntimeSchema("string"),
                    "output" to RuntimeSchema("string"),
                    "craftable" to RuntimeSchema("boolean"),
                    "limit" to RuntimeSchema("integer"),
                ),
            result = recipeQueryResultSchema(),
        ),
        FabricBootstrapOperationDefinition(
            id = FabricBootstrapOperationIds.RECIPE_CRAFT,
            availability = FabricBootstrapOperationAvailabilityKind.RECIPE_CRAFT,
            arguments =
                mapOf(
                    "target" to recipeCraftTargetSchema(),
                    "count" to RuntimeSchema("integer"),
                ),
            result = recipeCraftResultSchema(),
        ),
        FabricBootstrapOperationDefinition(
            id = FabricBootstrapOperationIds.ENTITY_QUERY,
            availability = FabricBootstrapOperationAvailabilityKind.ENTITY,
            arguments =
                mapOf(
                    "radius" to RuntimeSchema("number"),
                    "limit" to RuntimeSchema("integer"),
                ),
            result = entityQueryResultSchema(),
        ),
        FabricBootstrapOperationDefinition(
            id = FabricBootstrapOperationIds.ENTITY_ATTACK,
            availability = FabricBootstrapOperationAvailabilityKind.ENTITY_ATTACK,
            arguments =
                mapOf(
                    "target" to RuntimeSchema("object", required = true),
                    "max-distance" to RuntimeSchema("number"),
                ),
            result = entityAttackResultSchema(),
        ),
        FabricBootstrapOperationDefinition(
            id = FabricBootstrapOperationIds.WORLD_BLOCK_QUERY,
            availability = FabricBootstrapOperationAvailabilityKind.BLOCK_QUERY,
            arguments =
                mapOf(
                    "radius" to RuntimeSchema("number"),
                    "limit" to RuntimeSchema("integer"),
                    "category" to RuntimeSchema("string"),
                    "target" to RuntimeSchema("object"),
                ),
            result = blockQueryResultSchema(),
        ),
        FabricBootstrapOperationDefinition(
            id = FabricBootstrapOperationIds.WORLD_TIME_QUERY,
            availability = FabricBootstrapOperationAvailabilityKind.WORLD,
            result = actionEnvelopeResultSchema(data = RuntimeSchema.objectSchema()),
        ),
        FabricBootstrapOperationDefinition(
            id = FabricBootstrapOperationIds.WORLD_BLOCK_BREAK,
            availability = FabricBootstrapOperationAvailabilityKind.BLOCK_BREAK,
            arguments = blockTargetArgumentsSchema() + mapOf("ticks" to RuntimeSchema("integer")),
            result = actionEnvelopeResultSchema(data = RuntimeSchema.objectSchema()),
        ),
        FabricBootstrapOperationDefinition(
            id = FabricBootstrapOperationIds.WORLD_BLOCK_INTERACT,
            availability = FabricBootstrapOperationAvailabilityKind.BLOCK_INTERACT,
            arguments = blockTargetArgumentsSchema() + mapOf("side" to RuntimeSchema("string")),
            result = actionEnvelopeResultSchema(data = RuntimeSchema.objectSchema()),
        ),
        FabricBootstrapOperationDefinition(
            id = FabricBootstrapOperationIds.SCREEN_QUERY,
            availability = FabricBootstrapOperationAvailabilityKind.SCREEN,
            result = actionEnvelopeResultSchema(data = RuntimeSchema.objectSchema()),
        ),
        FabricBootstrapOperationDefinition(
            id = FabricBootstrapOperationIds.SCREEN_CLOSE,
            availability = FabricBootstrapOperationAvailabilityKind.SCREEN_CLOSE,
        ),
    )

internal fun FabricBootstrapOperationDefinition.toRuntimeOperation(availability: RuntimeAvailability): RuntimeOperationNode =
    RuntimeOperationNode(
        id = id,
        resource = id.substringBeforeLast("."),
        adapter =
            requireNotNull(fabricBootstrapOperationAdapterKey(id)) {
                "missing bootstrap operation adapter for $id"
            },
        arguments = arguments,
        result = result,
        availability = availability,
    )

internal fun RuntimeOperationNode.toFabricEventNode(): RuntimeEventNode =
    RuntimeEventNode(
        id = id,
        resource = resource,
        payload = RuntimeSchema.objectSchema(),
        availability = availability,
        sourceEvidence = sourceEvidence,
    )

private fun raycastArgumentsSchema(): Map<String, RuntimeSchema> =
    mapOf(
        "max-distance" to RuntimeSchema("number"),
        "include-fluids" to RuntimeSchema("boolean"),
    )

private fun blockTargetArgumentsSchema(): Map<String, RuntimeSchema> =
    raycastArgumentsSchema() +
        mapOf(
            "target" to RuntimeSchema("object"),
        )

private fun actionEnvelopeResultSchema(data: RuntimeSchema? = null): RuntimeSchema {
    val properties =
        mutableMapOf(
            "action" to RuntimeSchema("string", required = true),
            "status" to RuntimeSchema("string", required = true),
            "message" to RuntimeSchema("string"),
        )
    if (data != null) {
        properties["data"] = data
    }
    return RuntimeSchema(
        type = "object",
        properties = properties,
    )
}

private fun recipeQueryResultSchema(): RuntimeSchema =
    RuntimeSchema(
        type = "object",
        properties =
            mapOf(
                "count" to RuntimeSchema("integer"),
                "recipes" to RuntimeSchema("array", items = recipeRecordSchema()),
                "reason" to RuntimeSchema("string"),
            ),
    )

private fun entityQueryResultSchema(): RuntimeSchema =
    RuntimeSchema(
        type = "object",
        properties =
            mapOf(
                "origin" to RuntimeSchema.objectSchema(),
                "radius" to RuntimeSchema("number"),
                "count" to RuntimeSchema("integer"),
                "entities" to RuntimeSchema("array", items = RuntimeSchema.objectSchema()),
                "reason" to RuntimeSchema("string"),
            ),
    )

private fun blockQueryResultSchema(): RuntimeSchema =
    RuntimeSchema(
        type = "object",
        properties =
            mapOf(
                "origin" to RuntimeSchema.objectSchema(),
                "radius" to RuntimeSchema("number"),
                "count" to RuntimeSchema("integer"),
                "blocks" to RuntimeSchema("array", items = RuntimeSchema.objectSchema()),
                "reason" to RuntimeSchema("string"),
            ),
    )

private fun entityAttackResultSchema(): RuntimeSchema =
    RuntimeSchema(
        type = "object",
        properties =
            mapOf(
                "handle" to RuntimeSchema("string"),
                "label" to RuntimeSchema("string"),
                "category" to RuntimeSchema("string"),
                "distance" to RuntimeSchema("number"),
                "position" to RuntimeSchema.objectSchema(),
                "hit" to RuntimeSchema("boolean"),
                "alive" to RuntimeSchema("boolean"),
                "origin" to RuntimeSchema.objectSchema(),
                "reason" to RuntimeSchema("string"),
            ),
    )

private fun recipeRecordSchema(): RuntimeSchema =
    RuntimeSchema(
        type = "object",
        properties =
            mapOf(
                "handle" to RuntimeSchema("string"),
                "kind" to RuntimeSchema("string"),
                "craftable" to RuntimeSchema("boolean"),
                "outputs" to RuntimeSchema("array", items = recipeItemSchema()),
                "ingredients" to RuntimeSchema("array", items = recipeItemSchema()),
                "produces" to RuntimeSchema("array", items = recipeItemSchema()),
                "requires" to RuntimeSchema("array", items = recipeItemSchema()),
                "station" to recipeItemSchema(),
                "reason" to RuntimeSchema("string"),
            ),
    )

private fun recipeItemSchema(): RuntimeSchema =
    RuntimeSchema(
        type = "object",
        properties =
            mapOf(
                "label" to RuntimeSchema("string"),
                "count" to RuntimeSchema("integer"),
                "category" to RuntimeSchema("string"),
            ),
    )

private fun recipeCraftTargetSchema(): RuntimeSchema =
    RuntimeSchema(
        type = "object",
        required = true,
        properties =
            mapOf(
                "handle" to RuntimeSchema("string"),
            ),
    )

private fun recipeCraftResultSchema(): RuntimeSchema =
    RuntimeSchema(
        type = "object",
        properties =
            mapOf(
                "handle" to RuntimeSchema("string"),
                "accepted" to RuntimeSchema("boolean"),
                "changed" to RuntimeSchema("boolean"),
                "requested-count" to RuntimeSchema("integer"),
                "crafted-count" to RuntimeSchema("integer"),
                "inventory-before" to RuntimeSchema("string"),
                "inventory-after" to RuntimeSchema("string"),
                "sync-id" to RuntimeSchema("integer"),
                "output-slot" to RuntimeSchema("integer"),
                "attempt" to RuntimeSchema("integer"),
                "confirmation-attempt" to RuntimeSchema("integer"),
                "phase" to RuntimeSchema("string"),
                "reason" to RuntimeSchema("string"),
                "expected-output" to recipeItemSchema(),
                "actual-output" to recipeItemSchema(),
            ),
    )
