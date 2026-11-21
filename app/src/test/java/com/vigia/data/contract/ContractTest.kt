package com.vigia.data.contract

import com.vigia.domain.model.Alert
import com.vigia.domain.model.AlertKind
import com.vigia.domain.model.AlertSeverity
import com.vigia.domain.model.Command
import com.vigia.domain.model.CommandType
import com.vigia.domain.model.MissionProgress
import com.vigia.domain.model.Pose
import com.vigia.domain.model.RobotState
import com.vigia.domain.model.RobotTelemetry
import com.vigia.domain.model.ServiceCounters
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractTest {

    // Ejemplos literales de PLAN.md, sección 8
    private val telemetrySample = """
        {
          "type": "telemetry",
          "robotId": "go2-vigia-01",
          "seq": 1532,
          "timestamp": "2026-11-16T03:14:05Z",
          "connection": { "rssi": -62, "latencyMs": 85 },
          "battery": { "percent": 64, "minutesLeft": 72, "charging": false },
          "state": "PATROLLING",
          "mission": { "id": "m-0007", "routeId": "ronda-completa", "progress": 0.42, "currentCheckpoint": "CP4", "checkpointsVerified": 3, "checkpointsTotal": 8 },
          "pose": { "x": 18.4, "y": 6.2, "heading": 90, "zone": "Laboratorio de informática" },
          "motion": { "speed": 0.8, "moving": true },
          "service": { "checkpointsVerified": 3, "peopleInRestrictedZone": 0, "openDoors": 1 }
        }
    """.trimIndent()

    private val commandSample =
        """{ "type": "command", "commandId": "cmd-0042", "robotId": "go2-vigia-01", "command": "START_MISSION", "params": { "routeId": "ronda-completa" }, "issuedAt": "2026-11-16T03:10:00Z" }"""
    private val acceptedSample =
        """{ "type": "command_ack", "commandId": "cmd-0042", "result": "ACCEPTED", "reason": null, "respondedAt": "2026-11-16T03:10:01Z" }"""
    private val rejectedSample =
        """{ "type": "command_ack", "commandId": "cmd-0043", "result": "REJECTED", "reason": "BATTERY_TOO_LOW", "respondedAt": "2026-11-16T03:10:02Z" }"""
    private val alertSample =
        """{ "type": "alert", "alertId": "al-77", "severity": "CRITICAL", "kind": "PERSON_IN_RESTRICTED_ZONE", "zone": "Laboratorio de informática", "message": "Persona detectada en zona restringida", "raisedAt": "2026-11-16T03:14:02Z", "requiresDecision": true }"""

    private fun millis(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun telemetrySampleMapsToDomain() {
        val telemetry = ContractJson.decodeFromString(TelemetryDto.serializer(), telemetrySample).toDomain()

        val expected = RobotTelemetry(
            robotId = "go2-vigia-01",
            seq = 1532,
            timestampMillis = millis("2026-11-16T03:14:05Z"),
            rssi = -62,
            latencyMs = 85,
            batteryPercent = 64,
            minutesLeft = 72,
            charging = false,
            state = RobotState.PATROLLING,
            mission = MissionProgress("m-0007", "ronda-completa", 0.42, "CP4", 3, 8),
            pose = Pose(18.4, 6.2, 90.0, "Laboratorio de informática"),
            speed = 0.8,
            moving = true,
            service = ServiceCounters(3, 0, 1),
        )
        assertEquals(expected, telemetry)
    }

    @Test
    fun commandSampleMapsToDomain() {
        val command = ContractJson.decodeFromString(CommandDto.serializer(), commandSample).toDomain()

        assertEquals(
            Command("cmd-0042", CommandType.START_MISSION, mapOf("routeId" to "ronda-completa"), millis("2026-11-16T03:10:00Z")),
            command,
        )
    }

    @Test
    fun ackSamplesParse() {
        val accepted = ContractJson.decodeFromString(CommandAckDto.serializer(), acceptedSample)
        val rejected = ContractJson.decodeFromString(CommandAckDto.serializer(), rejectedSample)

        assertEquals(CommandAckDto.ACCEPTED, accepted.result)
        assertNull(accepted.reason)
        assertEquals(CommandAckDto.REJECTED, rejected.result)
        assertEquals("BATTERY_TOO_LOW", rejected.reason)
        assertEquals(millis("2026-11-16T03:10:02Z"), rejected.respondedAt.toEpochMillis())
    }

    @Test
    fun alertSampleMapsToDomain() {
        val alert = ContractJson.decodeFromString(AlertDto.serializer(), alertSample).toDomain()

        assertEquals(
            Alert(
                id = "al-77",
                severity = AlertSeverity.CRITICAL,
                kind = AlertKind.PERSON_IN_RESTRICTED_ZONE,
                zone = "Laboratorio de informática",
                message = "Persona detectada en zona restringida",
                raisedAtMillis = millis("2026-11-16T03:14:02Z"),
                requiresDecision = true,
            ),
            alert,
        )
        assertFalse(alert.acknowledged)
    }

    @Test
    fun unknownKeysAreIgnored() {
        val json = alertSample.replace("\"requiresDecision\"", "\"extra\": 1, \"requiresDecision\"")

        assertTrue(ContractJson.decodeFromString(AlertDto.serializer(), json).requiresDecision)
    }

    @Test
    fun telemetryRoundTrip() {
        val telemetry = RobotTelemetry(
            robotId = "go2-vigia-01",
            seq = 7,
            timestampMillis = 1_789_000_123_456,
            rssi = -50,
            latencyMs = 60,
            batteryPercent = 99,
            minutesLeft = 8,
            charging = true,
            state = RobotState.DOCKED,
            mission = null,
            pose = Pose(3.0, 3.0, 45.5, "Central"),
            speed = 0.0,
            moving = false,
            service = ServiceCounters(8, 1, 2),
        )

        val json = ContractJson.encodeToString(TelemetryDto.serializer(), telemetry.toDto())

        assertTrue(json.contains("\"mission\":null"))
        assertEquals(telemetry, ContractJson.decodeFromString(TelemetryDto.serializer(), json).toDomain())
    }

    @Test
    fun commandAndAlertRoundTrip() {
        val command = Command("cmd-0001", CommandType.RESOLVE_INCIDENT, mapOf("alertId" to "al-0001", "decision" to "WAIT"), 1_000)
        val alert = Alert("al-0001", AlertSeverity.WARNING, AlertKind.DOOR_OPEN, "Salida de emergencia", "Puerta abierta", 2_000, false)

        val commandJson = ContractJson.encodeToString(CommandDto.serializer(), command.toDto("go2-vigia-01"))
        val alertJson = ContractJson.encodeToString(AlertDto.serializer(), alert.toDto())

        assertTrue(commandJson.contains("\"type\":\"command\""))
        assertEquals(command, ContractJson.decodeFromString(CommandDto.serializer(), commandJson).toDomain())
        assertEquals(alert, ContractJson.decodeFromString(AlertDto.serializer(), alertJson).toDomain())
    }
}
