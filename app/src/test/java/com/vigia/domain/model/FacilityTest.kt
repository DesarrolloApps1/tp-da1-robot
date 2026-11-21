package com.vigia.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class FacilityTest {

    @Test
    fun `every checkpoint falls in its expected zone`() {
        val expected = mapOf(
            "BASE" to "Central",
            "CP1" to "Hall",
            "CP2" to "Hall",
            "CP3" to "Pasillo A",
            "CP4" to "Laboratorio",
            "CP5" to "Biblioteca",
            "CP6" to "Sala técnica",
            "CP7" to "Salida de emergencia",
            "CP8" to "Pasillo B",
        )
        Facility.checkpoints.forEach {
            assertEquals(it.id, expected[it.id], Facility.zoneAt(it.x, it.y))
        }
    }

    @Test
    fun `points outside every zone are reported as outside`() {
        assertEquals(Facility.OUTSIDE, Facility.zoneAt(2.0, 10.0))
        assertEquals(Facility.OUTSIDE, Facility.zoneAt(-1.0, 5.0))
        assertEquals(Facility.OUTSIDE, Facility.zoneAt(50.0, 50.0))
    }

    @Test
    fun `restricted zones are the lab and the technical room`() {
        assertEquals(
            setOf("Laboratorio", "Sala técnica"),
            Facility.zones.filter { it.restricted }.map { it.name }.toSet(),
        )
    }

    @Test
    fun `zones fit inside the floor plan`() {
        Facility.zones.forEach {
            assertTrue(it.name, it.left >= 0 && it.top >= 0)
            assertTrue(it.name, it.right <= Facility.WIDTH_METERS && it.bottom <= Facility.HEIGHT_METERS)
        }
    }

    @Test
    fun `routes reference existing checkpoints and start and end at base`() {
        Facility.routes.forEach { route ->
            route.checkpointIds.forEach { assertNotNull("${route.id}: $it", Facility.checkpoint(it)) }
            assertEquals(route.id, Facility.BASE_ID, route.checkpointIds.first())
            assertEquals(route.id, Facility.BASE_ID, route.checkpointIds.last())
        }
    }

    @Test
    fun `full round visits every checkpoint`() {
        val full = Facility.route("ronda-completa")!!
        assertEquals(Facility.checkpoints.map { it.id }.toSet(), full.checkpointIds.toSet())
    }

    @Test
    fun `lookups return null for unknown ids`() {
        assertNull(Facility.checkpoint("CP99"))
        assertNull(Facility.route("ronda-inexistente"))
    }

    @Test
    fun `route length sums straight segments`() {
        // BASE (3,3) -> CP4 (30,4) -> CP6 (30,17) -> BASE (3,3)
        val expected = hypot(27.0, 1.0) + 13.0 + hypot(27.0, 14.0)
        assertEquals(expected, Facility.routeLengthMeters(Facility.route("zonas-criticas")!!), 1e-9)
    }
}
