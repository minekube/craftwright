package com.minekube.craftless.driver.fabric.v1_21_6

data class FabricClientSmokePlan(
    val environmentGate: String,
    val minecraftVersion: String,
    val gradleTasks: List<String>,
    val steps: List<FabricSmokeStep>,
    val artifacts: List<String>,
) {
    companion object {
        fun default(): FabricClientSmokePlan =
            FabricClientSmokePlan(
                environmentGate = "CRAFTLESS_FABRIC_CLIENT_SMOKE",
                minecraftVersion = "1.21.6",
                gradleTasks =
                    listOf(
                        ":driver-fabric:fabricClientSmoke",
                    ),
                steps =
                    listOf(
                        FabricSmokeStep(
                            FabricSmokeStepKind.START_LOCAL_SERVER,
                            "Start the opt-in local server fixture, kept running through client actions",
                        ),
                        FabricSmokeStep(
                            FabricSmokeStepKind.LAUNCH_FABRIC_CLIENT,
                            "Launch the Craftless Fabric driver client for Minecraft 1.21.6",
                        ),
                        FabricSmokeStep(
                            FabricSmokeStepKind.START_DAEMON_API,
                            "Start the Craftless daemon API against the Fabric driver backend",
                        ),
                        FabricSmokeStep(
                            FabricSmokeStepKind.CONNECT_CLIENT,
                            "Connect the Fabric client through the Craftless lifecycle API",
                        ),
                        FabricSmokeStep(
                            FabricSmokeStepKind.INVOKE_GENERATED_CHAT_ACTION,
                            "Invoke generated player.chat through the per-client action API",
                        ),
                        FabricSmokeStep(
                            FabricSmokeStepKind.INVOKE_GENERATED_MOVE_ACTION,
                            "Invoke generated player.move through the per-client action API",
                        ),
                        FabricSmokeStep(
                            FabricSmokeStepKind.INVOKE_GENERATED_GAMEPLAY_ACTIONS,
                            "Re-fetch connected client metadata and invoke generated inventory and block actions through the per-client action API",
                        ),
                        FabricSmokeStep(
                            FabricSmokeStepKind.ASSERT_SERVER_EVIDENCE,
                            "Assert server-side join, chat, movement, and disconnect evidence",
                        ),
                        FabricSmokeStep(
                            FabricSmokeStepKind.COLLECT_ARTIFACTS,
                            "Collect server logs, client OpenAPI, action metadata, events, and runtime metadata",
                        ),
                    ),
                artifacts =
                    listOf(
                        "server.log",
                        "server-evidence.jsonl",
                        "client-openapi.json",
                        "client-actions.json",
                        "client-openapi-connected.json",
                        "client-actions-connected.json",
                        "client-events.jsonl",
                        "gameplay-results.jsonl",
                        "runtime-metadata.json",
                    ),
            )
    }
}

data class FabricSmokeStep(
    val kind: FabricSmokeStepKind,
    val description: String,
)

enum class FabricSmokeStepKind {
    START_LOCAL_SERVER,
    LAUNCH_FABRIC_CLIENT,
    START_DAEMON_API,
    CONNECT_CLIENT,
    INVOKE_GENERATED_CHAT_ACTION,
    INVOKE_GENERATED_MOVE_ACTION,
    INVOKE_GENERATED_GAMEPLAY_ACTIONS,
    ASSERT_SERVER_EVIDENCE,
    COLLECT_ARTIFACTS,
}
