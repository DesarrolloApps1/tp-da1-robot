package com.vigia.data.sim

import com.vigia.domain.model.Clock
import com.vigia.domain.model.CommandStatus
import com.vigia.domain.model.CommandType
import com.vigia.domain.model.EventRecord
import com.vigia.domain.model.EventType
import com.vigia.domain.model.RejectReason
import com.vigia.domain.model.RobotState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SimulatedRobotRepositoryTest {

    private val start = mapOf("routeId" to "ronda-completa")

    private fun TestScope.repository(): SimulatedRobotRepository {
        val clock = Clock { testScheduler.currentTime }
        return SimulatedRobotRepository(RobotSimulator(clock), backgroundScope, clock)
    }

    private fun TestScope.collectEvents(repository: SimulatedRobotRepository): List<EventRecord> {
        val events = mutableListOf<EventRecord>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.events.toList(events) }
        return events
    }

    @Test
    fun publishesTelemetryEveryTick() = runTest {
        val repository = repository()
        runCurrent()

        assertEquals(RobotState.DOCKED, repository.telemetry.value?.state)
        assertEquals(0L, repository.lastUpdateMillis.value)
        assertEquals(2, repository.robots.value.size)
        assertFalse(repository.robots.value.last().available)

        advanceTimeBy(1_001)
        assertEquals(1_000L, repository.lastUpdateMillis.value)
    }

    @Test
    fun startIsConfirmedAfterAckDelay() = runTest {
        val repository = repository()
        val events = collectEvents(repository)

        val result = async { repository.send(CommandType.START_MISSION, start) }
        runCurrent()
        val pending = repository.commands.value.single()
        assertEquals(CommandStatus.PENDING, pending.status)
        assertEquals("cmd-0001", pending.command.id)

        advanceTimeBy(600)
        assertEquals(CommandStatus.PENDING, repository.commands.value.single().status)

        runCurrent()
        val record = result.await()
        assertEquals(CommandStatus.CONFIRMED, record.status)
        assertEquals(600L, record.respondedAtMillis)
        assertEquals(record, repository.commands.value.single())

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(RobotState.PATROLLING, repository.telemetry.value?.state)
        assertTrue(events.any { it.type == EventType.COMMAND && it.message == "Iniciar misión: Confirmada" })
        assertTrue(events.any { it.type == EventType.MISSION && it.missionId == "m-0001" })
    }

    @Test
    fun forcedRejectionIsReported() = runTest {
        val repository = repository()
        repository.rejectNextCommand()

        val record = repository.send(CommandType.START_MISSION, start)

        assertEquals(CommandStatus.REJECTED, record.status)
        assertEquals(RejectReason.FORCED, record.reason)
    }

    @Test
    fun disconnectedCommandGetsNoResponseAndDataStops() = runTest {
        val repository = repository()
        val events = collectEvents(repository)
        advanceTimeBy(2_001)
        val telemetry = repository.telemetry.value
        val lastUpdate = repository.lastUpdateMillis.value

        repository.setConnected(false)
        val result = async { repository.send(CommandType.START_MISSION, start) }
        advanceTimeBy(4_999)
        assertEquals(CommandStatus.PENDING, repository.commands.value.single().status)

        advanceTimeBy(2)
        val record = result.await()
        assertEquals(CommandStatus.NO_RESPONSE, record.status)
        assertNull(record.respondedAtMillis)
        assertEquals(telemetry, repository.telemetry.value)
        assertEquals(lastUpdate, repository.lastUpdateMillis.value)
        assertTrue(events.any { it.type == EventType.CONNECTION })

        repository.setConnected(true)
        advanceTimeBy(1_001)
        assertNotEquals(lastUpdate, repository.lastUpdateMillis.value)
        assertEquals(RobotState.DOCKED, repository.telemetry.value?.state)
    }

    @Test
    fun ackAlertMarksAlertAcknowledged() = runTest {
        val repository = repository()
        repository.triggerOpenDoor()
        advanceTimeBy(1)
        val alert = repository.alerts.value.single()
        assertFalse(alert.acknowledged)

        val record = repository.send(CommandType.ACK_ALERT, mapOf("alertId" to alert.id))

        assertEquals(CommandStatus.CONFIRMED, record.status)
        assertTrue(repository.alerts.value.single().acknowledged)
    }

    @Test
    fun resolveIncidentStoresDecision() = runTest {
        val repository = repository()
        repository.send(CommandType.START_MISSION, start)
        advanceTimeBy(2_000)
        repository.triggerObstacle()
        advanceTimeBy(1_000)
        val alert = repository.alerts.value.single()

        val record = repository.send(
            CommandType.RESOLVE_INCIDENT,
            mapOf("alertId" to alert.id, "decision" to "RETURN_TO_BASE"),
        )

        assertEquals(CommandStatus.CONFIRMED, record.status)
        val resolved = repository.alerts.value.single()
        assertTrue(resolved.acknowledged)
        assertEquals("RETURN_TO_BASE", resolved.resolution?.name)
    }
}
