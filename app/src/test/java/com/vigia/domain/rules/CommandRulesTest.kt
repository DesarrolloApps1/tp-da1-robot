package com.vigia.domain.rules

import com.vigia.domain.model.CommandType
import com.vigia.domain.model.CommandType.ACK_ALERT
import com.vigia.domain.model.CommandType.CANCEL_MISSION
import com.vigia.domain.model.CommandType.OPERATIONAL_STOP
import com.vigia.domain.model.CommandType.PAUSE_MISSION
import com.vigia.domain.model.CommandType.RESOLVE_INCIDENT
import com.vigia.domain.model.CommandType.RESUME_MISSION
import com.vigia.domain.model.CommandType.RETURN_TO_BASE
import com.vigia.domain.model.CommandType.START_MISSION
import com.vigia.domain.model.Pose
import com.vigia.domain.model.RobotState
import com.vigia.domain.model.RobotTelemetry
import com.vigia.domain.model.ServiceCounters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandRulesTest {

    private fun telemetry(state: RobotState, battery: Int) = RobotTelemetry(
        robotId = "go2-vigia-01",
        seq = 1,
        timestampMillis = 0,
        rssi = -60,
        latencyMs = 80,
        batteryPercent = battery,
        minutesLeft = 60,
        charging = false,
        state = state,
        mission = null,
        pose = Pose(3.0, 3.0, 0.0, "Central"),
        speed = 0.0,
        moving = false,
        service = ServiceCounters(0, 0, 0),
    )

    private fun ctx(
        state: RobotState? = RobotState.IDLE,
        battery: Int = 80,
        stale: Boolean = false,
        openIncident: Boolean = false,
        pending: Set<CommandType> = emptySet(),
        unacknowledged: Boolean = false,
    ) = CommandContext(
        telemetry = state?.let { telemetry(it, battery) },
        stale = stale,
        openIncident = openIncident,
        pendingTypes = pending,
        hasUnacknowledgedAlert = unacknowledged,
    )

    private fun enabled(type: CommandType, ctx: CommandContext) = CommandRules.availability(type, ctx).enabled

    private fun enabledStates(type: CommandType) =
        RobotState.entries.filter { enabled(type, ctx(state = it)) }.toSet()

    // Iniciar misión

    @Test
    fun `start is enabled only from docked or idle`() {
        assertEquals(setOf(RobotState.DOCKED, RobotState.IDLE), enabledStates(START_MISSION))
    }

    @Test
    fun `start needs 30 percent battery`() {
        assertTrue(enabled(START_MISSION, ctx(battery = 30)))
        val result = CommandRules.availability(START_MISSION, ctx(battery = 29))
        assertFalse(result.enabled)
        assertEquals("Batería por debajo del 30 %", result.reason)
    }

    @Test
    fun `start is blocked by an open incident`() {
        assertFalse(enabled(START_MISSION, ctx(openIncident = true)))
    }

    @Test
    fun `start is disabled with stale data`() {
        val result = CommandRules.availability(START_MISSION, ctx(stale = true))
        assertFalse(result.enabled)
        assertEquals("Sin datos recientes del robot", result.reason)
    }

    // Pausar y reanudar

    @Test
    fun `pause is enabled only while patrolling`() {
        assertEquals(setOf(RobotState.PATROLLING), enabledStates(PAUSE_MISSION))
    }

    @Test
    fun `resume is enabled only from paused`() {
        assertEquals(setOf(RobotState.PAUSED), enabledStates(RESUME_MISSION))
    }

    @Test
    fun `resume needs 15 percent battery`() {
        assertTrue(enabled(RESUME_MISSION, ctx(state = RobotState.PAUSED, battery = 15)))
        val result = CommandRules.availability(RESUME_MISSION, ctx(state = RobotState.PAUSED, battery = 14))
        assertFalse(result.enabled)
        assertEquals("Batería por debajo del 15 %", result.reason)
    }

    @Test
    fun `resume is blocked by an open incident`() {
        assertFalse(enabled(RESUME_MISSION, ctx(state = RobotState.PAUSED, openIncident = true)))
    }

    // Cancelar y regresar

    @Test
    fun `cancel is enabled while patrolling, paused or in incident`() {
        assertEquals(
            setOf(RobotState.PATROLLING, RobotState.PAUSED, RobotState.INCIDENT),
            enabledStates(CANCEL_MISSION),
        )
    }

    @Test
    fun `return is enabled in every state except docked and returning`() {
        assertEquals(
            RobotState.entries.toSet() - RobotState.DOCKED - RobotState.RETURNING,
            enabledStates(RETURN_TO_BASE),
        )
    }

    @Test
    fun `cancel and return ask for confirmation, stop needs a hold`() {
        assertEquals(
            setOf(CANCEL_MISSION, RETURN_TO_BASE),
            CommandType.entries.filter(CommandRules::requiresConfirmation).toSet(),
        )
        assertEquals(setOf(OPERATIONAL_STOP), CommandType.entries.filter(CommandRules::requiresHold).toSet())
    }

    // Detención operativa

    @Test
    fun `stop is enabled in every state`() {
        assertEquals(RobotState.entries.toSet(), enabledStates(OPERATIONAL_STOP))
    }

    @Test
    fun `stop is enabled without telemetry, with a warning`() {
        val result = CommandRules.availability(OPERATIONAL_STOP, ctx(state = null, stale = true))
        assertTrue(result.enabled)
        assertEquals(CommandRules.STALE_WARNING, result.warning)
    }

    // Alertas e incidentes

    @Test
    fun `ack needs an unacknowledged alert`() {
        assertFalse(enabled(ACK_ALERT, ctx(unacknowledged = false)))
        assertTrue(enabled(ACK_ALERT, ctx(unacknowledged = true)))
    }

    @Test
    fun `resolve needs an open incident`() {
        assertFalse(enabled(RESOLVE_INCIDENT, ctx(state = RobotState.INCIDENT, openIncident = false)))
        assertTrue(enabled(RESOLVE_INCIDENT, ctx(state = RobotState.INCIDENT, openIncident = true)))
    }

    // Casos transversales

    @Test
    fun `a pending command blocks another of the same type only`() {
        val pending = ctx(state = RobotState.PATROLLING, pending = setOf(PAUSE_MISSION))
        val result = CommandRules.availability(PAUSE_MISSION, pending)
        assertFalse(result.enabled)
        assertEquals("Hay una orden igual pendiente", result.reason)
        assertTrue(enabled(RETURN_TO_BASE, pending))
    }

    @Test
    fun `without telemetry everything but stop is disabled`() {
        val noData = ctx(state = null, stale = true, openIncident = true, unacknowledged = true)
        assertEquals(listOf(OPERATIONAL_STOP), CommandType.entries.filter { enabled(it, noData) })
        assertEquals("Sin datos del robot", CommandRules.availability(PAUSE_MISSION, noData).reason)
    }

    @Test
    fun `stale data keeps state checks on the last telemetry and adds a warning`() {
        val stalePatrolling = ctx(state = RobotState.PATROLLING, stale = true)
        val pause = CommandRules.availability(PAUSE_MISSION, stalePatrolling)
        assertTrue(pause.enabled)
        assertEquals(CommandRules.STALE_WARNING, pause.warning)
        assertFalse(enabled(RESUME_MISSION, stalePatrolling))
    }

    @Test
    fun `no warning with fresh data or on disabled commands`() {
        assertNull(CommandRules.availability(PAUSE_MISSION, ctx(state = RobotState.PATROLLING)).warning)
        assertNull(CommandRules.availability(PAUSE_MISSION, ctx(state = RobotState.IDLE, stale = true)).warning)
    }
}
