package ru.plasmadynamics.visits

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.testing.*
import kotlin.test.*

class HealthTest {
    @Test
    fun testHealth() = testApplication {
        application { module() }
        val response = client.get("/health")
        assertEquals("OK", response.bodyAsText())
    }
}
