package com.minekube.craftless.driver.fabric.v1_21_6

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import net.minecraft.item.ItemStack
import java.lang.reflect.InvocationTargetException

internal fun craftlessRecipeRecord(
    entry: Any,
    craftable: Boolean,
): JsonObject =
    craftlessRecipeRecord(
        recipe = entry.toCraftlessRecipeProjection(),
        craftable = craftable,
    )

internal fun Any.craftlessOutputItems(): List<CraftlessRecipeItem> =
    craftlessDisplayObject()
        ?.invokeNoArg("result")
        ?.toCraftlessRecipeItems()
        ?: emptyList()

internal fun Any.craftlessRecipeHandleKey(): String =
    craftlessIdObject()
        ?.invokeNoArg("index")
        ?.toString()
        ?: craftlessIdObject()?.toString().orEmpty()

internal fun Any.craftlessIdObject(): Any? = invokeNoArg("id")

internal fun Any.craftlessDisplayObject(): Any? =
    invokeNoArg("display")
        ?: invokeNoArg("value")

internal fun Any.craftlessIsEnabled(features: Any?): Boolean = features == null || invokeBoolean("isEnabled", features) ?: true

internal fun craftlessRecipeRecord(
    recipe: CraftlessRecipeProjection,
    craftable: Boolean,
): JsonObject {
    val outputs =
        buildJsonArray {
            recipe.outputs.forEach { output -> add(output.toCraftlessRecipeItem()) }
        }
    val ingredients =
        buildJsonArray {
            recipe.ingredients.forEach { ingredient -> add(ingredient.toCraftlessRecipeItem()) }
        }
    return buildJsonObject {
        put("handle", "recipe.handle:${recipe.handle}")
        put("kind", recipe.kind)
        put("craftable", craftable)
        put("outputs", outputs)
        put("ingredients", ingredients)
        put("produces", outputs)
        put("requires", ingredients)
        if (!craftable) {
            put("reason", "recipe-not-craftable")
        }
        recipe.station?.let { station ->
            put("station", station.toCraftlessRecipeItem())
        }
    }
}

internal data class CraftlessRecipeProjection(
    val handle: String,
    val kind: String,
    val outputs: List<CraftlessRecipeItem>,
    val ingredients: List<CraftlessRecipeItem> = emptyList(),
    val station: CraftlessRecipeItem? = null,
) {
    constructor(
        handleIndex: Int,
        kind: String,
        outputs: List<CraftlessRecipeItem>,
        ingredients: List<CraftlessRecipeItem> = emptyList(),
        station: CraftlessRecipeItem? = null,
    ) : this(
        handle = handleIndex.toString(),
        kind = kind,
        outputs = outputs,
        ingredients = ingredients,
        station = station,
    )
}

internal fun craftlessRecipeItem(
    rawName: String,
    translationKey: String,
    count: Int = 1,
): CraftlessRecipeItem =
    CraftlessRecipeItem(
        label = translationKey.toCraftlessItemLabel(rawName),
        count = count.coerceAtLeast(1),
        category = translationKey.toCraftlessItemCategory(),
    )

internal fun JsonObject.matchesCraftlessRecipeQuery(
    category: String?,
    output: String?,
    craftable: Boolean?,
): Boolean {
    if (craftable != null && this["craftable"]?.jsonPrimitive?.content != craftable.toString()) {
        return false
    }
    val outputs = this["outputs"]?.jsonArray.orEmpty().map { element -> element.jsonObject }
    if (category != null && outputs.none { recipeOutput -> recipeOutput["category"]?.jsonPrimitive?.content == category }) {
        return false
    }
    if (output != null) {
        val normalizedOutput = output.lowercase()
        if (outputs.none { recipeOutput ->
                recipeOutput["label"]
                    ?.jsonPrimitive
                    ?.content
                    ?.lowercase()
                    ?.contains(normalizedOutput) == true
            }
        ) {
            return false
        }
    }
    return true
}

private fun Any.toCraftlessRecipeProjection(): CraftlessRecipeProjection {
    val display = craftlessDisplayObject()
    return CraftlessRecipeProjection(
        handle = craftlessRecipeHandleKey(),
        kind = display?.toCraftlessRecipeKind() ?: "recipe",
        outputs = display?.invokeNoArg("result")?.toCraftlessRecipeItems().orEmpty(),
        ingredients =
            display
                ?.toCraftlessIngredientDisplays()
                .orEmpty()
                .flatMap { ingredient -> ingredient.toCraftlessRecipeItems() },
        station = display?.invokeNoArg("craftingStation")?.toCraftlessRecipeItems()?.firstOrNull(),
    )
}

