package com.vigia.data.local

import com.vigia.domain.model.AlertSeverity
import com.vigia.domain.model.EventRecord
import com.vigia.domain.model.EventType
import com.vigia.domain.model.MissionRecord
import com.vigia.domain.model.MissionResult
import org.junit.Assert.assertEquals
import org.junit.Test

class MappersTest {

    @Test
    fun missionRoundTrip() {
        val finished = MissionRecord(
            id = "m-0001@s1",
            routeId = "ronda-completa",
            routeName = "Ronda completa",
            startedAtMillis = 1_000,
            endedAtMillis = 90_000,
            result = MissionResult.COMPLETED,
            checkpointsVerified = 8,
            checkpointsTotal = 8,
            peopleDetected = 1,
            openDoors = 2,
        )
        val running = finished.copy(endedAtMillis = null, result = MissionResult.IN_PROGRESS)

        assertEquals("COMPLETED", finished.toEntity().result)
        assertEquals(finished, finished.toEntity().toDomain())
        assertEquals(running, running.toEntity().toDomain())
    }

    @Test
    fun eventRoundTrip() {
        val decision = EventRecord(
            id = 7,
            timestampMillis = 3_000,
            missionId = "m-0001@s1",
            type = EventType.DECISION,
            severity = AlertSeverity.INFO,
            message = "Decisión del guardia: Falsa alarma",
            zone = "Laboratorio",
        )
        val connection = EventRecord(
            timestampMillis = 4_000,
            missionId = null,
            type = EventType.CONNECTION,
            severity = AlertSeverity.WARNING,
            message = "Se perdió la conexión con el robot",
            zone = null,
        )

        assertEquals("DECISION", decision.toEntity().type)
        assertEquals(decision, decision.toEntity().toDomain())
        assertEquals(connection, connection.toEntity().toDomain())
        // Con id 0 Room genera uno nuevo al insertar
        assertEquals(0L, connection.toEntity().id)
    }
}
