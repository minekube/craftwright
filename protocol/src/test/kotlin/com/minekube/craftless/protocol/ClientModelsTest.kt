package com.minekube.craftless.protocol

import kotlin.test.Test
import kotlin.test.assertFailsWith

class ClientModelsTest {
    @Test
    fun `create client request rejects ids that cannot be used as route segments`() {
        listOf(
            "",
            "alice/bob",
            "alice:run",
            "alice bob",
            ".alice",
            "alice.",
        ).forEach { clientId ->
            assertFailsWith<IllegalArgumentException> {
                CreateClientRequest(
                    id = clientId,
                    version = "1.21.4",
                    loader = Loader.FABRIC,
                    profile = Profile.offline("Alice"),
                )
            }
        }
    }
}
