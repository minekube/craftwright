package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.protocol.isCraftlessActionId

internal data class SurvivalTaskGraph(
    val id: String,
    val steps: List<SurvivalTaskStep>,
) {
    init {
        require(id.isCraftlessActionId()) { "invalid survival task graph id $id" }
        require(steps.isNotEmpty()) { "survival task graph requires at least one step" }
        val duplicateStep = steps.groupBy { it.id }.entries.firstOrNull { (_, matches) -> matches.size > 1 }
        require(duplicateStep == null) { "duplicate survival task step ${duplicateStep?.key}" }
        require(!toString().containsForbiddenSurvivalShortcut()) {
            "survival task graph must not contain server shortcuts or static completion actions"
        }
    }

    companion object {
        fun honestCowHunt(): SurvivalTaskGraph =
            SurvivalTaskGraph(
                id = "task.survival.honest-cow-hunt",
                steps =
                    listOf(
                        SurvivalTaskStep(
                            id = "observe.logs",
                            description = "Observe nearby logs or tree resources before planning movement.",
                            requires = listOf("runtime.graph", "world.state"),
                        ),
                        SurvivalTaskStep(
                            id = "navigation.plan",
                            description = "Plan movement from observed client state to reachable materials.",
                            requires = listOf("observe.logs"),
                            invokes = listOf("navigation.plan", "navigation.follow"),
                        ),
                        SurvivalTaskStep(
                            id = "world.block.break",
                            description = "Break reachable material blocks through discovered world interaction operations.",
                            requires = listOf("navigation.plan"),
                            invokes = listOf("world.block.break"),
                        ),
                        SurvivalTaskStep(
                            id = "inventory.query",
                            description = "Observe inventory state after collecting materials.",
                            requires = listOf("world.block.break"),
                            invokes = listOf("inventory.query"),
                        ),
                        SurvivalTaskStep(
                            id = "craft.weapon",
                            description = "Craft or otherwise obtain a legitimate survival weapon from observed materials.",
                            requires = listOf("inventory.query"),
                            invokes = listOf("task.run"),
                        ),
                        SurvivalTaskStep(
                            id = "observe.entity",
                            description = "Observe nearby entities and select a reachable animal target.",
                            requires = listOf("craft.weapon"),
                        ),
                        SurvivalTaskStep(
                            id = "combat.attack-entity",
                            description = "Navigate to the selected entity and use discovered interaction operations.",
                            requires = listOf("observe.entity"),
                            invokes = listOf("navigation.plan", "navigation.follow", "player.look", "world.block.interact"),
                        ),
                    ),
            )
    }
}

internal data class SurvivalTaskStep(
    val id: String,
    val description: String,
    val requires: List<String> = emptyList(),
    val invokes: List<String> = emptyList(),
) {
    init {
        require(id.isCraftlessActionId()) { "invalid survival task step id $id" }
        require(description.isNotBlank()) { "survival task step description is required" }
        (requires + invokes).forEach { reference ->
            require(reference.isCraftlessActionId()) { "invalid survival task step reference $reference" }
        }
        require(!toString().containsForbiddenSurvivalShortcut()) {
            "survival task step must not contain server shortcuts or static completion actions"
        }
    }
}

private fun String.containsForbiddenSurvivalShortcut(): Boolean =
    contains("give", ignoreCase = true) ||
        contains("kill.cow", ignoreCase = true) ||
        contains("server-side item", ignoreCase = true)
