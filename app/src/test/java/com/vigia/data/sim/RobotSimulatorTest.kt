package com.vigia.data.sim

import com.vigia.data.contract.AlertDto
import com.vigia.data.contract.CommandAckDto
import com.vigia.data.contract.CommandDto
import com.vigia.data.contract.ContractJson
import com.vigia.data.contract.TelemetryDto
import com.vigia.data.contract.toDomain
import com.vigia.data.contract.toDto
import com.vigia.domain.model.Alert
import com.vigia.domain.model.AlertKind
import com.vigia.domain.model.AlertSeverity
import com.vigia.domain.model.Clock
import com.vigia.domain.model.Command
import com.vigia.domain.model.CommandType
import com.vigia.domain.model.EventType
import com.vigia.domain.model.IncidentDecision
import com.vigia.domain.model.MissionResult
import com.vigia.domain.model.RobotState
import com.vigia.domain.model.RobotTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RobotSimulatorTest {

    private var now = 0L
    private val sim = RobotSimulator(Clock { now })
    private var commandCount = 0

    private fun telemetry(): RobotTelemetry =
        ContractJson.decodeFromString(TelemetryDto.serializer(), sim.telemetryJson()).toDomain()

    private fun alerts(): List<Alert> =
        sim.drainAlerts().map { ContractJson.decodeFromString(AlertDto.serializer(), it).toDomain() }

    private fun send(type: CommandType, params: Map<String, String> = emptyMap()): CommandAckDto {
        val command = Command("cmd-${++commandCount}", type, params, now)
        val json = ContractJson.encodeToString(CommandDto.serializer(), command.toDto(RobotSimulator.ROBOT_ID))
        return ContractJson.decodeFromString(CommandAckDto.serializer(), sim.receive(json))
    }

    private fun advance(seconds: Int, linkUp: Boolean = true) = repeat(seconds) {
        now += 1_000
        sim.advance(1.0, linkUp)
    }

    private fun start(routeId: String = "ronda-completa") {
        assertEquals(CommandAckDto.ACCEPTED, send(CommandType.START_MISSION, mapOf("routeId" to routeId)).result)
    }

    private fun assertRejected(reason: String, ack: CommandAckDto) {
        assertEquals(CommandAckDto.REJECTED, ack.result)
        assertEquals(reason, ack.reason)
    }

    private fun resolve(alert: Alert, decision: IncidentDecision) =
        send(CommandType.RESOLVE_INCIDENT, mapOf("alertId" to alert.id, "decision" to decision.name))

    @Test
    fun startsDockedWithFullBattery() {
        val telemetry = telemetry()

        assertEquals(RobotState.DOCKED, telemetry.state)
        assertEquals(100, telemetry.batteryPercent)
        assertNull(telemetry.mission)
        assertEquals("Central", telemetry.pose.zone)
    }

    @Test
    fun fullRoundEndsDockedAndCompleted() {
        start()
        val started = telemetry()
        assertEquals(RobotState.PATROLLING, started.state)
        assertEquals("m-0001", started.mission?.id)
        assertEquals(8, started.mission?.checkpointsTotal)

        advance(60)
        assertEquals(RobotState.PATROLLING, telemetry().state)

        var seconds = 60
        while (telemetry().state != RobotState.DOCKED && seconds < 300) {
            advance(1)
            seconds++
        }

        val end = telemetry()
        assertEquals(RobotState.DOCKED, end.state)
        assertNull(end.mission)
        assertEquals(8, end.service.checkpointsVerified)
        val events = sim.drainEvents()
        assertEquals(8, events.count { it.type == EventType.CHECKPOINT })
        assertTrue(events.last().message.contains("completada"))
        assertTrue(alerts().isEmpty())
    }

    @Test
    fun missionIdsIncrement() {
        start()
        send(CommandType.CANCEL_MISSION)
        start()

        assertEquals("m-0002", telemetry().mission?.id)
    }

    @Test
    fun pauseStopsMovementAndResumeContinues() {
        start()
        advance(3)
        assertEquals(CommandAckDto.ACCEPTED, send(CommandType.PAUSE_MISSION).result)
        val paused = telemetry()

        advance(10)
        val later = telemetry()
        assertEquals(RobotState.PAUSED, later.state)
        assertEquals(paused.pose, later.pose)
        assertFalse(later.moving)
        assertEquals(0.0, later.speed, 0.0)

        assertEquals(CommandAckDto.ACCEPTED, send(CommandType.RESUME_MISSION).result)
        advance(1)
        assertTrue(telemetry().pose != later.pose)
    }

    @Test
    fun cancelLeavesRobotIdle() {
        start()
        advance(5)

        assertEquals(CommandAckDto.ACCEPTED, send(CommandType.CANCEL_MISSION).result)

        val telemetry = telemetry()
        assertEquals(RobotState.IDLE, telemetry.state)
        assertNull(telemetry.mission)
        assertTrue(sim.drainEvents().last().message.contains("cancelada"))
    }

    @Test
    fun operationalStopKeepsMissionAndAllowsReturn() {
        start()
        advance(2)

        assertEquals(CommandAckDto.ACCEPTED, send(CommandType.OPERATIONAL_STOP).result)
        val stopped = telemetry()
        assertEquals(RobotState.STOPPED, stopped.state)
        assertEquals("m-0001", stopped.mission?.id)
        assertEquals(0.0, stopped.speed, 0.0)

        assertEquals(CommandAckDto.ACCEPTED, send(CommandType.RETURN_TO_BASE).result)
        advance(30)
        assertEquals(RobotState.DOCKED, telemetry().state)
        assertTrue(sim.drainEvents().last().message.contains("regreso a la base"))
    }

    @Test
    fun lowBatteryRaisesOneWarning() {
        start()
        sim.setBattery(24)
        advance(5)

        val alerts = alerts()
        assertEquals(1, alerts.size)
        assertEquals(AlertKind.LOW_BATTERY, alerts.single().kind)
        assertEquals(AlertSeverity.WARNING, alerts.single().severity)
        assertEquals(RobotState.PATROLLING, telemetry().state)
    }

    @Test
    fun criticalBatteryReturnsByItself() {
        start()
        advance(3)
        sim.setBattery(14)
        advance(1)

        val alert = alerts().single()
        assertEquals(AlertKind.CRITICAL_BATTERY, alert.kind)
        assertEquals(AlertSeverity.CRITICAL, alert.severity)
        assertEquals(RobotState.RETURNING, telemetry().state)

        advance(30)
        assertEquals(RobotState.DOCKED, telemetry().state)
        assertTrue(telemetry().charging)
    }

    @Test
    fun obstacleOpensIncidentAndWaitResumesAfterTenSeconds() {
        start()
        advance(2)
        sim.obstacleAhead()

        val alert = alerts().single()
        assertEquals(AlertKind.OBSTACLE, alert.kind)
        assertTrue(alert.requiresDecision)
        assertEquals(RobotState.INCIDENT, telemetry().state)
        assertRejected("INVALID_STATE", resolve(alert, IncidentDecision.INTRUDER))

        assertEquals(CommandAckDto.ACCEPTED, resolve(alert, IncidentDecision.WAIT).result)
        advance(9)
        assertEquals(RobotState.INCIDENT, telemetry().state)
        advance(1)
        assertEquals(RobotState.PATROLLING, telemetry().state)
    }

    @Test
    fun obstacleSkipCheckpointMovesToNextPoint() {
        start()
        advance(2)
        assertEquals("CP1", telemetry().mission?.currentCheckpointId)
        sim.obstacleAhead()

        assertEquals(CommandAckDto.ACCEPTED, resolve(alerts().single(), IncidentDecision.SKIP_CHECKPOINT).result)

        val telemetry = telemetry()
        assertEquals(RobotState.PATROLLING, telemetry.state)
        assertEquals("CP2", telemetry.mission?.currentCheckpointId)
        assertEquals(0, telemetry.service.checkpointsVerified)
    }

    @Test
    fun skippedCheckpointIsNotReportedAsVerified() {
        start()
        advance(2)
        sim.obstacleAhead()
        resolve(alerts().single(), IncidentDecision.SKIP_CHECKPOINT)

        repeat(120) {
            if (telemetry().mission?.checkpointsVerified == 2) return@repeat
            advance(1)
        }

        assertEquals(listOf("CP2", "CP3"), telemetry().mission?.verifiedCheckpointIds)
    }

    @Test
    fun missionEndEventCarriesTheResult() {
        start()
        advance(61, linkUp = false)
        repeat(60) { advance(1, linkUp = false) }
        assertEquals(RobotState.DOCKED, telemetry().state)

        val end = sim.drainEvents().last { it.type == EventType.MISSION }
        assertEquals(MissionResult.RETURNED_TO_BASE, end.missionResult)
        assertEquals("m-0001", end.missionId)
    }

    @Test
    fun obstacleReturnToBaseStartsReturning() {
        start()
        advance(2)
        sim.obstacleAhead()

        assertEquals(CommandAckDto.ACCEPTED, resolve(alerts().single(), IncidentDecision.RETURN_TO_BASE).result)

        assertEquals(RobotState.RETURNING, telemetry().state)
        assertEquals("BASE", telemetry().mission?.currentCheckpointId)
    }

    @Test
    fun personInRestrictedZoneCountsAndAppliesDecisions() {
        start()
        advance(2)
        sim.personInRestrictedZone()

        val first = alerts().single()
        assertEquals(AlertKind.PERSON_IN_RESTRICTED_ZONE, first.kind)
        assertEquals("Laboratorio de informática", first.zone)
        assertTrue(first.requiresDecision)
        assertEquals(RobotState.INCIDENT, telemetry().state)
        assertEquals(1, telemetry().service.peopleInRestrictedZone)

        resolve(first, IncidentDecision.INTRUDER)
        assertEquals(RobotState.PAUSED, telemetry().state)

        send(CommandType.RESUME_MISSION)
        sim.personInRestrictedZone()
        resolve(alerts().single(), IncidentDecision.AUTHORIZED_EMPLOYEE)
        assertEquals(RobotState.PATROLLING, telemetry().state)

        sim.personInRestrictedZone()
        resolve(alerts().single(), IncidentDecision.FALSE_ALARM)
        assertEquals(RobotState.PATROLLING, telemetry().state)
        assertEquals(3, telemetry().service.peopleInRestrictedZone)
    }

    @Test
    fun openDoorCountsWithoutStopping() {
        start()
        advance(2)
        sim.doorOpen()

        val alert = alerts().single()
        assertEquals(AlertKind.DOOR_OPEN, alert.kind)
        assertEquals(AlertSeverity.WARNING, alert.severity)
        assertEquals("Salida de emergencia", alert.zone)
        assertFalse(alert.requiresDecision)
        assertEquals(RobotState.PATROLLING, telemetry().state)
        assertEquals(1, telemetry().service.openDoors)
    }

    @Test
    fun robotSideRejections() {
        assertRejected("INVALID_STATE", send(CommandType.PAUSE_MISSION))
        assertRejected("INVALID_STATE", send(CommandType.RETURN_TO_BASE))
        assertRejected("UNKNOWN_ALERT", send(CommandType.ACK_ALERT, mapOf("alertId" to "al-9999")))

        sim.setBattery(29)
        assertRejected("BATTERY_TOO_LOW", send(CommandType.START_MISSION))

        sim.setBattery(80)
        start()
        assertRejected("ROBOT_BUSY", send(CommandType.START_MISSION))
    }

    @Test
    fun forcedRejectionAppliesOnceAndSparesTheStop() {
        sim.forceRejectNext()

        assertEquals(CommandAckDto.ACCEPTED, send(CommandType.OPERATIONAL_STOP).result)
        assertRejected("FORCED", send(CommandType.CANCEL_MISSION))
        assertEquals(CommandAckDto.ACCEPTED, send(CommandType.CANCEL_MISSION).result)
    }

    @Test
    fun linkDownForMoreThanSixtySecondsReturnsToBase() {
        start()
        advance(60, linkUp = false)
        assertEquals(RobotState.PATROLLING, telemetry().state)

        advance(1, linkUp = false)
        assertEquals(RobotState.RETURNING, telemetry().state)
    }

    @Test
    fun signalDegradesAwayFromBase() {
        val near = telemetry()
        start()
        advance(40)
        val far = telemetry()

        assertEquals(-45, near.rssi)
        assertEquals(40, near.latencyMs)
        assertTrue(far.rssi < near.rssi)
        assertTrue(far.latencyMs > near.latencyMs)
    }
}