private fun Any.toCraftlessRecipeKind(): String {
    val name = javaClass.simpleName
    return when {
        name.contains("Shaped") -> "shaped-crafting"
        name.contains("Shapeless") -> "shapeless-crafting"
        name.contains("Furnace") || name.contains("Smelting") || name.contains("Blasting") || name.contains("Smoking") -> "furnace"
        else -> "recipe"
    }
}

private fun Any.toCraftlessIngredientDisplays(): List<Any> =
    invokeNoArg("ingredients")
        .asIterable()
        ?.toList()
        ?: listOfNotNull(invokeNoArg("ingredient"), invokeNoArg("fuel"))

private fun Any.toCraftlessRecipeItems(): List<CraftlessRecipeItem> {
    if (this is ItemStack) {
        return listOf(toCraftlessRecipeItem()).filterNot { item -> item.label.isBlank() }
    }
    val stack = invokeNoArg("stack") as? ItemStack
    if (stack != null) {
        return listOf(stack.toCraftlessRecipeItem()).filterNot { item -> item.label.isBlank() }
    }
    val itemStack = invokeNoArg("item")?.invokeNoArg("value")?.invokeNoArg("getDefaultStack") as? ItemStack
    if (itemStack != null) {
        return listOf(itemStack.toCraftlessRecipeItem()).filterNot { item -> item.label.isBlank() }
    }
    val contents = invokeNoArg("contents").asIterable()
    if (contents != null) {
        return contents.flatMap { content -> content.toCraftlessRecipeItems() }.filterNot { item -> item.label.isBlank() }
    }
    val input = invokeNoArg("input")
    if (input != null) {
        return input.toCraftlessRecipeItems().filterNot { item -> item.label.isBlank() }
    }
    return emptyList()
}

internal data class CraftlessRecipeItem(
    val label: String,
    val count: Int,
    val category: String,
) {
    fun toCraftlessRecipeItem(): JsonObject =
        buildJsonObject {
            put("label", label)
            put("count", count)
            put("category", category)
        }
}

internal fun ItemStack.toCraftlessRecipeItem(): CraftlessRecipeItem =
    craftlessRecipeItem(
        rawName = name.string,
        translationKey = item.translationKey,
        count = count,
    )

internal fun Any?.asIterable(): Iterable<Any>? =
    when (this) {
        is Iterable<*> -> filterNotNull()
        else -> null
    }

internal fun Any.invokeNoArg(name: String): Any? =
    try {
        javaClass.methods
            .firstOrNull { method -> method.name == name && method.parameterCount == 0 }
            ?.invoke(this)
    } catch (_: IllegalAccessException) {
        null
    } catch (_: InvocationTargetException) {
        null
    } catch (_: SecurityException) {
        null
    }

internal fun Any.invokeBoolean(
    name: String,
    argument: Any,
): Boolean? =
    try {
        javaClass.methods
            .firstOrNull { method -> method.name == name && method.parameterCount == 1 }
            ?.invoke(this, argument) as? Boolean
    } catch (_: IllegalAccessException) {
        null
    } catch (_: InvocationTargetException) {
        null
    } catch (_: SecurityException) {
        null
    }

private fun String.toCraftlessItemLabel(rawName: String): String {
    val fallback = substringAfterLast('.').toCraftlessTitle()
    return rawName
        .takeUnless { name -> name.contains('.') || name.contains('_') }
        ?.takeIf { name -> name.isNotBlank() }
        ?: fallback
}

private fun String.toCraftlessItemCategory(): String {
    val key = substringAfterLast('.')
    return when {
        key.contains("sword") || key.contains("bow") || key.contains("trident") || key.contains("mace") -> "weapon"
        key.contains("pickaxe") || key.contains("shovel") || key.contains("axe") || key.contains("hoe") -> "tool"
        key.contains("helmet") || key.contains("chestplate") || key.contains("leggings") || key.contains("boots") -> "armor"
        key.contains("planks") ||
            key.contains("log") ||
            key.contains("stick") ||
            key.contains("ingot") ||
            key.contains("gem") -> "material"
        key.contains("bread") ||
            key.contains("apple") ||
            key.contains("carrot") ||
            key.contains("potato") ||
            key.contains("beef") ||
            key.contains("pork") ||
            key.contains("chicken") ||
            key.contains("mutton") -> "food"
        else -> "item"
    }
}

private fun String.toCraftlessTitle(): String =
    split('_', '-', '.')
        .filter { part -> part.isNotBlank() }
        .joinToString(" ") { part -> part.replaceFirstChar { char -> char.uppercase() } }
