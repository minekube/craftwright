package com.minekube.craftwright.bridge.hmc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RealClientSmokePlanTest {
    @Test
    fun `smoke plan contains required bridge backed proof steps`() {
        val plan = RealClientSmokePlan.default()

        assertEquals("CRAFTWRIGHT_REAL_CLIENT_SMOKE", plan.environmentGate)
        assertTrue(plan.steps.any { it.kind == SmokeStepKind.START_SERVER })
        assertTrue(plan.steps.any { it.kind == SmokeStepKind.LAUNCH_CLIENT })
        assertTrue(plan.steps.any { it.kind == SmokeStepKind.START_API })
        assertTrue(plan.steps.any { it.kind == SmokeStepKind.CONNECT_CLIENT })
        assertTrue(plan.steps.any { it.kind == SmokeStepKind.SEND_CHAT })
        assertTrue(plan.steps.any { it.kind == SmokeStepKind.MOVE_FORWARD })
        assertTrue(plan.steps.any { it.kind == SmokeStepKind.ASSERT_SERVER_JOIN })
        assertTrue(plan.steps.any { it.kind == SmokeStepKind.ASSERT_CHAT_LOG })
        assertTrue(plan.steps.any { it.kind == SmokeStepKind.ASSERT_POSITION_CHANGED })
        assertTrue(plan.artifacts.contains("openapi.json"))
        assertTrue(plan.artifacts.contains("events.jsonl"))
        assertTrue(plan.artifacts.contains("version.json"))
    }
}
